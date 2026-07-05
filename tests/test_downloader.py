"""
Smoke-test suite for NGWH-002 (Historical Downloader).

No real network calls are made — `UpstoxHistoricalClient` is exercised
against a fake, injectable HTTP transport that returns synthetic candle
data, matching Upstox's documented response shape exactly. This tests
everything EXCEPT the live Upstox endpoint itself, which cannot be
verified from this environment (network egress here is restricted to a
fixed allowlist that does not include api.upstox.com) — verify the raw
transport against the real API in a follow-up smoke test once deployed.
"""

from __future__ import annotations

import datetime as dt
import json
from types import SimpleNamespace

import pytest

from warehouse.bootstrap import WarehouseBootstrap
from warehouse.config import load_config
from warehouse.core.constants import JobStatus, Timeframe
from warehouse.downloader import DownloaderConfig, run_historical_backfill
from warehouse.downloader.candle_normalizer import normalize_candles
from warehouse.downloader.coverage_planner import CoverageGap, find_missing_ranges
from warehouse.downloader.download_orchestrator import DownloadOrchestrator
from warehouse.downloader.downloader_config import RateLimitConfig, RetryConfig
from warehouse.downloader.exceptions import CandleValidationError, UpstoxAuthError, UpstoxRateLimitError
from warehouse.downloader.http_client import UpstoxHistoricalClient
from warehouse.downloader.interval_policy import build_plan
from warehouse.downloader.rate_limiter import TokenBucketRateLimiter
from warehouse.core.constants import WarehouseLayer


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------
@pytest.fixture()
def warehouse(tmp_path):
    cfg = load_config(
        paths={
            "root_dir": str(tmp_path / "warehouse"),
            "instrument_master_db_path": str(tmp_path / "instrument_master.db"),
        },
        logging={"also_console": False},
    )
    handles = WarehouseBootstrap(cfg).run()
    yield handles
    handles.duckdb_manager.close()


def _fake_candle_row(day: dt.date, close: float = 100.0):
    ts = dt.datetime(day.year, day.month, day.day, 9, 15, tzinfo=dt.timezone.utc).isoformat()
    return [ts, close - 1, close + 2, close - 2, close, 1000.0, None]


class FakeResponse:
    def __init__(self, status_code: int, payload: dict | None = None, text: str = ""):
        self.status_code = status_code
        self._payload = payload or {}
        self.text = text

    def json(self):
        return self._payload


def make_fake_transport(candles_by_call):
    """Returns a fake `http_get` that yields one FakeResponse per call, in
    order, from `candles_by_call` (a list of either FakeResponse objects or
    plain candle-lists which get wrapped into a 200 response)."""
    calls = {"n": 0}

    def fake_get(url, headers=None, timeout=None):
        idx = calls["n"]
        calls["n"] += 1
        item = candles_by_call[idx]
        if isinstance(item, FakeResponse):
            return item
        return FakeResponse(200, {"data": {"candles": item}})

    fake_get.call_count = lambda: calls["n"]
    return fake_get


# ---------------------------------------------------------------------------
# Rate limiter
# ---------------------------------------------------------------------------
def test_rate_limiter_throttles():
    limiter = TokenBucketRateLimiter(RateLimitConfig(requests_per_second=100, burst_capacity=2))
    # Should not block for the first 2 (burst capacity)
    limiter.acquire()
    limiter.acquire()
    # Third should still succeed (just may wait briefly) — verifying no exception/deadlock
    limiter.acquire()


def test_rate_limiter_try_acquire_respects_capacity():
    limiter = TokenBucketRateLimiter(RateLimitConfig(requests_per_second=0.001, burst_capacity=1))
    assert limiter.try_acquire() is True
    assert limiter.try_acquire() is False  # bucket empty, refill rate negligible


# ---------------------------------------------------------------------------
# Interval policy / chunk planning
# ---------------------------------------------------------------------------
def test_build_plan_clips_to_max_lookback():
    from warehouse.downloader.downloader_config import IntervalPolicyConfig

    policy = IntervalPolicyConfig()
    today = dt.date(2026, 7, 5)
    plan = build_plan(policy, Timeframe.DAY_1, dt.date(2000, 1, 1), today, today=today)
    assert plan.clipped_days > 0
    assert plan.effective_start > dt.date(2000, 1, 1)
    assert plan.effective_end == today


def test_build_plan_chunks_respect_max_span():
    from warehouse.downloader.downloader_config import IntervalPolicyConfig

    policy = IntervalPolicyConfig()
    today = dt.date(2026, 7, 5)
    plan = build_plan(policy, Timeframe.MIN_30, today - dt.timedelta(days=90), today, today=today)
    entry = policy.get(Timeframe.MIN_30)
    for chunk in plan.chunks:
        span_days = (chunk.to_date - chunk.from_date).days + 1
        assert span_days <= entry.chunk_days_per_request
    # chunks should be contiguous and cover the full effective range
    assert plan.chunks[0].from_date == plan.effective_start
    assert plan.chunks[-1].to_date == plan.effective_end


# ---------------------------------------------------------------------------
# Candle normalizer
# ---------------------------------------------------------------------------
def test_normalize_candles_happy_path():
    rows = [_fake_candle_row(dt.date(2024, 1, 2), 105), _fake_candle_row(dt.date(2024, 1, 1), 100)]
    table = normalize_candles("NSE_EQ|TEST", "1day", "day", rows)
    assert table.num_rows == 2
    assert set(table.column_names) == {f.name for f in table.schema}


def test_normalize_candles_rejects_bad_ohlc_strict():
    bad_row = ["2024-01-01T09:15:00+05:30", 100, 90, 95, 98, 1000, None]  # high < open, invalid
    with pytest.raises(CandleValidationError):
        normalize_candles("NSE_EQ|TEST", "1day", "day", [bad_row], strict=True)


def test_normalize_candles_skips_bad_rows_non_strict():
    good = _fake_candle_row(dt.date(2024, 1, 1))
    bad = ["2024-01-02T09:15:00+05:30", 100, 90, 95, 98, 1000, None]
    table = normalize_candles("NSE_EQ|TEST", "1day", "day", [good, bad], strict=False)
    assert table.num_rows == 1


# ---------------------------------------------------------------------------
# HTTP client — retry / auth / rate-limit handling via fake transport
# ---------------------------------------------------------------------------
def test_http_client_retries_then_succeeds():
    transport = make_fake_transport([
        FakeResponse(503, text="server error"),
        FakeResponse(200, {"data": {"candles": [_fake_candle_row(dt.date(2024, 1, 1))]}}),
    ])
    limiter = TokenBucketRateLimiter(RateLimitConfig(requests_per_second=1000, burst_capacity=10))
    config = DownloaderConfig(retry=RetryConfig(max_retries=3, backoff_base_seconds=0.01, backoff_max_seconds=0.02))
    client = UpstoxHistoricalClient("fake-token", config, limiter, http_get=transport, sleep_fn=lambda s: None)
    result = client.fetch_candles("NSE_EQ|TEST", "day", dt.date(2024, 1, 1), dt.date(2024, 1, 1))
    assert len(result.candles) == 1


def test_http_client_raises_auth_error_without_retry():
    transport = make_fake_transport([FakeResponse(401, text="unauthorized")])
    limiter = TokenBucketRateLimiter(RateLimitConfig(requests_per_second=1000, burst_capacity=10))
    config = DownloaderConfig(retry=RetryConfig(max_retries=3, backoff_base_seconds=0.01, backoff_max_seconds=0.02))
    client = UpstoxHistoricalClient("bad-token", config, limiter, http_get=transport, sleep_fn=lambda s: None)
    with pytest.raises(UpstoxAuthError):
        client.fetch_candles("NSE_EQ|TEST", "day", dt.date(2024, 1, 1), dt.date(2024, 1, 1))
    assert transport.call_count() == 1  # no retry on auth errors


def test_http_client_raises_rate_limit_error_after_exhausting_retries():
    transport = make_fake_transport([FakeResponse(429, text="rate limited")] * 4)
    limiter = TokenBucketRateLimiter(RateLimitConfig(requests_per_second=1000, burst_capacity=10))
    config = DownloaderConfig(retry=RetryConfig(max_retries=3, backoff_base_seconds=0.01, backoff_max_seconds=0.02))
    client = UpstoxHistoricalClient("fake-token", config, limiter, http_get=transport, sleep_fn=lambda s: None)
    with pytest.raises(UpstoxRateLimitError):
        client.fetch_candles("NSE_EQ|TEST", "day", dt.date(2024, 1, 1), dt.date(2024, 1, 1))
    assert transport.call_count() == 4  # initial + 3 retries


# ---------------------------------------------------------------------------
# Coverage planner
# ---------------------------------------------------------------------------
def test_find_missing_ranges_full_range_when_no_coverage(warehouse):
    gaps = find_missing_ranges(
        warehouse.metadata_manager, WarehouseLayer.RAW_OHLCV, "NSE_EQ|TEST", Timeframe.DAY_1,
        dt.date(2024, 1, 1), dt.date(2024, 1, 31),
    )
    assert gaps == [CoverageGap(dt.date(2024, 1, 1), dt.date(2024, 1, 31))]


# ---------------------------------------------------------------------------
# Full orchestrator integration (fake transport, real warehouse)
# ---------------------------------------------------------------------------
def test_orchestrator_end_to_end_writes_and_catalogs(warehouse):
    instrument_id = "NSE_EQ|TEST"
    candles = [_fake_candle_row(dt.date(2024, 1, d), 100 + d) for d in range(1, 6)]
    transport = make_fake_transport([FakeResponse(200, {"data": {"candles": candles}})])
    limiter = TokenBucketRateLimiter(RateLimitConfig(requests_per_second=1000, burst_capacity=10))
    config = DownloaderConfig()
    client = UpstoxHistoricalClient("fake-token", config, limiter, http_get=transport, sleep_fn=lambda s: None)

    orchestrator = DownloadOrchestrator(warehouse, config, client)
    result = orchestrator.run("test-job-1", instrument_id, Timeframe.DAY_1, dt.date(2024, 1, 1), dt.date(2024, 1, 5))

    assert result.total_rows_written == 5
    summary = warehouse.metadata_manager.coverage_summary(instrument_id, "1day")
    assert summary["total_rows"] == 5


def test_orchestrator_resumes_via_checkpoint_skips_completed_chunk(warehouse):
    instrument_id = "NSE_EQ|TEST"
    candles = [_fake_candle_row(dt.date(2024, 1, 1))]
    transport = make_fake_transport([FakeResponse(200, {"data": {"candles": candles}})])
    limiter = TokenBucketRateLimiter(RateLimitConfig(requests_per_second=1000, burst_capacity=10))
    config = DownloaderConfig()
    client = UpstoxHistoricalClient("fake-token", config, limiter, http_get=transport, sleep_fn=lambda s: None)
    orchestrator = DownloadOrchestrator(warehouse, config, client)

    job_id = "resume-job"
    orchestrator.run(job_id, instrument_id, Timeframe.DAY_1, dt.date(2024, 1, 1), dt.date(2024, 1, 1))
    assert transport.call_count() == 1

    # Second run against the SAME job_id and an overlapping force_refresh
    # request should hit the checkpoint (not the coverage gap logic) and
    # make zero additional HTTP calls for that already-completed chunk.
    result2 = orchestrator.run(job_id, instrument_id, Timeframe.DAY_1, dt.date(2024, 1, 1), dt.date(2024, 1, 1), force_refresh=True)
    assert transport.call_count() == 1  # no new call — checkpoint skip
    assert result2.chunks_skipped == 1


def test_batch_backfill_across_multiple_instruments(warehouse):
    instrument_ids = ["NSE_EQ|A", "NSE_EQ|B"]
    candles = [_fake_candle_row(dt.date(2024, 1, 1))]

    def fake_get(url, headers=None, timeout=None):
        return FakeResponse(200, {"data": {"candles": candles}})

    import warehouse.downloader.http_client as http_client_module
    original_default = http_client_module.requests.get
    http_client_module.requests.get = fake_get
    try:
        result = run_historical_backfill(
            warehouse, "fake-token", instrument_ids, [Timeframe.DAY_1],
            dt.date(2024, 1, 1), dt.date(2024, 1, 1),
            downloader_config=DownloaderConfig(max_parallel_downloads=2),
        )
    finally:
        http_client_module.requests.get = original_default

    assert result.is_fully_successful
    assert len(result.successes) == 2
    job = warehouse.job_manager.get_job(result.job_id)
    assert job.status == JobStatus.COMPLETED.value


def test_resolve_instrument_universe_reads_from_instrument_master(warehouse):
    """
    Uses a real sqlite3 connection against the EXACT schema shape from the
    live ng-signal-app's instrument_master/schema.py (instrument_key PK,
    trading_symbol, active_status TEXT enum) — not a guessed schema — to
    prove resolve_instrument_universe()'s soft-reference read path actually
    matches what InstrumentRegistry expects post-fix.
    """
    import sqlite3
    from warehouse.downloader.api import resolve_instrument_universe

    im_path = warehouse.config.resolved_paths().instrument_master_db_path
    im_path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(im_path)
    conn.execute(
        """
        CREATE TABLE instruments (
            instrument_key TEXT PRIMARY KEY, trading_symbol TEXT, exchange TEXT,
            segment TEXT, asset_class TEXT, active_status TEXT,
            created_at TEXT, row_updated_at TEXT
        )
        """
    )
    now = dt.datetime.now(dt.timezone.utc).isoformat()
    conn.execute(
        "INSERT INTO instruments VALUES (?,?,?,?,?,?,?,?)",
        ("NSE_EQ|INE002A01018", "RELIANCE", "NSE", "EQ", "equity", "ACTIVE", now, now),
    )
    conn.execute(
        "INSERT INTO instruments VALUES (?,?,?,?,?,?,?,?)",
        ("NSE_EQ|OLD", "OLDCO", "NSE", "EQ", "equity", "INACTIVE", now, now),
    )
    conn.execute(
        "INSERT INTO instruments VALUES (?,?,?,?,?,?,?,?)",
        ("MCX_FO|NATGAS", "NATURALGAS", "MCX", "FUT", "commodity_futures", "ACTIVE", now, now),
    )
    conn.commit()
    conn.close()

    all_active = resolve_instrument_universe(warehouse)
    assert set(all_active) == {"NSE_EQ|INE002A01018", "MCX_FO|NATGAS"}

    equities_only = resolve_instrument_universe(warehouse, asset_class="equity")
    assert equities_only == ["NSE_EQ|INE002A01018"]

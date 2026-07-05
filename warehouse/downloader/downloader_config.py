"""
warehouse.downloader.downloader_config
=========================================

Configuration specific to NGWH-002, kept as its own Pydantic model rather
than added to `warehouse.config.WarehouseConfig` — the downloader is a
consumer of the foundation, not part of it, and the foundation's config
contract stays frozen. A `DownloaderConfig` is always used alongside a
`WarehouseConfig`, never instead of it.

IMPORTANT — values that need verification before a production run:
`IntervalPolicyConfig.max_lookback_days` and `chunk_days_per_request` below
are configured defaults, not values scraped from live Upstox documentation
(this environment cannot reach api.upstox.com or upstox.com's docs to
confirm current limits at build time). Treat the defaults as conservative
placeholders: verify them against Upstox's current historical-candle API
documentation before running a full 10-year backfill, and adjust via
config rather than code changes if they're wrong — that's the entire
reason these are configuration, not constants.
"""

from __future__ import annotations

from pydantic import BaseModel, Field, field_validator

from warehouse.core.constants import Timeframe


class RateLimitConfig(BaseModel):
    """Token-bucket rate limiting shared across all parallel workers in a
    single process. Sized conservatively given the known Streamlit
    Community Cloud shared-IP 429 issue (see NGWH-001 infrastructure notes)
    — tune down further if 429s persist in production."""

    requests_per_second: float = Field(default=2.0, gt=0)
    burst_capacity: int = Field(default=4, gt=0)


class RetryConfig(BaseModel):
    max_retries: int = Field(default=5, ge=0)
    backoff_base_seconds: float = Field(default=1.0, gt=0)
    backoff_max_seconds: float = Field(default=60.0, gt=0)
    retry_on_status_codes: tuple[int, ...] = Field(default=(429, 500, 502, 503, 504))

    @field_validator("backoff_max_seconds")
    @classmethod
    def _max_ge_base(cls, v, info):
        base = info.data.get("backoff_base_seconds")
        if base is not None and v < base:
            raise ValueError("backoff_max_seconds must be >= backoff_base_seconds")
        return v


class IntervalPolicyEntry(BaseModel):
    """Per-timeframe download policy: which Upstox interval string to
    request, how many days of history are realistically available for it,
    and the maximum date span to request per single API call."""

    upstox_interval: str
    max_lookback_days: int = Field(gt=0)
    chunk_days_per_request: int = Field(gt=0)


class IntervalPolicyConfig(BaseModel):
    """
    Maps each downloadable Timeframe to its Upstox request policy.

    Only Timeframe.MIN_30 and Timeframe.DAY_1 are populated by default,
    matching NGWH-001's BASE_DOWNLOAD_TIMEFRAME (MIN_30) plus daily candles
    (used for longer history than intraday retention typically allows).
    5min/15min/1hour are intentionally DERIVED_TIMEFRAMES per the approved
    architecture — NGWH-002 does not fetch them directly; a future
    timeframe-derivation module resamples them from MIN_30.
    """

    entries: dict[Timeframe, IntervalPolicyEntry] = Field(
        default_factory=lambda: {
            Timeframe.MIN_30: IntervalPolicyEntry(
                upstox_interval="30minute",
                max_lookback_days=365 * 2,   # placeholder — verify intraday retention window
                chunk_days_per_request=30,    # placeholder — verify max span per intraday call
            ),
            Timeframe.DAY_1: IntervalPolicyEntry(
                upstox_interval="day",
                max_lookback_days=365 * 10,  # daily history is expected to extend much further back
                chunk_days_per_request=365,
            ),
            Timeframe.WEEK_1: IntervalPolicyEntry(
                upstox_interval="week",
                max_lookback_days=365 * 10,
                chunk_days_per_request=365 * 2,
            ),
        }
    )

    def get(self, timeframe: Timeframe) -> IntervalPolicyEntry:
        from warehouse.downloader.exceptions import UnsupportedIntervalError

        entry = self.entries.get(timeframe)
        if entry is None:
            raise UnsupportedIntervalError(
                f"No downloader interval policy for timeframe {timeframe.value!r}. "
                "This timeframe is likely a DERIVED_TIMEFRAMES target (resampled from "
                "MIN_30), not something the downloader fetches directly.",
                context={"timeframe": timeframe.value},
            )
        return entry


class DownloaderConfig(BaseModel):
    request_timeout_seconds: float = Field(default=15.0, gt=0)
    rate_limit: RateLimitConfig = Field(default_factory=RateLimitConfig)
    retry: RetryConfig = Field(default_factory=RetryConfig)
    interval_policy: IntervalPolicyConfig = Field(default_factory=IntervalPolicyConfig)
    max_parallel_downloads: int = Field(
        default=4, gt=0,
        description="Upper bound on concurrent instrument downloads in-process. "
        "Should generally stay <= WarehouseConfig.scale.max_parallel_jobs.",
    )
    catalog_upsert_batch: bool = Field(
        default=True,
        description="If True, catalog upsert happens once per partition write "
        "(recommended). Kept as a flag in case a future bulk-catalog-rebuild mode wants to skip it.",
    )

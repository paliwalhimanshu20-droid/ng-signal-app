"""
Smoke-test suite for NGWH-001 (Historical Intelligence Warehouse Foundation).

Run with:  pytest tests/test_foundation.py -v
(requires pytest; not otherwise a dependency of the warehouse package itself)

These tests exercise the foundation end-to-end against a temp directory:
config validation, bootstrap idempotency, Parquet write/read/dedupe,
DuckDB lake queries, catalog/version/checkpoint/job managers, and the
health checker. They intentionally do NOT test the instrument registry
against a real Instrument Master DB (that module is exercised separately
once NGSP-003A.1's live schema is confirmed).
"""

from __future__ import annotations

import datetime as dt

import pyarrow as pa
import pytest

from warehouse.bootstrap import WarehouseBootstrap, WarehouseHealthChecker
from warehouse.config import load_config
from warehouse.core.constants import CheckpointScope, JobStatus, JobType, Timeframe, WarehouseLayer
from warehouse.core.exceptions import InvalidJobStateTransitionError, SchemaMismatchError
from warehouse.metadata import CheckpointManager, JobManager, VersionManager, WarehouseMetadataManager
from warehouse.storage import OHLCV_SCHEMA, DuckDBManager, ParquetStorageManager, PartitionManager


@pytest.fixture()
def warehouse(tmp_path):
    cfg = load_config(paths={"root_dir": str(tmp_path / "warehouse")}, logging={"also_console": False})
    handles = WarehouseBootstrap(cfg).run()
    yield handles
    handles.duckdb_manager.close()


def _sample_table(instrument_id="NSE_EQ_TEST", n=3, start=dt.date(2024, 1, 1)):
    now = dt.datetime.now(dt.timezone.utc)
    timestamps = [dt.datetime(start.year, start.month, start.day + i, tzinfo=dt.timezone.utc) for i in range(n)]
    return pa.table(
        {
            "instrument_id": [instrument_id] * n,
            "timeframe": ["1day"] * n,
            "timestamp_utc": timestamps,
            "open": [100.0 + i for i in range(n)],
            "high": [105.0 + i for i in range(n)],
            "low": [99.0 + i for i in range(n)],
            "close": [104.0 + i for i in range(n)],
            "volume": [1000.0 + i for i in range(n)],
            "open_interest": [None] * n,
            "source": ["upstox_v2"] * n,
            "ingested_at_utc": [now] * n,
            "is_derived": [False] * n,
            "derived_from_timeframe": [None] * n,
            "schema_version": [1] * n,
        },
        schema=OHLCV_SCHEMA,
    )


def test_bootstrap_creates_expected_layout(warehouse):
    root = warehouse.config.resolved_paths().root_dir
    assert (root / "raw_ohlcv").exists()
    assert (root / "derived_timeframes").exists()
    assert (root / "_metadata" / "warehouse_metadata.duckdb").exists()


def test_bootstrap_is_idempotent(warehouse):
    handles2 = WarehouseBootstrap(warehouse.config).run()
    assert handles2.config.resolved_paths().root_dir == warehouse.config.resolved_paths().root_dir
    handles2.duckdb_manager.close()


def test_parquet_write_read_roundtrip(warehouse):
    key = warehouse.partition_manager.make_key(
        WarehouseLayer.RAW_OHLCV, "NSE_EQ_TEST", Timeframe.DAY_1, dt.date(2024, 1, 1)
    )
    table = _sample_table()
    result = warehouse.parquet_manager.write_partition(key, table, mode="overwrite")
    assert result.rows_written == 3

    readback = warehouse.parquet_manager.read_partition(key)
    assert readback.num_rows == 3


def test_parquet_append_deduplicates_on_primary_key(warehouse):
    key = warehouse.partition_manager.make_key(
        WarehouseLayer.RAW_OHLCV, "NSE_EQ_TEST", Timeframe.DAY_1, dt.date(2024, 1, 1)
    )
    warehouse.parquet_manager.write_partition(key, _sample_table(n=3), mode="overwrite")
    # second write overlaps day 3 (index 2) and adds day 4
    overlapping = _sample_table(n=2, start=dt.date(2024, 1, 3))
    result = warehouse.parquet_manager.write_partition(key, overlapping, mode="append")
    assert result.rows_written == 4  # 3 + 2 - 1 overlap


def test_schema_mismatch_rejected(warehouse):
    key = warehouse.partition_manager.make_key(
        WarehouseLayer.RAW_OHLCV, "NSE_EQ_TEST", Timeframe.DAY_1, dt.date(2024, 1, 1)
    )
    bad_table = pa.table({"instrument_id": ["X"], "timeframe": ["1day"]})
    with pytest.raises(SchemaMismatchError):
        warehouse.parquet_manager.write_partition(key, bad_table)


def test_catalog_upsert_and_coverage(warehouse):
    key = warehouse.partition_manager.make_key(
        WarehouseLayer.RAW_OHLCV, "NSE_EQ_TEST", Timeframe.DAY_1, dt.date(2024, 1, 1)
    )
    warehouse.metadata_manager.upsert_entry(
        key, row_count=3, min_timestamp_utc=None, max_timestamp_utc=None,
        file_size_bytes=1000, sha256="deadbeef", schema_version=1,
    )
    entry = warehouse.metadata_manager.get_entry(key)
    assert entry.row_count == 3
    summary = warehouse.metadata_manager.coverage_summary("NSE_EQ_TEST", "1day")
    assert summary["total_rows"] == 3


def test_job_manager_rejects_illegal_transition(warehouse):
    job_id = warehouse.job_manager.create_job(JobType.BACKFILL_DOWNLOAD, {"x": 1})
    warehouse.job_manager.transition(job_id, JobStatus.RUNNING)
    warehouse.job_manager.transition(job_id, JobStatus.COMPLETED)
    with pytest.raises(InvalidJobStateTransitionError):
        warehouse.job_manager.transition(job_id, JobStatus.RUNNING)


def test_checkpoint_round_trip(warehouse):
    warehouse.checkpoint_manager.save_checkpoint(
        "job-x", CheckpointScope.INSTRUMENT_TIMEFRAME, "NSE_EQ_TEST/1day", {"last": "2024-01-03"}
    )
    cp = warehouse.checkpoint_manager.load_checkpoint(
        "job-x", CheckpointScope.INSTRUMENT_TIMEFRAME, "NSE_EQ_TEST/1day"
    )
    assert cp.payload["last"] == "2024-01-03"
    assert cp.is_complete is False


def test_health_checker_reports_healthy_on_fresh_bootstrap(warehouse):
    checker = WarehouseHealthChecker(warehouse.config, warehouse.duckdb_manager, warehouse.partition_manager)
    report = checker.run()
    assert report.health_score == 100.0


def test_duckdb_lake_query_after_write(warehouse):
    key = warehouse.partition_manager.make_key(
        WarehouseLayer.RAW_OHLCV, "NSE_EQ_TEST", Timeframe.DAY_1, dt.date(2024, 1, 1)
    )
    warehouse.parquet_manager.write_partition(key, _sample_table(), mode="overwrite")
    df = warehouse.duckdb_manager.query_lake(
        WarehouseLayer.RAW_OHLCV, "SELECT count(*) AS n FROM {source}", timeframe=Timeframe.DAY_1
    )
    assert df["n"].iloc[0] == 3

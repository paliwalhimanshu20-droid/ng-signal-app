"""
warehouse_admin/stats.py

Pure data-computation functions for the Warehouse Dashboard and Warehouse
Statistics pages — deliberately free of any `streamlit` import so these
can be unit tested directly (mirrors this codebase's existing split, e.g.
risk_engine.py computes / ui_components.py renders). The corresponding
render_*.py modules in this package call these functions and format the
results with st.metric/st.dataframe/etc.

Every function takes a `WarehouseHandles` (from warehouse.bootstrap) and
returns plain dicts/dataclasses — no Streamlit objects, no side effects
beyond read-only queries against the warehouse's own metadata and the
Instrument Master (via the existing read-only InstrumentRegistry).
"""

from __future__ import annotations

import shutil
from dataclasses import dataclass, field
from datetime import datetime
from typing import Optional

from warehouse.bootstrap.bootstrap import WarehouseHandles
from warehouse.bootstrap.health_checker import WarehouseHealthChecker, HealthReport
from warehouse.core.constants import (
    CHECKPOINT_TABLE, JobStatus, JobType,
    INSTRUMENT_MASTER_ACTIVE_FLAG_FIELD, INSTRUMENT_MASTER_ACTIVE_VALUE,
)
from warehouse.registry.instrument_registry import InstrumentRegistry


@dataclass
class DashboardStats:
    total_instruments: Optional[int]
    active_instruments: Optional[int]
    total_candles: int
    years_of_coverage: float
    storage_used_bytes: int
    partition_count: int
    duckdb_status: str
    metadata_db_status: str
    last_successful_download: Optional[datetime]
    last_incremental_update: Optional[datetime]
    jobs_running: int
    jobs_completed: int
    jobs_failed: int
    resume_checkpoints_pending: int
    health_score_percent: float
    overall_status: str
    instrument_master_available: bool


@dataclass
class WarehouseStatistics:
    total_partitions: int
    total_files: int
    storage_size_bytes: int
    average_partition_size_bytes: float
    coverage_percent: Optional[float]
    earliest_date: Optional[datetime]
    latest_date: Optional[datetime]
    missing_instruments: list = field(default_factory=list)
    data_quality_summary: str = ""


def _instrument_counts(handles: WarehouseHandles) -> tuple[Optional[int], Optional[int], bool]:
    """Returns (total, active, available) — (None, None, False) if the
    Instrument Master isn't reachable, which is expected/non-fatal (same
    UNKNOWN-not-FAIL treatment as the health checker and validator use).

    PERFORMANCE (audit finding): this used to call
    registry.list_active_instruments() and take len(...) of the result —
    fetching and materializing an InstrumentRecord for every one of
    126,644+ rows just to get a count. Measured at ~700ms-1s per call on
    the real production-scale Instrument Master, repeated on every single
    call to compute_dashboard_stats()/compute_warehouse_statistics(). Both
    counts are now direct SQL COUNT(*) queries (microseconds), using the
    exact same field/value constants the frozen registry itself uses —
    this does not touch instrument_registry.py or change what "active"
    means, it just stops materializing rows nobody reads.
    """
    db_path = handles.config.resolved_paths().instrument_master_db_path
    try:
        import sqlite3
        with sqlite3.connect(f"file:{db_path}?mode=ro", uri=True) as conn:
            total = conn.execute("SELECT COUNT(*) FROM instruments").fetchone()[0]
            active = conn.execute(
                f"SELECT COUNT(*) FROM instruments WHERE {INSTRUMENT_MASTER_ACTIVE_FLAG_FIELD} = ?",
                (INSTRUMENT_MASTER_ACTIVE_VALUE,),
            ).fetchone()[0]
    except Exception:
        return None, None, False

    return total, active, True


def _catalog_aggregate(handles: WarehouseHandles) -> dict:
    """Single aggregate query over the whole catalog table — total rows,
    total bytes, partition count, earliest/latest timestamp. One query
    instead of Python-side iteration over list_entries(), since this needs
    to stay fast even at the 100-instrument / 10-year target scale."""
    from warehouse.core.constants import METADATA_CATALOG_TABLE

    with handles.duckdb_manager.metadata_cursor() as con:
        row = con.execute(
            f"""
            SELECT count(*) AS partitions, sum(row_count) AS total_rows,
                   sum(file_size_bytes) AS total_bytes,
                   min(min_timestamp_utc) AS earliest, max(max_timestamp_utc) AS latest
            FROM {METADATA_CATALOG_TABLE}
            """
        ).fetchone()
    partitions, total_rows, total_bytes, earliest, latest = row
    return {
        "partitions": partitions or 0,
        "total_rows": int(total_rows) if total_rows else 0,
        "total_bytes": int(total_bytes) if total_bytes else 0,
        "earliest": earliest,
        "latest": latest,
    }


def _pending_checkpoint_count(handles: WarehouseHandles) -> int:
    """Count of NOT-yet-complete checkpoints system-wide. CheckpointManager
    only exposes per-job `list_incomplete(job_id)`; this reads the same
    table directly (read-only aggregate) rather than looping job-by-job,
    since the dashboard wants one number across every job at once."""
    with handles.duckdb_manager.metadata_cursor() as con:
        row = con.execute(f"SELECT count(*) FROM {CHECKPOINT_TABLE} WHERE is_complete = FALSE").fetchone()
    return int(row[0]) if row else 0


def _covered_instrument_ids(handles: WarehouseHandles) -> set[str]:
    """
    PERFORMANCE FIX: distinct instrument_ids present in the catalog table,
    via a single `SELECT DISTINCT` — not `handles.metadata_manager.list_entries()`
    (which was being used here).

    Measured against production scale: list_entries() with no filter runs
    `SELECT * FROM warehouse_catalog ORDER BY instrument_id, timeframe,
    year, month`, fetches every row (one per instrument x timeframe x
    year x month partition ever downloaded), and constructs a full
    CatalogEntry dataclass for each — then this call site immediately
    threw all of that away except the instrument_id, to build a Python
    set for a membership check. Confirmed via instrumented per-tab timing
    logs (5 consecutive reruns, 23.6s-27.5s each) as the sole cause of a
    2-3 minute perceived UI freeze: this function is called from the
    Warehouse Statistics tab, which — like every Streamlit st.tabs()
    body — re-executes on every single rerun of the entire app once its
    15s cache TTL expires, regardless of which page or widget the user
    actually interacted with.

    This query asks the database for exactly what's needed (the distinct
    set of covered instrument_ids) with no ORDER BY and no per-row Python
    object construction. `missing_instruments` and `coverage_percent`
    below are computed identically to before — same inputs, same
    comparison, same output shape — only the source of `covered_ids`
    changed.

    NOTE: `warehouse.bootstrap.health_checker.py`'s `_check_catalog_consistency()`
    also calls `list_entries()` unfiltered, for a different purpose
    (sampling entries to verify their Parquet files exist on disk). That
    is a separate call site, used by both the Dashboard and Statistics
    tabs via WarehouseHealthChecker.run() — Dashboard stayed fast
    (25-50ms) in every rerun of the diagnostic log, so it isn't currently
    the active cost driver, but it is the same underlying query and
    hasn't been touched here (out of scope for this fix — flagged for a
    separate look if it ever shows up as slow).
    """
    from warehouse.core.constants import METADATA_CATALOG_TABLE

    with handles.duckdb_manager.metadata_cursor() as con:
        rows = con.execute(f"SELECT DISTINCT instrument_id FROM {METADATA_CATALOG_TABLE}").fetchall()
    return {r[0] for r in rows}


def _last_job_finished_at(handles: WarehouseHandles, job_type: JobType) -> Optional[datetime]:
    jobs = handles.job_manager.list_jobs(status=JobStatus.COMPLETED, job_type=job_type)
    finished = [j.finished_at_utc for j in jobs if j.finished_at_utc is not None]
    return max(finished) if finished else None


def compute_dashboard_stats(handles: WarehouseHandles) -> DashboardStats:
    total_instruments, active_instruments, im_available = _instrument_counts(handles)
    agg = _catalog_aggregate(handles)

    years_of_coverage = 0.0
    if agg["earliest"] and agg["latest"]:
        years_of_coverage = round((agg["latest"] - agg["earliest"]).days / 365.25, 2)

    checker = WarehouseHealthChecker(handles.config, handles.duckdb_manager, handles.partition_manager)
    health_report: HealthReport = checker.run()
    category_by_name = {c.name: c for c in health_report.categories}

    running = len(handles.job_manager.list_jobs(status=JobStatus.RUNNING))
    completed = len(handles.job_manager.list_jobs(status=JobStatus.COMPLETED))
    failed = len(handles.job_manager.list_jobs(status=JobStatus.FAILED))

    last_backfill = _last_job_finished_at(handles, JobType.BACKFILL_DOWNLOAD)
    last_incremental = _last_job_finished_at(handles, JobType.INCREMENTAL_UPDATE)
    last_download = max([d for d in (last_backfill, last_incremental) if d is not None], default=None)

    return DashboardStats(
        total_instruments=total_instruments,
        active_instruments=active_instruments,
        total_candles=agg["total_rows"],
        years_of_coverage=years_of_coverage,
        storage_used_bytes=agg["total_bytes"],
        partition_count=agg["partitions"],
        duckdb_status=category_by_name.get("metadata_db").status.value if category_by_name.get("metadata_db") else "unknown",
        metadata_db_status=category_by_name.get("schema_version").status.value if category_by_name.get("schema_version") else "unknown",
        last_successful_download=last_download,
        last_incremental_update=last_incremental,
        jobs_running=running,
        jobs_completed=completed,
        jobs_failed=failed,
        resume_checkpoints_pending=_pending_checkpoint_count(handles),
        health_score_percent=health_report.health_score,
        overall_status=health_report.overall_status.value,
        instrument_master_available=im_available,
    )


def compute_warehouse_statistics(handles: WarehouseHandles) -> WarehouseStatistics:
    agg = _catalog_aggregate(handles)
    avg_size = (agg["total_bytes"] / agg["partitions"]) if agg["partitions"] else 0.0

    total_instruments, active_instruments, im_available = _instrument_counts(handles)
    coverage_percent = None
    missing_instruments: list = []

    if im_available and active_instruments:
        covered_ids = _covered_instrument_ids(handles)
        db_path = handles.config.resolved_paths().instrument_master_db_path
        registry = InstrumentRegistry(db_path)
        active_records = registry.list_active_instruments()
        missing_instruments = [r.instrument_id for r in active_records if r.instrument_id not in covered_ids]
        covered_count = len(active_records) - len(missing_instruments)
        coverage_percent = round(100.0 * covered_count / len(active_records), 1) if active_records else None

    # Data quality: delegate to the existing catalog_consistency health
    # check rather than re-implementing partition-file-existence scanning.
    checker = WarehouseHealthChecker(handles.config, handles.duckdb_manager, handles.partition_manager)
    health_report = checker.run()
    consistency = next((c for c in health_report.categories if c.name == "catalog_consistency"), None)
    data_quality_summary = consistency.detail if consistency else "Not checked."

    return WarehouseStatistics(
        total_partitions=agg["partitions"],
        total_files=agg["partitions"],  # one Parquet file per partition, per PartitionManager's layout
        storage_size_bytes=agg["total_bytes"],
        average_partition_size_bytes=round(avg_size, 1),
        coverage_percent=coverage_percent,
        earliest_date=agg["earliest"],
        latest_date=agg["latest"],
        missing_instruments=missing_instruments,
        data_quality_summary=data_quality_summary,
    )


def format_bytes(num_bytes: int) -> str:
    """Same formatting convention as warehouse.core.utils.bytes_to_human,
    duplicated here at small scale rather than importing a NGWH-001
    internal helper not exposed in its public __init__ surface."""
    value = float(num_bytes)
    for unit in ("B", "KB", "MB", "GB", "TB"):
        if value < 1024.0:
            return f"{value:.1f} {unit}"
        value /= 1024.0
    return f"{value:.1f} PB"

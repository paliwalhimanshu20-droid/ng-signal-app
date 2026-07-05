"""
warehouse_admin/progress_monitor.py

Live progress monitoring, built on NGWH-001's `warehouse.progress` registry
(in-memory, process-wide `ProgressTracker` instances) plus the most recent
`BatchResult` the Historical Downloader page stashes in `st.session_state`
after a run completes.

Important limitation, consistent with job_management.py's honesty about
this app's execution model: `ProgressTracker` snapshots are only populated
DURING a synchronous `run_historical_backfill()`/`run_daily_incremental_update()`
call — i.e. while that exact Streamlit script run is blocked executing it.
Once that script run finishes (success or failure) and Streamlit reruns,
the in-memory tracker is gone (per NGWH-001's design: ProgressTracker is
explicitly NOT persisted, see its own module docstring). So this module
also surfaces the LAST COMPLETED run's summary (from session_state) so the
Progress page still shows something meaningful after a run finishes,
rather than going blank.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Optional

from warehouse.progress.progress_tracker import ProgressSnapshot, get_active_progress


@dataclass
class ProgressView:
    is_live: bool
    label: Optional[str] = None
    percent_complete: Optional[float] = None
    completed_units: Optional[int] = None
    total_units: Optional[int] = None
    elapsed_seconds: Optional[float] = None
    eta_seconds: Optional[float] = None
    last_run_summary: Optional[dict] = None


def get_live_progress() -> list[ProgressSnapshot]:
    """Every ProgressTracker currently active in this process. In this
    app's execution model (see module docstring) this will only be
    non-empty from CODE THAT RUNS AFTER a backfill has started but before
    it returns — which, for a synchronous call, means it's only
    observable if something else polls it concurrently (not the same
    script run that kicked it off). Kept as a real, correct primitive
    regardless; a future async/background execution mode would make this
    immediately useful without any change here.
    """
    return get_active_progress()


def summarize_last_batch_result(batch_result) -> dict:
    """
    Converts a `warehouse.downloader.BatchResult` (returned by
    run_historical_backfill/run_daily_incremental_update, stashed in
    st.session_state by the Historical Downloader page after a run) into
    the plain-dict shape the Progress page renders.
    """
    return {
        "job_id": batch_result.job_id,
        "instruments_completed": len(batch_result.successes),
        "instruments_failed": len(batch_result.failures),
        "total_candles_downloaded": batch_result.total_rows_written,
        "is_fully_successful": batch_result.is_fully_successful,
        "failures": [
            {"instrument_id": f.instrument_id, "timeframe": f.timeframe, "error": f.error}
            for f in batch_result.failures
        ],
    }


def build_progress_view(last_batch_result=None) -> ProgressView:
    live = get_live_progress()
    if live:
        snap = live[0]  # a single Streamlit session drives at most one backfill at a time
        return ProgressView(
            is_live=True,
            label=snap.label,
            percent_complete=snap.percent_complete,
            completed_units=snap.completed_units,
            total_units=snap.total_units,
            elapsed_seconds=snap.elapsed_seconds,
            eta_seconds=snap.estimated_remaining_seconds,
        )

    if last_batch_result is not None:
        return ProgressView(is_live=False, last_run_summary=summarize_last_batch_result(last_batch_result))

    return ProgressView(is_live=False)

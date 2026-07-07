"""
warehouse_admin/job_management.py

Job Management logic for NGWH-003 — built entirely on NGWH-001's
JobManager + CheckpointManager (frozen; nothing here modifies either) and
NGWH-002's checkpoint-driven resumability (also frozen).

IMPORTANT — honest scope of "Pause"/"Cancel" here, given how this app runs:
Every backfill/incremental-update call in NGWH-002 (`BatchRunner._execute`)
runs synchronously to completion within a single Streamlit script
execution — there is no separate always-on worker process this UI can signal
mid-flight the way a Celery/RQ task queue could. So:

  - A job's `RUNNING` status you see here almost always means "a previous
    Streamlit run started this and the browser tab was closed, the
    process crashed, or Streamlit Community Cloud restarted mid-run" —
    not "actively executing in this exact process right now" (if it WERE
    still actively running in this exact process, this code couldn't be
    running at all, since Python is single-threaded per script run and
    BatchRunner blocks until done).
  - "Pause" and "Cancel" here are JOB-STATE bookkeeping actions: they flip
    the JobManager row to PAUSED/CANCELLED so (a) the Job Management table
    stops showing it as falsely RUNNING forever, and (b) a future
    Restart/Resume action knows to re-invoke the backfill (which, thanks
    to NGWH-002's checkpointing, only re-fetches whatever chunks weren't
    marked complete — it does NOT re-download from scratch).
  - This is stated explicitly in the UI (render_job_management.py) rather
    than implying a live interrupt this architecture cannot actually
    provide. A true live-pause would require NGWH-002's BatchRunner loop
    itself to check a cooperative cancellation flag between chunks, which
    is NGWH-002 internals and out of scope for NGWH-003 (frozen).

=== TEMPORARY TIMING INSTRUMENTATION (this session) ===
list_job_history() and find_stale_running_jobs() are the two functions in
this file with no caching decorator, called unconditionally on every
render of the Jobs tab (i.e. every rerun of the entire app, per
st.tabs()'s non-lazy execution). Both are wrapped with before/after
timing around the actual handles.job_manager.* call — nothing about
their logic, arguments, or return values is changed. Remove `_timed`/
`import time`/`_now_iso` and the two `with _timed(...)` blocks once the
slow stage is identified.
"""

from __future__ import annotations

import time
from contextlib import contextmanager
from dataclasses import dataclass
from datetime import datetime, timedelta
from datetime import datetime as _dt
from typing import Optional

from warehouse.bootstrap.bootstrap import WarehouseHandles
from warehouse.core.constants import CheckpointScope, JobStatus, JobType, Timeframe
from warehouse.core.exceptions import InvalidJobStateTransitionError, JobNotFoundError
from warehouse.metadata.job_manager import Job

_SLOW_THRESHOLD_MS = 50.0


def _now_iso() -> str:
    return _dt.now().isoformat(timespec="milliseconds")


@contextmanager
def _timed(label: str):
    t0 = time.perf_counter()
    print(f"[TIMING BEFORE] {label:<55s} @ {_now_iso()}")
    try:
        yield
    finally:
        ms = (time.perf_counter() - t0) * 1000
        flag = "  <<< SLOW (>50ms)" if ms > _SLOW_THRESHOLD_MS else ""
        print(f"[TIMING AFTER]  {label:<55s} @ {_now_iso()}  {ms:9.2f} ms{flag}")


# Job types this UI knows how to re-invoke on Resume/Restart. Any other
# job_type (e.g. a future SCHEMA_MIGRATION job) is shown read-only in the
# job history table with resume/restart actions disabled, rather than
# guessing at how to re-run it.
_RESUMABLE_JOB_TYPES = {JobType.BACKFILL_DOWNLOAD, JobType.INCREMENTAL_UPDATE}


@dataclass
class JobActionResult:
    success: bool
    message: str


@dataclass
class ResumePlan:
    """What re-invoking a job's underlying backfill/incremental call would
    look like, reconstructed from the job's own stored params — shown to
    the user for confirmation before Resume actually re-runs anything."""
    job_type: JobType
    instrument_ids: list[str]
    timeframes: list[Timeframe]
    start_date: Optional[str]
    end_date: Optional[str]
    lookback_days: Optional[int]
    force_refresh: bool
    pending_checkpoints: int


def list_job_history(handles: WarehouseHandles, *, limit: int = 100) -> list[Job]:
    """Every job, newest first, capped at `limit` for table rendering."""
    with _timed(f"job_management.list_job_history(): handles.job_manager.list_jobs()"):
        jobs = handles.job_manager.list_jobs()
    return jobs[:limit]


def pending_checkpoint_count_for_job(handles: WarehouseHandles, job_id: str) -> int:
    with _timed(f"job_management.pending_checkpoint_count_for_job(): checkpoint_manager.list_incomplete()"):
        result = len(handles.checkpoint_manager.list_incomplete(job_id))
    return result


def build_resume_plan(handles: WarehouseHandles, job: Job) -> Optional[ResumePlan]:
    """Reconstructs a ResumePlan from a job's stored params. Returns None
    if this job_type isn't resumable (see _RESUMABLE_JOB_TYPES)."""
    job_type = JobType(job.job_type)
    if job_type not in _RESUMABLE_JOB_TYPES:
        return None

    params = job.params
    timeframes = [Timeframe(t) for t in params.get("timeframes", [])]
    pending = pending_checkpoint_count_for_job(handles, job.job_id)

    return ResumePlan(
        job_type=job_type,
        instrument_ids=params.get("instrument_ids", []),
        timeframes=timeframes,
        start_date=params.get("start_date"),
        end_date=params.get("end_date"),
        lookback_days=params.get("lookback_days"),
        force_refresh=params.get("force_refresh", False),
        pending_checkpoints=pending,
    )


def request_pause(handles: WarehouseHandles, job_id: str) -> JobActionResult:
    """
    Marks a RUNNING job as PAUSED. See module docstring — this is
    bookkeeping (stops the job from being misreported as live-running
    forever), not a live interrupt of an in-flight download loop.
    """
    try:
        handles.job_manager.transition(job_id, JobStatus.PAUSED)
        return JobActionResult(True, f"Job {job_id} marked PAUSED. Use Resume to continue from its last checkpoint.")
    except JobNotFoundError:
        return JobActionResult(False, f"Job {job_id} not found.")
    except InvalidJobStateTransitionError as e:
        return JobActionResult(False, f"Cannot pause job {job_id}: {e}")


def request_cancel(handles: WarehouseHandles, job_id: str) -> JobActionResult:
    """Marks a job CANCELLED. Already-written partitions and catalog
    entries are NOT rolled back (they're valid data, not a failed write) —
    only the job's own bookkeeping row changes state."""
    try:
        handles.job_manager.transition(job_id, JobStatus.CANCELLED)
        return JobActionResult(True, f"Job {job_id} marked CANCELLED.")
    except JobNotFoundError:
        return JobActionResult(False, f"Job {job_id} not found.")
    except InvalidJobStateTransitionError as e:
        return JobActionResult(False, f"Cannot cancel job {job_id}: {e}")


def find_stale_running_jobs(handles: WarehouseHandles, *, stale_after_minutes: int = 30) -> list[Job]:
    """
    Surfaces jobs stuck in RUNNING with no recent heartbeat — the most
    common real-world case in this architecture (see module docstring):
    a prior Streamlit run started a backfill and the process ended before
    it reached a terminal state. Uses JobManager.stale_running_jobs()
    (NGWH-001, already exists) rather than reimplementing the heartbeat
    check.
    """
    with _timed(f"job_management.find_stale_running_jobs(): handles.job_manager.stale_running_jobs()"):
        result = handles.job_manager.stale_running_jobs(stale_after_minutes * 60)
    return result


def restart_failed_job_as_new(handles: WarehouseHandles, job: Job) -> tuple[Optional[str], JobActionResult]:
    """
    'Restart Failed Jobs': creates a NEW job with the same params as a
    FAILED one (rather than trying to force the old job's terminal FAILED
    status back to RUNNING, which JobManager's state machine correctly
    forbids — FAILED is terminal by design). The new job benefits from the
    same checkpoints the old job left behind, since checkpoints are keyed
    by job_id... which means a genuinely NEW job_id starts with NO
    checkpoint history of its own.

    This is called out explicitly in the returned message: restarting as
    a new job re-downloads everything (no checkpoint reuse across
    different job_ids), whereas Resume (for a PAUSED/stale-RUNNING job,
    not a FAILED one) reuses the ORIGINAL job_id's checkpoints. Use Resume
    when possible; Restart is the fallback for a job that's already FAILED.
    """
    if JobStatus(job.status) != JobStatus.FAILED:
        return None, JobActionResult(False, f"Job {job.job_id} is not FAILED (status={job.status}) — nothing to restart.")

    job_type = JobType(job.job_type)
    if job_type not in _RESUMABLE_JOB_TYPES:
        return None, JobActionResult(False, f"Job type {job.job_type} does not support restart.")

    new_job_id = handles.job_manager.create_job(job_type, job.params)
    return new_job_id, JobActionResult(
        True,
        f"Created new job {new_job_id} with the same parameters as failed job {job.job_id}. "
        "This starts fresh (a new job_id has no checkpoint history of its own) — "
        "trigger it from the Historical Downloader page using the same instrument/timeframe/date selection.",
    )

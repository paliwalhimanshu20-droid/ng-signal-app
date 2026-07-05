"""
warehouse.progress.progress_tracker
======================================

Lightweight, dependency-free progress tracking for long-running warehouse
operations (a future 100-instrument backfill, a timeframe derivation pass,
etc.). This foundation module does not run any such job — it defines the
tracker object those jobs will report progress through, and a thread-safe
in-memory registry so a Streamlit admin panel (future) can poll "what's
running right now and how far along is it" without needing to query the
JobManager's DuckDB table on every rerun.

Design note: this is intentionally in-memory, not persisted. Durable
resume state lives in CheckpointManager; ProgressTracker is for
human-facing "X of Y done, ETA Z" reporting during a single process's
lifetime, which is a different concern from crash-safe resumption.
"""

from __future__ import annotations

import threading
import time
from dataclasses import dataclass, field
from typing import Optional


@dataclass
class ProgressSnapshot:
    job_id: str
    label: str
    total_units: int
    completed_units: int
    failed_units: int
    started_at: float
    updated_at: float

    @property
    def percent_complete(self) -> float:
        if self.total_units <= 0:
            return 0.0
        return round(100.0 * self.completed_units / self.total_units, 1)

    @property
    def elapsed_seconds(self) -> float:
        return self.updated_at - self.started_at

    @property
    def estimated_remaining_seconds(self) -> Optional[float]:
        if self.completed_units <= 0:
            return None
        rate = self.completed_units / max(self.elapsed_seconds, 1e-9)
        remaining_units = max(self.total_units - self.completed_units, 0)
        return round(remaining_units / rate, 1) if rate > 0 else None


class ProgressTracker:
    """Tracks progress for a single job. Thread-safe for concurrent
    `advance()` calls from parallel worker threads (e.g. a future parallel
    downloader fanning out across instruments)."""

    def __init__(self, job_id: str, label: str, total_units: int):
        self._lock = threading.Lock()
        now = time.time()
        self._state = ProgressSnapshot(
            job_id=job_id, label=label, total_units=total_units,
            completed_units=0, failed_units=0, started_at=now, updated_at=now,
        )
        _REGISTRY.register(self)

    @property
    def job_id(self) -> str:
        return self._state.job_id

    def advance(self, units: int = 1, *, failed: bool = False) -> None:
        with self._lock:
            if failed:
                self._state.failed_units += units
            else:
                self._state.completed_units += units
            self._state.updated_at = time.time()

    def snapshot(self) -> ProgressSnapshot:
        with self._lock:
            # Return a copy so callers can't mutate internal state.
            return ProgressSnapshot(**vars(self._state))

    def finish(self) -> None:
        """Remove this tracker from the process-wide registry. Call when the
        job reaches a terminal state so the admin panel doesn't show stale
        'running' progress forever."""
        _REGISTRY.unregister(self.job_id)


class _ProgressRegistry:
    """Process-wide registry of active ProgressTrackers, keyed by job_id."""

    def __init__(self):
        self._lock = threading.Lock()
        self._trackers: dict[str, ProgressTracker] = {}

    def register(self, tracker: ProgressTracker) -> None:
        with self._lock:
            self._trackers[tracker.job_id] = tracker

    def unregister(self, job_id: str) -> None:
        with self._lock:
            self._trackers.pop(job_id, None)

    def get(self, job_id: str) -> Optional[ProgressTracker]:
        with self._lock:
            return self._trackers.get(job_id)

    def all_snapshots(self) -> list[ProgressSnapshot]:
        with self._lock:
            trackers = list(self._trackers.values())
        return [t.snapshot() for t in trackers]


_REGISTRY = _ProgressRegistry()


def get_active_progress() -> list[ProgressSnapshot]:
    """Return snapshots for every currently-active ProgressTracker in this
    process. Intended for a future Streamlit admin panel to poll."""
    return _REGISTRY.all_snapshots()

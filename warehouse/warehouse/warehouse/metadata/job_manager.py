"""
warehouse.metadata.job_manager
=================================

Tracks the lifecycle of warehouse jobs (backfills, incremental updates,
timeframe derivation runs, schema migrations, health checks) as a state
machine. This module implements job *bookkeeping* only — it does not
execute any job. The future downloader will call `create_job()`,
`transition()`, and `heartbeat()`; NGWH-001 just guarantees that state is
persisted durably and that illegal transitions are rejected loudly rather
than silently corrupting job history.

State machine:

    PENDING -> RUNNING -> COMPLETED
    RUNNING -> FAILED
    RUNNING -> PAUSED -> RUNNING
    (any non-terminal state) -> CANCELLED
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime
from typing import Any, Optional

from warehouse.core.constants import JOB_TABLE, JobStatus, JobType
from warehouse.core.exceptions import InvalidJobStateTransitionError, JobNotFoundError
from warehouse.core.logging_config import get_logger
from warehouse.core.utils import new_uuid, utc_now
from warehouse.storage.duckdb_manager import DuckDBManager

logger = get_logger(__name__)

_CREATE_TABLE_SQL = f"""
CREATE TABLE IF NOT EXISTS {JOB_TABLE} (
    job_id VARCHAR PRIMARY KEY,
    job_type VARCHAR NOT NULL,
    status VARCHAR NOT NULL,
    params_json VARCHAR NOT NULL,
    error_message VARCHAR,
    created_at_utc TIMESTAMP NOT NULL,
    updated_at_utc TIMESTAMP NOT NULL,
    started_at_utc TIMESTAMP,
    finished_at_utc TIMESTAMP,
    last_heartbeat_utc TIMESTAMP
);
"""

# Legal transitions. Terminal states have no outgoing transitions except
# none (a job is done once COMPLETED/FAILED/CANCELLED).
_ALLOWED_TRANSITIONS: dict[JobStatus, set[JobStatus]] = {
    JobStatus.PENDING: {JobStatus.RUNNING, JobStatus.CANCELLED},
    JobStatus.RUNNING: {JobStatus.COMPLETED, JobStatus.FAILED, JobStatus.PAUSED, JobStatus.CANCELLED},
    JobStatus.PAUSED: {JobStatus.RUNNING, JobStatus.CANCELLED},
    JobStatus.COMPLETED: set(),
    JobStatus.FAILED: set(),
    JobStatus.CANCELLED: set(),
}


@dataclass(frozen=True)
class Job:
    job_id: str
    job_type: str
    status: str
    params: dict
    error_message: Optional[str]
    created_at_utc: datetime
    updated_at_utc: datetime
    started_at_utc: Optional[datetime]
    finished_at_utc: Optional[datetime]
    last_heartbeat_utc: Optional[datetime]


class JobManager:
    """Durable job lifecycle tracking with an enforced state machine."""

    def __init__(self, duckdb_manager: DuckDBManager):
        self._db = duckdb_manager
        self._ensure_table()

    def _ensure_table(self) -> None:
        with self._db.metadata_cursor() as con:
            con.execute(_CREATE_TABLE_SQL)

    def create_job(self, job_type: JobType, params: dict[str, Any]) -> str:
        job_id = new_uuid()
        now = utc_now()
        with self._db.metadata_cursor() as con:
            con.execute(
                f"""
                INSERT INTO {JOB_TABLE}
                    (job_id, job_type, status, params_json, created_at_utc, updated_at_utc)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                [job_id, job_type.value, JobStatus.PENDING.value, json.dumps(params, default=str), now, now],
            )
        logger.info(f"Job created: {job_id} type={job_type.value}")
        return job_id

    def get_job(self, job_id: str) -> Job:
        with self._db.metadata_cursor() as con:
            row = con.execute(f"SELECT * FROM {JOB_TABLE} WHERE job_id = ?", [job_id]).fetchone()
        if row is None:
            raise JobNotFoundError("No job found", context={"job_id": job_id})
        return _row_to_job(row)

    def transition(self, job_id: str, new_status: JobStatus, *, error_message: Optional[str] = None) -> Job:
        """Move a job to `new_status`, enforcing the legal-transition state
        machine. Raises InvalidJobStateTransitionError on an illegal move."""
        job = self.get_job(job_id)
        current = JobStatus(job.status)

        if new_status not in _ALLOWED_TRANSITIONS[current]:
            raise InvalidJobStateTransitionError(
                f"Cannot transition job {job_id} from {current.value} to {new_status.value}",
                context={"job_id": job_id, "from": current.value, "to": new_status.value},
            )

        now = utc_now()
        started_at = job.started_at_utc
        finished_at = job.finished_at_utc
        if new_status == JobStatus.RUNNING and started_at is None:
            started_at = now
        if new_status in (JobStatus.COMPLETED, JobStatus.FAILED, JobStatus.CANCELLED):
            finished_at = now

        with self._db.metadata_cursor() as con:
            con.execute(
                f"""
                UPDATE {JOB_TABLE}
                SET status=?, error_message=?, updated_at_utc=?, started_at_utc=?, finished_at_utc=?
                WHERE job_id=?
                """,
                [new_status.value, error_message, now, started_at, finished_at, job_id],
            )
        logger.info(f"Job {job_id} transitioned {current.value} -> {new_status.value}")
        return self.get_job(job_id)

    def heartbeat(self, job_id: str) -> None:
        """Update last_heartbeat_utc for a running job. A future job runner
        would call this periodically; a future health checker can flag jobs
        stuck in RUNNING with a stale heartbeat as likely-crashed."""
        with self._db.metadata_cursor() as con:
            con.execute(
                f"UPDATE {JOB_TABLE} SET last_heartbeat_utc=? WHERE job_id=?",
                [utc_now(), job_id],
            )

    def list_jobs(self, *, status: Optional[JobStatus] = None, job_type: Optional[JobType] = None) -> list[Job]:
        clauses, params = [], []
        if status is not None:
            clauses.append("status = ?")
            params.append(status.value)
        if job_type is not None:
            clauses.append("job_type = ?")
            params.append(job_type.value)
        where = f"WHERE {' AND '.join(clauses)}" if clauses else ""
        with self._db.metadata_cursor() as con:
            rows = con.execute(f"SELECT * FROM {JOB_TABLE} {where} ORDER BY created_at_utc DESC", params).fetchall()
        return [_row_to_job(r) for r in rows]

    def stale_running_jobs(self, heartbeat_timeout_seconds: int) -> list[Job]:
        """Return RUNNING jobs whose last heartbeat is older than the given
        timeout — candidates for the future health checker/job runner to
        mark FAILED and hand off to checkpoint-based resume."""
        with self._db.metadata_cursor() as con:
            rows = con.execute(
                f"""
                SELECT * FROM {JOB_TABLE}
                WHERE status = ?
                  AND (last_heartbeat_utc IS NULL OR last_heartbeat_utc < now() - INTERVAL '{int(heartbeat_timeout_seconds)} seconds')
                """,
                [JobStatus.RUNNING.value],
            ).fetchall()
        return [_row_to_job(r) for r in rows]


def _row_to_job(row) -> Job:
    (job_id, job_type, status, params_json, error_message, created_at, updated_at,
     started_at, finished_at, last_heartbeat) = row
    return Job(
        job_id=job_id, job_type=job_type, status=status, params=json.loads(params_json),
        error_message=error_message, created_at_utc=created_at, updated_at_utc=updated_at,
        started_at_utc=started_at, finished_at_utc=finished_at, last_heartbeat_utc=last_heartbeat,
    )

"""
warehouse.metadata.checkpoint_manager
========================================

Resume-after-interruption support. A checkpoint records "how far a unit of
work has gotten" so that a future downloader/job (not implemented in
NGWH-001) can resume from the last completed unit instead of restarting a
100-instrument, 10-year backfill from scratch after a crash or a
Streamlit Community Cloud restart.

This module defines and persists the checkpoint *contract* — it does not
create, advance, or interpret checkpoints on behalf of any specific job
type. That's intentional: the downloader will call `save_checkpoint()` /
`load_checkpoint()` with its own payload shape, and this manager just
guarantees durable, atomic persistence keyed by (scope, job_id, key_path).
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime
from typing import Any, Optional

from warehouse.core.constants import CHECKPOINT_TABLE, CheckpointScope
from warehouse.core.exceptions import CheckpointNotFoundError
from warehouse.core.logging_config import get_logger
from warehouse.core.utils import utc_now
from warehouse.storage.duckdb_manager import DuckDBManager

logger = get_logger(__name__)

_CREATE_TABLE_SQL = f"""
CREATE TABLE IF NOT EXISTS {CHECKPOINT_TABLE} (
    job_id VARCHAR NOT NULL,
    scope VARCHAR NOT NULL,
    key_path VARCHAR NOT NULL,          -- e.g. "NSE_EQ_RELIANCE/1day/2024/03"
    payload_json VARCHAR NOT NULL,       -- arbitrary job-defined progress payload
    is_complete BOOLEAN NOT NULL DEFAULT FALSE,
    created_at_utc TIMESTAMP NOT NULL,
    updated_at_utc TIMESTAMP NOT NULL,
    PRIMARY KEY (job_id, scope, key_path)
);
"""


@dataclass(frozen=True)
class Checkpoint:
    job_id: str
    scope: str
    key_path: str
    payload: dict
    is_complete: bool
    created_at_utc: datetime
    updated_at_utc: datetime


class CheckpointManager:
    """Durable, atomic checkpoint persistence for resumable jobs."""

    def __init__(self, duckdb_manager: DuckDBManager):
        self._db = duckdb_manager
        self._ensure_table()

    def _ensure_table(self) -> None:
        with self._db.metadata_cursor() as con:
            con.execute(_CREATE_TABLE_SQL)

    def save_checkpoint(
        self,
        job_id: str,
        scope: CheckpointScope,
        key_path: str,
        payload: dict[str, Any],
        *,
        is_complete: bool = False,
    ) -> None:
        """Persist (or update) a checkpoint. Idempotent — safe to call
        repeatedly at whatever cadence the caller's autosave_interval dictates."""
        now = utc_now()
        with self._db.metadata_cursor() as con:
            con.execute(
                f"""
                INSERT INTO {CHECKPOINT_TABLE}
                    (job_id, scope, key_path, payload_json, is_complete, created_at_utc, updated_at_utc)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (job_id, scope, key_path) DO UPDATE SET
                    payload_json = excluded.payload_json,
                    is_complete = excluded.is_complete,
                    updated_at_utc = excluded.updated_at_utc
                """,
                [job_id, scope.value, key_path, json.dumps(payload, default=str), is_complete, now, now],
            )
        logger.debug(f"Checkpoint saved: job={job_id} scope={scope.value} key={key_path} complete={is_complete}")

    def load_checkpoint(self, job_id: str, scope: CheckpointScope, key_path: str) -> Checkpoint:
        with self._db.metadata_cursor() as con:
            row = con.execute(
                f"SELECT * FROM {CHECKPOINT_TABLE} WHERE job_id=? AND scope=? AND key_path=?",
                [job_id, scope.value, key_path],
            ).fetchone()
        if row is None:
            raise CheckpointNotFoundError(
                "No checkpoint found", context={"job_id": job_id, "scope": scope.value, "key_path": key_path}
            )
        job_id_, scope_, key_path_, payload_json, is_complete, created_at, updated_at = row
        return Checkpoint(job_id_, scope_, key_path_, json.loads(payload_json), is_complete, created_at, updated_at)

    def has_checkpoint(self, job_id: str, scope: CheckpointScope, key_path: str) -> bool:
        try:
            self.load_checkpoint(job_id, scope, key_path)
            return True
        except CheckpointNotFoundError:
            return False

    def list_incomplete(self, job_id: str) -> list[Checkpoint]:
        """Return all incomplete checkpoints for a job — the resume list a
        downloader would iterate over after a restart."""
        with self._db.metadata_cursor() as con:
            rows = con.execute(
                f"SELECT * FROM {CHECKPOINT_TABLE} WHERE job_id=? AND is_complete=FALSE ORDER BY updated_at_utc",
                [job_id],
            ).fetchall()
        return [Checkpoint(r[0], r[1], r[2], json.loads(r[3]), r[4], r[5], r[6]) for r in rows]

    def mark_complete(self, job_id: str, scope: CheckpointScope, key_path: str) -> None:
        with self._db.metadata_cursor() as con:
            con.execute(
                f"UPDATE {CHECKPOINT_TABLE} SET is_complete=TRUE, updated_at_utc=? "
                "WHERE job_id=? AND scope=? AND key_path=?",
                [utc_now(), job_id, scope.value, key_path],
            )

    def prune_completed(self, older_than: Optional[datetime] = None) -> int:
        """Delete completed checkpoints older than `older_than` (UTC). Returns
        the number of rows deleted. Called by a future maintenance job per
        CheckpointConfig.retain_completed_checkpoints_days."""
        with self._db.metadata_cursor() as con:
            if older_than is None:
                count_row = con.execute(f"SELECT count(*) FROM {CHECKPOINT_TABLE} WHERE is_complete=TRUE").fetchone()
                con.execute(f"DELETE FROM {CHECKPOINT_TABLE} WHERE is_complete=TRUE")
            else:
                count_row = con.execute(
                    f"SELECT count(*) FROM {CHECKPOINT_TABLE} WHERE is_complete=TRUE AND updated_at_utc < ?",
                    [older_than],
                ).fetchone()
                con.execute(
                    f"DELETE FROM {CHECKPOINT_TABLE} WHERE is_complete=TRUE AND updated_at_utc < ?",
                    [older_than],
                )
            return int(count_row[0]) if count_row else 0

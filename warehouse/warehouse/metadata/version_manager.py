"""
warehouse.metadata.version_manager
=====================================

Tracks the warehouse's own schema version history. This is deliberately
tiny and append-only: every time SCHEMA_REGISTRY_VERSION (core/constants.py)
is bumped, a new row is recorded here describing what changed. Future
migration jobs (not implemented in NGWH-001) will read this table to know
which partitions were written under which schema version and what
migration path to apply.

This is distinct from the Research & Learning DB's own versioning
(experiment_id/version_id/run_id, NGSP-003A.2) — that tracks *strategy*
versions; this tracks *storage schema* versions.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Optional

from warehouse.core.constants import SCHEMA_VERSION_TABLE
from warehouse.core.exceptions import VersionConflictError
from warehouse.core.logging_config import get_logger
from warehouse.core.utils import utc_now
from warehouse.storage.duckdb_manager import DuckDBManager

logger = get_logger(__name__)

_CREATE_TABLE_SQL = f"""
CREATE TABLE IF NOT EXISTS {SCHEMA_VERSION_TABLE} (
    schema_version INTEGER PRIMARY KEY,
    description VARCHAR NOT NULL,
    applied_at_utc TIMESTAMP NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT FALSE
);
"""


@dataclass(frozen=True)
class SchemaVersionRecord:
    schema_version: int
    description: str
    applied_at_utc: datetime
    is_current: bool


class VersionManager:
    """Append-only ledger of warehouse schema versions."""

    def __init__(self, duckdb_manager: DuckDBManager):
        self._db = duckdb_manager
        self._ensure_table()

    def _ensure_table(self) -> None:
        with self._db.metadata_cursor() as con:
            con.execute(_CREATE_TABLE_SQL)

    def register_version(self, schema_version: int, description: str, *, make_current: bool = True) -> None:
        """
        Record a new schema version. Raises VersionConflictError if this
        version number was already registered with a *different*
        description (silent no-op if identical — safe to call at every
        bootstrap).
        """
        with self._db.metadata_cursor() as con:
            existing = con.execute(
                f"SELECT description FROM {SCHEMA_VERSION_TABLE} WHERE schema_version = ?",
                [schema_version],
            ).fetchone()

            if existing is not None:
                if existing[0] != description:
                    raise VersionConflictError(
                        f"Schema version {schema_version} already registered with a different description",
                        context={"schema_version": schema_version, "existing": existing[0], "new": description},
                    )
            else:
                con.execute(
                    f"INSERT INTO {SCHEMA_VERSION_TABLE} (schema_version, description, applied_at_utc, is_current) "
                    "VALUES (?, ?, ?, FALSE)",
                    [schema_version, description, utc_now()],
                )
                logger.info(f"Registered new schema version {schema_version}: {description}")

            if make_current:
                con.execute(f"UPDATE {SCHEMA_VERSION_TABLE} SET is_current = FALSE")
                con.execute(
                    f"UPDATE {SCHEMA_VERSION_TABLE} SET is_current = TRUE WHERE schema_version = ?",
                    [schema_version],
                )

    def current_version(self) -> Optional[SchemaVersionRecord]:
        with self._db.metadata_cursor() as con:
            row = con.execute(
                f"SELECT * FROM {SCHEMA_VERSION_TABLE} WHERE is_current = TRUE"
            ).fetchone()
        return SchemaVersionRecord(*row) if row else None

    def history(self) -> list[SchemaVersionRecord]:
        with self._db.metadata_cursor() as con:
            rows = con.execute(
                f"SELECT * FROM {SCHEMA_VERSION_TABLE} ORDER BY schema_version"
            ).fetchall()
        return [SchemaVersionRecord(*r) for r in rows]

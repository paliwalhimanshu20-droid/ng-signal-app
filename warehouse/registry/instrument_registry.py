"""
warehouse.registry.instrument_registry
=========================================

Read-only soft-reference interface into the EXISTING Instrument Master
SQLite database (NGSP-003A.1). This is the only place in the warehouse
foundation that touches that database, and it never writes to it.

"Soft reference" means: the warehouse stores `instrument_id` as a plain
string in Parquet/DuckDB with no foreign key constraint against the
Instrument Master DB. This registry exists so that any warehouse module
(or future downloader) that needs to resolve "give me all active
instruments" or "is this instrument_id real and active" has one sanctioned,
narrow, read-only path to do that — instead of every module opening its own
ad-hoc sqlite3 connection with its own column-name assumptions.

If the Instrument Master schema changes, only this file needs updating.
If the Instrument Master DB is temporarily unavailable (e.g. file missing,
locked), callers get a clear InstrumentMasterUnavailableError rather than a
raw sqlite3 exception, and the warehouse's own bootstrap/health check does
NOT hard-fail because of it (see bootstrap/health_checker.py) — the
warehouse's physical storage is intentionally decoupled from the
availability of the instrument master.
"""

from __future__ import annotations

import sqlite3
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from warehouse.core.constants import (
    INSTRUMENT_MASTER_ACTIVE_FLAG_FIELD,
    INSTRUMENT_MASTER_ACTIVE_VALUE,
    INSTRUMENT_MASTER_ASSET_CLASS_FIELD,
    INSTRUMENT_MASTER_ID_FIELD,
    INSTRUMENT_MASTER_SYMBOL_FIELD,
)
from warehouse.core.exceptions import InstrumentMasterUnavailableError, InstrumentNotFoundError
from warehouse.core.logging_config import get_logger

logger = get_logger(__name__)

# NOTE: the actual Instrument Master table name is an assumption pending
# confirmation against the live NGSP-003A.1 schema. Centralizing it here
# means correcting it later is a one-line change.
_INSTRUMENT_TABLE = "instruments"


@dataclass(frozen=True)
class InstrumentRecord:
    instrument_id: str
    symbol: str
    asset_class: str
    is_active: bool


class InstrumentRegistry:
    """Read-only lookups against the existing Instrument Master SQLite DB."""

    def __init__(self, db_path: Path):
        self._db_path = Path(db_path)

    def _connect(self) -> sqlite3.Connection:
        if not self._db_path.exists():
            raise InstrumentMasterUnavailableError(
                f"Instrument Master DB not found at {self._db_path}",
                context={"path": str(self._db_path)},
            )
        try:
            # Read-only URI connection: this process must never be able to
            # write to a database another module owns, even by accident.
            uri = f"file:{self._db_path}?mode=ro"
            conn = sqlite3.connect(uri, uri=True)
            conn.row_factory = sqlite3.Row
            return conn
        except sqlite3.Error as exc:
            raise InstrumentMasterUnavailableError(
                f"Failed to open Instrument Master DB read-only at {self._db_path}",
                context={"path": str(self._db_path)},
            ) from exc

    def is_available(self) -> bool:
        try:
            with self._connect() as conn:
                conn.execute(f"SELECT 1 FROM {_INSTRUMENT_TABLE} LIMIT 1")
            return True
        except Exception:
            return False

    def get_instrument(self, instrument_id: str) -> InstrumentRecord:
        with self._connect() as conn:
            row = conn.execute(
                f"SELECT {INSTRUMENT_MASTER_ID_FIELD}, {INSTRUMENT_MASTER_SYMBOL_FIELD}, "
                f"{INSTRUMENT_MASTER_ASSET_CLASS_FIELD}, {INSTRUMENT_MASTER_ACTIVE_FLAG_FIELD} "
                f"FROM {_INSTRUMENT_TABLE} WHERE {INSTRUMENT_MASTER_ID_FIELD} = ?",
                (instrument_id,),
            ).fetchone()
        if row is None:
            raise InstrumentNotFoundError(
                f"Instrument not found in Instrument Master: {instrument_id}",
                context={"instrument_id": instrument_id},
            )
        return _row_to_record(row)

    def list_active_instruments(self, asset_class: Optional[str] = None) -> list[InstrumentRecord]:
        clause = f"WHERE {INSTRUMENT_MASTER_ACTIVE_FLAG_FIELD} = ?"
        params: tuple = (INSTRUMENT_MASTER_ACTIVE_VALUE,)
        if asset_class is not None:
            clause += f" AND {INSTRUMENT_MASTER_ASSET_CLASS_FIELD} = ?"
            params = params + (asset_class,)
        with self._connect() as conn:
            rows = conn.execute(
                f"SELECT {INSTRUMENT_MASTER_ID_FIELD}, {INSTRUMENT_MASTER_SYMBOL_FIELD}, "
                f"{INSTRUMENT_MASTER_ASSET_CLASS_FIELD}, {INSTRUMENT_MASTER_ACTIVE_FLAG_FIELD} "
                f"FROM {_INSTRUMENT_TABLE} {clause}",
                params,
            ).fetchall()
        return [_row_to_record(r) for r in rows]

    def exists(self, instrument_id: str) -> bool:
        try:
            self.get_instrument(instrument_id)
            return True
        except InstrumentNotFoundError:
            return False


def _row_to_record(row: sqlite3.Row) -> InstrumentRecord:
    return InstrumentRecord(
        instrument_id=row[INSTRUMENT_MASTER_ID_FIELD],
        symbol=row[INSTRUMENT_MASTER_SYMBOL_FIELD],
        asset_class=row[INSTRUMENT_MASTER_ASSET_CLASS_FIELD],
        is_active=row[INSTRUMENT_MASTER_ACTIVE_FLAG_FIELD] == INSTRUMENT_MASTER_ACTIVE_VALUE,
    )

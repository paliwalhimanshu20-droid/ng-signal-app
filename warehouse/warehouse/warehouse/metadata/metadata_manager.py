"""
warehouse.metadata.metadata_manager
======================================

The warehouse catalog: a queryable record of "what data exists where" —
one row per (layer, instrument_id, timeframe, partition) describing row
counts, date coverage, last-write time, and content hash. This is the
foundation's answer to "do I already have this data, and is it healthy?"
without having to open every Parquet file to find out.

This is NOT the Research & Learning DB (NGSP-003A.2) and does not replace
it — that database tracks experiments/strategies/confidence history. This
catalog tracks physical data coverage only. They are related purely by
soft reference (an experiment can name an instrument_id; neither database
enforces a foreign key against the other).

Backed by the same small `warehouse_metadata.duckdb` file as VersionManager,
CheckpointManager, and JobManager (via DuckDBManager.metadata_connection()) —
one lightweight operational database, four logical tables.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, timezone
from typing import Optional

from warehouse.core.constants import METADATA_CATALOG_TABLE, WarehouseLayer
from warehouse.core.exceptions import CatalogEntryNotFoundError
from warehouse.core.logging_config import get_logger
from warehouse.core.utils import utc_now
from warehouse.storage.duckdb_manager import DuckDBManager
from warehouse.storage.partition_manager import PartitionKey

logger = get_logger(__name__)

_YEARLY_MONTH_SENTINEL = -1  # DuckDB PRIMARY KEY columns must be NOT NULL; yearly-granularity
                              # partitions (no real month) are stored with this sentinel and
                              # translated back to None at the CatalogEntry boundary.

_CREATE_TABLE_SQL = f"""
CREATE TABLE IF NOT EXISTS {METADATA_CATALOG_TABLE} (
    layer VARCHAR NOT NULL,
    instrument_id VARCHAR NOT NULL,
    timeframe VARCHAR NOT NULL,
    year INTEGER NOT NULL,
    month INTEGER NOT NULL,             -- {_YEARLY_MONTH_SENTINEL} sentinel for yearly-granularity partitions
    row_count BIGINT NOT NULL,
    min_timestamp_utc TIMESTAMP,
    max_timestamp_utc TIMESTAMP,
    file_size_bytes BIGINT NOT NULL,
    sha256 VARCHAR NOT NULL,
    schema_version INTEGER NOT NULL,
    last_written_at_utc TIMESTAMP NOT NULL,
    PRIMARY KEY (layer, instrument_id, timeframe, year, month)
);
"""


@dataclass(frozen=True)
class CatalogEntry:
    layer: str
    instrument_id: str
    timeframe: str
    year: int
    month: Optional[int]
    row_count: int
    min_timestamp_utc: Optional[datetime]
    max_timestamp_utc: Optional[datetime]
    file_size_bytes: int
    sha256: str
    schema_version: int
    last_written_at_utc: datetime


class WarehouseMetadataManager:
    """CRUD interface over the warehouse_catalog table."""

    def __init__(self, duckdb_manager: DuckDBManager):
        self._db = duckdb_manager
        self._ensure_table()

    def _ensure_table(self) -> None:
        with self._db.metadata_cursor() as con:
            con.execute(_CREATE_TABLE_SQL)

    def upsert_entry(
        self,
        key: PartitionKey,
        *,
        row_count: int,
        min_timestamp_utc: Optional[datetime],
        max_timestamp_utc: Optional[datetime],
        file_size_bytes: int,
        sha256: str,
        schema_version: int,
    ) -> None:
        """Insert or update the catalog row for a single partition. Called
        after every successful ParquetStorageManager.write_partition()."""
        with self._db.metadata_cursor() as con:
            con.execute(
                f"""
                INSERT INTO {METADATA_CATALOG_TABLE}
                    (layer, instrument_id, timeframe, year, month, row_count,
                     min_timestamp_utc, max_timestamp_utc, file_size_bytes,
                     sha256, schema_version, last_written_at_utc)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (layer, instrument_id, timeframe, year, month)
                DO UPDATE SET
                    row_count = excluded.row_count,
                    min_timestamp_utc = excluded.min_timestamp_utc,
                    max_timestamp_utc = excluded.max_timestamp_utc,
                    file_size_bytes = excluded.file_size_bytes,
                    sha256 = excluded.sha256,
                    schema_version = excluded.schema_version,
                    last_written_at_utc = excluded.last_written_at_utc
                """,
                [
                    key.layer.value, key.instrument_id, key.timeframe.value, key.year,
                    _month_to_db(key.month),
                    row_count, min_timestamp_utc, max_timestamp_utc, file_size_bytes,
                    sha256, schema_version, utc_now(),
                ],
            )
        logger.debug(f"Catalog upserted: {key.layer.value}/{key.instrument_id}/{key.timeframe.value}/{key.year}/{key.month}")

    def get_entry(self, key: PartitionKey) -> CatalogEntry:
        with self._db.metadata_cursor() as con:
            row = con.execute(
                f"SELECT * FROM {METADATA_CATALOG_TABLE} "
                "WHERE layer=? AND instrument_id=? AND timeframe=? AND year=? AND month=?",
                [key.layer.value, key.instrument_id, key.timeframe.value, key.year, _month_to_db(key.month)],
            ).fetchone()
        if row is None:
            raise CatalogEntryNotFoundError(
                "No catalog entry found for partition",
                context={"instrument_id": key.instrument_id, "timeframe": key.timeframe.value, "year": key.year, "month": key.month},
            )
        return _row_to_entry(row)

    def list_entries(
        self,
        *,
        layer: Optional[WarehouseLayer] = None,
        instrument_id: Optional[str] = None,
    ) -> list[CatalogEntry]:
        clauses, params = [], []
        if layer is not None:
            clauses.append("layer = ?")
            params.append(layer.value)
        if instrument_id is not None:
            clauses.append("instrument_id = ?")
            params.append(instrument_id)
        where = f"WHERE {' AND '.join(clauses)}" if clauses else ""
        with self._db.metadata_cursor() as con:
            rows = con.execute(
                f"SELECT * FROM {METADATA_CATALOG_TABLE} {where} ORDER BY instrument_id, timeframe, year, month",
                params,
            ).fetchall()
        return [_row_to_entry(r) for r in rows]

    def coverage_summary(self, instrument_id: str, timeframe: str) -> dict:
        """Return an aggregate coverage summary for one instrument/timeframe —
        total rows, earliest/latest timestamp, number of partitions. Used by
        the health checker and (later) the downloader to decide what's missing."""
        with self._db.metadata_cursor() as con:
            row = con.execute(
                f"""
                SELECT count(*) AS partitions, sum(row_count) AS total_rows,
                       min(min_timestamp_utc) AS earliest, max(max_timestamp_utc) AS latest
                FROM {METADATA_CATALOG_TABLE}
                WHERE instrument_id = ? AND timeframe = ?
                """,
                [instrument_id, timeframe],
            ).fetchone()
        partitions, total_rows, earliest, latest = row
        return {
            "instrument_id": instrument_id,
            "timeframe": timeframe,
            "partitions": partitions or 0,
            "total_rows": int(total_rows) if total_rows else 0,
            "earliest_timestamp_utc": earliest,
            "latest_timestamp_utc": latest,
        }

    def delete_entry(self, key: PartitionKey) -> None:
        with self._db.metadata_cursor() as con:
            con.execute(
                f"DELETE FROM {METADATA_CATALOG_TABLE} "
                "WHERE layer=? AND instrument_id=? AND timeframe=? AND year=? AND month=?",
                [key.layer.value, key.instrument_id, key.timeframe.value, key.year, _month_to_db(key.month)],
            )


def _month_to_db(month: Optional[int]) -> int:
    return _YEARLY_MONTH_SENTINEL if month is None else month


def _month_from_db(month: int) -> Optional[int]:
    return None if month == _YEARLY_MONTH_SENTINEL else month


def _row_to_entry(row) -> CatalogEntry:
    (layer, instrument_id, timeframe, year, month, row_count, min_ts, max_ts,
     file_size_bytes, sha256, schema_version, last_written_at_utc) = row
    return CatalogEntry(
        layer=layer, instrument_id=instrument_id, timeframe=timeframe, year=year,
        month=_month_from_db(month), row_count=row_count, min_timestamp_utc=min_ts,
        max_timestamp_utc=max_ts, file_size_bytes=file_size_bytes, sha256=sha256,
        schema_version=schema_version, last_written_at_utc=last_written_at_utc,
    )

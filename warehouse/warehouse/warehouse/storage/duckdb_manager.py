"""
warehouse.storage.duckdb_manager
===================================

DuckDB is used two ways in this architecture, and this manager supports
both:

1. As a fast, SQL-queryable VIEW over the Parquet lake (no data copied into
   DuckDB — it queries the .parquet files in place via `read_parquet`/
   `parquet_scan` with glob patterns, using Hive partition discovery).
2. As the storage engine for the warehouse's own operational metadata
   (catalog, schema versions, checkpoints, jobs) — a single small
   `warehouse_metadata.duckdb` file, NOT the multi-GB candle data.

These are deliberately two different connections (`analytics_connection()`
vs `metadata_connection()`) so that heavy ad-hoc analytical queries against
the Parquet lake can never block or corrupt the small, latency-sensitive
metadata database that checkpoints/jobs depend on.
"""

from __future__ import annotations

import threading
from contextlib import contextmanager
from pathlib import Path
from typing import Iterator

import duckdb

from warehouse.config.warehouse_config import WarehouseConfig
from warehouse.core.exceptions import DuckDBConnectionError, DuckDBQueryError
from warehouse.core.logging_config import get_logger
from warehouse.core.utils import ensure_directory
from warehouse.storage.partition_manager import PartitionManager

logger = get_logger(__name__)


class DuckDBManager:
    """
    Manages two categories of DuckDB connections for the warehouse:
    an in-memory analytical connection configured to scan the Parquet lake,
    and a persistent connection to the small metadata catalog database.

    Thread-safety: DuckDB connections are not safe to share across threads
    without care. This manager hands out a fresh cursor per `analytics_connection()`
    context and serializes metadata connection access with a lock, which is
    sufficient for the foundation's expected access pattern (occasional
    admin/health queries + metadata read-modify-write, not high-frequency
    concurrent analytical queries).
    """

    def __init__(self, config: WarehouseConfig, partition_manager: PartitionManager | None = None):
        self._config = config
        self._partitions = partition_manager or PartitionManager(config)
        self._metadata_lock = threading.Lock()
        self._metadata_conn: duckdb.DuckDBPyConnection | None = None

    # -- Analytics connection (queries the Parquet lake) ---------------------
    @contextmanager
    def analytics_connection(self) -> Iterator[duckdb.DuckDBPyConnection]:
        """
        Yield a fresh in-memory DuckDB connection pre-configured with the
        warehouse's thread/memory pragmas. Closed automatically on exit.

        Usage:
            with duckdb_manager.analytics_connection() as con:
                con.execute("SELECT * FROM read_parquet(?, hive_partitioning=1)", [glob_pattern])
        """
        try:
            con = duckdb.connect(database=":memory:")
            con.execute(f"PRAGMA memory_limit='{self._config.storage.duckdb_memory_limit}'")
            con.execute(f"PRAGMA threads={self._config.storage.duckdb_threads}")
        except Exception as exc:
            raise DuckDBConnectionError("Failed to open analytics DuckDB connection") from exc

        try:
            yield con
        except duckdb.Error as exc:
            raise DuckDBQueryError(f"DuckDB analytics query failed: {exc}") from exc
        finally:
            con.close()

    def parquet_glob(self, layer, instrument_id: str | None = None, timeframe=None) -> str:
        """
        Build a glob pattern covering the Parquet files for a layer, optionally
        narrowed to one instrument and/or timeframe. Used with
        `read_parquet(glob, hive_partitioning=1)`.
        """
        root = self._partitions.layer_root(layer)
        instrument_part = f"instrument_id={instrument_id}" if instrument_id else "instrument_id=*"
        timeframe_part = f"timeframe={timeframe.value}" if timeframe else "timeframe=*"
        return str(root / instrument_part / timeframe_part / "**" / "*.parquet")

    def query_lake(self, layer, sql_template: str, *, instrument_id: str | None = None, timeframe=None):
        """
        Convenience helper: run a SQL query against the Parquet lake for a
        layer. `sql_template` must contain exactly one `{source}` placeholder,
        e.g.:

            query_lake(WarehouseLayer.RAW_OHLCV,
                       "SELECT instrument_id, count(*) FROM {source} GROUP BY 1",
                       timeframe=Timeframe.DAY_1)

        Returns a pandas DataFrame (via DuckDB's `.df()`).
        """
        glob = self.parquet_glob(layer, instrument_id=instrument_id, timeframe=timeframe)
        source_expr = f"read_parquet('{glob}', hive_partitioning=1, union_by_name=true)"
        sql = sql_template.format(source=source_expr)
        with self.analytics_connection() as con:
            return con.execute(sql).df()

    # -- Metadata connection (small operational DB) --------------------------
    def metadata_connection(self) -> duckdb.DuckDBPyConnection:
        """
        Return the persistent, lazily-opened connection to the warehouse
        metadata catalog DB. Callers should NOT close this connection —
        it's managed by this class and reused across calls. Use the lock via
        `metadata_cursor()` context manager for thread-safe execution.
        """
        if self._metadata_conn is None:
            db_path = self._config.metadata_db_path()
            ensure_directory(db_path.parent)
            try:
                self._metadata_conn = duckdb.connect(database=str(db_path))
            except Exception as exc:
                raise DuckDBConnectionError(
                    f"Failed to open metadata DuckDB connection at {db_path}",
                    context={"path": str(db_path)},
                ) from exc
            logger.info(f"Metadata DuckDB connection opened at {db_path}")
        return self._metadata_conn

    @contextmanager
    def metadata_cursor(self) -> Iterator[duckdb.DuckDBPyConnection]:
        """Thread-safe access to the metadata connection for a single
        transaction-like block of statements."""
        with self._metadata_lock:
            con = self.metadata_connection()
            try:
                yield con
            except duckdb.Error as exc:
                raise DuckDBQueryError(f"DuckDB metadata query failed: {exc}") from exc

    def close(self) -> None:
        if self._metadata_conn is not None:
            self._metadata_conn.close()
            self._metadata_conn = None
            logger.info("Metadata DuckDB connection closed")

    def __enter__(self) -> "DuckDBManager":
        return self

    def __exit__(self, *exc_info) -> None:
        self.close()

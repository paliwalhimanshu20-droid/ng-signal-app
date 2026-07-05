"""
warehouse.storage.parquet_manager
====================================

The only module in the warehouse foundation permitted to write or read a
Parquet file directly. Every write is atomic (write to a temp file in the
same filesystem, then os.replace) so a crash or interruption mid-write
never leaves a corrupt/partial partition file behind — this is what makes
"resume after interruption" (CheckpointManager) safe to reason about.

This module does NOT know about instruments, jobs, or the download
pipeline. It knows about: a schema, a partition path, and bytes. Keeping it
this narrow is what lets it be reused unchanged by every future
layer/module (indicators, market context, DNA) once those are built.
"""

from __future__ import annotations

import os
import tempfile
from dataclasses import dataclass
from pathlib import Path

import pyarrow as pa
import pyarrow.parquet as pq

from warehouse.config.warehouse_config import WarehouseConfig
from warehouse.core.exceptions import ParquetReadError, ParquetWriteError, SchemaMismatchError
from warehouse.core.logging_config import get_logger, log_with_context
from warehouse.core.utils import ensure_directory, file_sha256
from warehouse.storage.schema import OHLCV_SORT_KEYS, validate_schema_compatible
from warehouse.storage.partition_manager import PartitionKey, PartitionManager

logger = get_logger(__name__)


@dataclass(frozen=True)
class WriteResult:
    path: Path
    rows_written: int
    file_size_bytes: int
    sha256: str


class ParquetStorageManager:
    """Atomic, schema-validated Parquet read/write for a single warehouse."""

    def __init__(self, config: WarehouseConfig, partition_manager: PartitionManager | None = None):
        self._config = config
        self._partitions = partition_manager or PartitionManager(config)

    # -- Write ------------------------------------------------------------
    def write_partition(
        self,
        key: PartitionKey,
        table: pa.Table,
        *,
        mode: str = "overwrite",
    ) -> WriteResult:
        """
        Write `table` to the partition identified by `key`.

        Args:
            mode: "overwrite" replaces any existing partition file entirely.
                  "append" reads the existing partition (if any), concatenates,
                  de-duplicates on the OHLCV primary key (keeping the newest
                  `ingested_at_utc`), sorts, and rewrites atomically. Since
                  Parquet files are immutable, "append" is actually a
                  read-modify-write — there is no true in-place append.

        Raises:
            SchemaMismatchError: if `table`'s schema doesn't match the
                canonical schema for `key.layer`.
            ParquetWriteError: on any I/O failure.
        """
        validate_schema_compatible(key.layer, table.schema)

        if mode not in ("overwrite", "append"):
            raise ValueError(f"mode must be 'overwrite' or 'append', got {mode!r}")

        target_path = self._partitions.partition_file(key)
        ensure_directory(target_path.parent)

        if mode == "append" and target_path.exists():
            existing = self.read_partition(key)
            table = _merge_and_dedupe(existing, table)

        table = table.sort_by([(col, "ascending") for col in OHLCV_SORT_KEYS if col in table.column_names])

        result = self._atomic_write(target_path, table)
        log_with_context(
            logger, 20, "Partition written",
            instrument_id=key.instrument_id, timeframe=key.timeframe.value,
            layer=key.layer.value, year=key.year, month=key.month,
            rows=result.rows_written, bytes=result.file_size_bytes, mode=mode,
        )
        return result

    def _atomic_write(self, target_path: Path, table: pa.Table) -> WriteResult:
        tmp_dir = target_path.parent
        try:
            fd, tmp_name = tempfile.mkstemp(
                dir=tmp_dir, prefix=".tmp_", suffix=".parquet"
            )
            os.close(fd)
            tmp_path = Path(tmp_name)
            pq.write_table(
                table,
                tmp_path,
                compression=self._config.storage.parquet_compression,
                row_group_size=self._config.storage.row_group_size,
            )
            os.replace(tmp_path, target_path)  # atomic on same filesystem
        except Exception as exc:
            raise ParquetWriteError(
                f"Failed to atomically write partition file: {target_path}",
                context={"path": str(target_path)},
            ) from exc

        size = target_path.stat().st_size
        digest = file_sha256(target_path)
        return WriteResult(path=target_path, rows_written=table.num_rows, file_size_bytes=size, sha256=digest)

    # -- Read ---------------------------------------------------------------
    def read_partition(self, key: PartitionKey) -> pa.Table:
        path = self._partitions.partition_file(key)
        if not path.exists():
            raise ParquetReadError(
                f"Partition file does not exist: {path}",
                context={"path": str(path)},
            )
        try:
            # Read via ParquetFile rather than pq.read_table(path): the latter
            # routes through pyarrow's Dataset factory, which in some pyarrow
            # versions raises a spurious "string vs dictionary<...>" schema
            # merge error on single-file reads where per-column-chunk encoding
            # differs. ParquetFile.read() reads the file directly with no
            # multi-fragment schema unification involved.
            table = pq.ParquetFile(path).read()
        except Exception as exc:
            raise ParquetReadError(
                f"Failed to read partition file: {path}", context={"path": str(path)}
            ) from exc

        validate_schema_compatible(key.layer, table.schema)
        return table

    def partition_exists(self, key: PartitionKey) -> bool:
        return self._partitions.partition_file(key).exists()

    def partition_row_count(self, key: PartitionKey) -> int:
        path = self._partitions.partition_file(key)
        if not path.exists():
            return 0
        try:
            return pq.ParquetFile(path).metadata.num_rows
        except Exception as exc:
            raise ParquetReadError(
                f"Failed to read metadata for partition file: {path}",
                context={"path": str(path)},
            ) from exc


def _merge_and_dedupe(existing: pa.Table, incoming: pa.Table) -> pa.Table:
    """
    Concatenate existing + incoming rows and de-duplicate on the OHLCV
    primary key, preferring the row with the latest `ingested_at_utc` on
    conflict. Implemented via pandas for straightforward dedup semantics;
    acceptable here because partitions are bounded in size by the
    partition_manager's granularity strategy (monthly for intraday, yearly
    for daily+), never a full-history table.
    """
    import pandas as pd

    if not existing.schema.equals(incoming.schema):
        raise SchemaMismatchError(
            "Cannot merge partitions with mismatched schemas",
            context={"existing_schema": str(existing.schema), "incoming_schema": str(incoming.schema)},
        )

    combined = pa.concat_tables([existing, incoming])
    df = combined.to_pandas()
    key_cols = ["instrument_id", "timeframe", "timestamp_utc"]
    df = df.sort_values("ingested_at_utc").drop_duplicates(subset=key_cols, keep="last")
    return pa.Table.from_pandas(df, schema=combined.schema, preserve_index=False)

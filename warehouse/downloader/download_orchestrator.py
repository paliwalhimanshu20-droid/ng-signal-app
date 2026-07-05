"""
warehouse.downloader.download_orchestrator
==============================================

Orchestrates a complete download for ONE (instrument, timeframe) pair:
plans the request against provider limits, skips whatever the catalog
already covers, fetches only what's missing chunk by chunk, normalizes and
validates each chunk, writes it into the correct warehouse partition(s),
updates the catalog, and checkpoints after every chunk so a crash loses at
most one chunk's worth of work — never the whole instrument.

This is the unit `batch_runner.py` fans out across many instruments. It
deliberately does ONE thing (one instrument, one timeframe) so it stays
simple to reason about and simple to retry in isolation.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date

import pandas as pd
import pyarrow as pa

from warehouse.bootstrap.bootstrap import WarehouseHandles
from warehouse.core.constants import CheckpointScope, Timeframe, WarehouseLayer
from warehouse.core.exceptions import CatalogEntryNotFoundError
from warehouse.core.logging_config import get_logger, log_with_context
from warehouse.downloader.candle_normalizer import normalize_candles
from warehouse.downloader.coverage_planner import find_missing_ranges
from warehouse.downloader.downloader_config import DownloaderConfig
from warehouse.downloader.http_client import UpstoxHistoricalClient
from warehouse.downloader.interval_policy import DownloadPlan, RequestChunk, build_plan
from warehouse.progress.progress_tracker import ProgressTracker
from warehouse.storage.partition_manager import PartitionKey

logger = get_logger(__name__)


@dataclass
class ChunkResult:
    chunk: RequestChunk
    rows_fetched: int
    rows_written: int
    partitions_touched: list[str]
    skipped_reason: str | None = None


@dataclass
class OrchestratorResult:
    instrument_id: str
    timeframe: str
    chunk_results: list[ChunkResult] = field(default_factory=list)

    @property
    def total_rows_written(self) -> int:
        return sum(c.rows_written for c in self.chunk_results)

    @property
    def total_chunks(self) -> int:
        return len(self.chunk_results)

    @property
    def chunks_skipped(self) -> int:
        return sum(1 for c in self.chunk_results if c.skipped_reason is not None)


class DownloadOrchestrator:
    """Runs a single instrument/timeframe download against the warehouse,
    fully integrated with NGWH-001's checkpoint/catalog/storage managers."""

    def __init__(
        self,
        handles: WarehouseHandles,
        downloader_config: DownloaderConfig,
        client: UpstoxHistoricalClient,
        *,
        layer: WarehouseLayer = WarehouseLayer.RAW_OHLCV,
    ):
        self._handles = handles
        self._config = downloader_config
        self._client = client
        self._layer = layer

    def run(
        self,
        job_id: str,
        instrument_id: str,
        timeframe: Timeframe,
        start_date: date,
        end_date: date,
        *,
        force_refresh: bool = False,
        progress: ProgressTracker | None = None,
    ) -> OrchestratorResult:
        result = OrchestratorResult(instrument_id=instrument_id, timeframe=timeframe.value)

        gaps = find_missing_ranges(
            self._handles.metadata_manager, self._layer, instrument_id, timeframe,
            start_date, end_date, force_refresh=force_refresh,
        )
        if not gaps:
            log_with_context(
                logger, 20, "No missing coverage — instrument/timeframe already up to date",
                instrument_id=instrument_id, timeframe=timeframe.value,
                requested_start=str(start_date), requested_end=str(end_date),
            )
            return result

        for gap in gaps:
            plan = build_plan(self._config.interval_policy, timeframe, gap.start, gap.end)
            self._run_plan(job_id, instrument_id, timeframe, plan, result, progress)

        return result

    def _run_plan(
        self,
        job_id: str,
        instrument_id: str,
        timeframe: Timeframe,
        plan: DownloadPlan,
        result: OrchestratorResult,
        progress: ProgressTracker | None,
    ) -> None:
        for chunk in plan.chunks:
            key_path = f"{instrument_id}/{timeframe.value}/{chunk.from_date.isoformat()}_{chunk.to_date.isoformat()}"

            if self._handles.checkpoint_manager.has_checkpoint(job_id, CheckpointScope.INSTRUMENT_TIMEFRAME_PARTITION, key_path):
                existing = self._handles.checkpoint_manager.load_checkpoint(
                    job_id, CheckpointScope.INSTRUMENT_TIMEFRAME_PARTITION, key_path
                )
                if existing.is_complete:
                    result.chunk_results.append(ChunkResult(chunk, 0, 0, [], skipped_reason="already_completed"))
                    if progress:
                        progress.advance(1)
                    continue

            chunk_result = self._process_chunk(instrument_id, timeframe, chunk)
            self._handles.checkpoint_manager.save_checkpoint(
                job_id, CheckpointScope.INSTRUMENT_TIMEFRAME_PARTITION, key_path,
                {"rows_written": chunk_result.rows_written, "partitions": chunk_result.partitions_touched},
                is_complete=True,
            )
            result.chunk_results.append(chunk_result)
            if progress:
                progress.advance(1)

    def _process_chunk(self, instrument_id: str, timeframe: Timeframe, chunk: RequestChunk) -> ChunkResult:
        raw = self._client.fetch_candles(instrument_id, chunk.upstox_interval, chunk.from_date, chunk.to_date)
        table = normalize_candles(instrument_id, timeframe.value, chunk.upstox_interval, raw.candles)

        if table.num_rows == 0:
            log_with_context(
                logger, 20, "Chunk returned zero candles (holiday range, pre-listing, or genuinely empty)",
                instrument_id=instrument_id, timeframe=timeframe.value,
                from_date=str(chunk.from_date), to_date=str(chunk.to_date),
            )
            return ChunkResult(chunk, 0, 0, [], skipped_reason="empty_response")

        partitions_touched = self._write_table(instrument_id, timeframe, table)
        return ChunkResult(chunk, table.num_rows, table.num_rows, partitions_touched)

    def _write_table(self, instrument_id: str, timeframe: Timeframe, table: pa.Table) -> list[str]:
        """Split a normalized table by partition key (year, or year+month for
        intraday timeframes) and write+catalog each partition group."""
        df = table.to_pandas()
        df["_year"] = df["timestamp_utc"].dt.year
        granularity = self._handles.partition_manager.granularity_for(timeframe)

        partitions_touched: list[str] = []
        if granularity.value == "monthly":
            df["_month"] = df["timestamp_utc"].dt.month
            group_cols = ["_year", "_month"]
        else:
            group_cols = ["_year"]

        for group_key, group_df in df.groupby(group_cols):
            year = int(group_key[0]) if isinstance(group_key, tuple) else int(group_key)
            month = int(group_key[1]) if isinstance(group_key, tuple) and len(group_key) > 1 else None

            key = PartitionKey(self._layer, instrument_id, timeframe, year, month)
            group_table = pa.Table.from_pandas(
                group_df.drop(columns=[c for c in ("_year", "_month") if c in group_df.columns]),
                schema=table.schema, preserve_index=False,
            )

            write_result = self._handles.parquet_manager.write_partition(key, group_table, mode="append")
            self._upsert_catalog(key, write_result, group_table)
            partitions_touched.append(str(write_result.path))

        return partitions_touched

    def _upsert_catalog(self, key: PartitionKey, write_result, incoming_table: pa.Table) -> None:
        slice_min = pd.to_datetime(incoming_table.column("timestamp_utc").to_pandas()).min().to_pydatetime()
        slice_max = pd.to_datetime(incoming_table.column("timestamp_utc").to_pandas()).max().to_pydatetime()

        try:
            existing = self._handles.metadata_manager.get_entry(key)
            min_ts = min(existing.min_timestamp_utc, slice_min) if existing.min_timestamp_utc else slice_min
            max_ts = max(existing.max_timestamp_utc, slice_max) if existing.max_timestamp_utc else slice_max
        except CatalogEntryNotFoundError:
            min_ts, max_ts = slice_min, slice_max

        self._handles.metadata_manager.upsert_entry(
            key,
            row_count=write_result.rows_written,
            min_timestamp_utc=min_ts,
            max_timestamp_utc=max_ts,
            file_size_bytes=write_result.file_size_bytes,
            sha256=write_result.sha256,
            schema_version=1,
        )

"""
warehouse.storage.partition_manager
======================================

Owns the on-disk partition layout convention for the entire warehouse.
Every other module (ParquetStorageManager, the future downloader, the
future Market Context/DNA readers) resolves paths through THIS module —
nothing else should ever construct a warehouse path by string-formatting
directly.

Layout convention:

    {root_dir}/{layer_subdir}/instrument_id={instrument_id}/timeframe={timeframe}/
        year=YYYY/part.parquet                      (yearly granularity)
        year=YYYY/month=MM/part.parquet              (monthly granularity)

This is a standard Hive-style partitioning layout, which DuckDB (and any
other Parquet-aware engine) can read directly via partition pruning without
needing a bespoke reader.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from pathlib import Path

from warehouse.config.warehouse_config import WarehouseConfig
from warehouse.core.constants import (
    DEFAULT_PARTITION_GRANULARITY,
    PARQUET_FILE_EXTENSION,
    PartitionGranularity,
    Timeframe,
    WarehouseLayer,
)
from warehouse.core.exceptions import PartitionError
from warehouse.core.utils import safe_path_component, year_month_range, year_range

_LAYER_SUBDIR_ATTR = {
    WarehouseLayer.RAW_OHLCV: "raw_ohlcv_subdir",
    WarehouseLayer.DERIVED_TIMEFRAMES: "derived_timeframes_subdir",
    WarehouseLayer.INDICATORS: "indicators_subdir",
    WarehouseLayer.MARKET_CONTEXT: "market_context_subdir",
    WarehouseLayer.INSTRUMENT_DNA: "instrument_dna_subdir",
    WarehouseLayer.RESEARCH_ARTIFACTS: "research_artifacts_subdir",
}


@dataclass(frozen=True)
class PartitionKey:
    """Fully resolved identity of a single partition file."""

    layer: WarehouseLayer
    instrument_id: str
    timeframe: Timeframe
    year: int
    month: int | None  # None for yearly-granularity partitions

    @property
    def granularity(self) -> PartitionGranularity:
        return PartitionGranularity.MONTHLY if self.month is not None else PartitionGranularity.YEARLY


class PartitionManager:
    """Resolves PartitionKeys to filesystem paths and back, and enumerates
    partitions covering a date range."""

    def __init__(self, config: WarehouseConfig):
        self._config = config
        self._paths = config.resolved_paths()

    # -- Path resolution ------------------------------------------------
    def layer_root(self, layer: WarehouseLayer) -> Path:
        subdir_attr = _LAYER_SUBDIR_ATTR.get(layer)
        if subdir_attr is None:
            raise PartitionError(f"No subdirectory mapping for layer {layer!r}")
        subdir = getattr(self._paths, subdir_attr)
        return self._paths.root_dir / subdir

    def instrument_timeframe_dir(
        self, layer: WarehouseLayer, instrument_id: str, timeframe: Timeframe
    ) -> Path:
        return (
            self.layer_root(layer)
            / f"instrument_id={safe_path_component(instrument_id)}"
            / f"timeframe={timeframe.value}"
        )

    def partition_dir(self, key: PartitionKey) -> Path:
        base = self.instrument_timeframe_dir(key.layer, key.instrument_id, key.timeframe)
        if key.granularity == PartitionGranularity.YEARLY:
            return base / f"year={key.year:04d}"
        return base / f"year={key.year:04d}" / f"month={key.month:02d}"

    def partition_file(self, key: PartitionKey) -> Path:
        return self.partition_dir(key) / f"part{PARQUET_FILE_EXTENSION}"

    def granularity_for(self, timeframe: Timeframe) -> PartitionGranularity:
        return DEFAULT_PARTITION_GRANULARITY.get(timeframe, PartitionGranularity.MONTHLY)

    def make_key(
        self,
        layer: WarehouseLayer,
        instrument_id: str,
        timeframe: Timeframe,
        as_of: date,
    ) -> PartitionKey:
        granularity = self.granularity_for(timeframe)
        month = as_of.month if granularity == PartitionGranularity.MONTHLY else None
        return PartitionKey(
            layer=layer,
            instrument_id=instrument_id,
            timeframe=timeframe,
            year=as_of.year,
            month=month,
        )

    # -- Range enumeration (used by future downloader + by health checker) --
    def enumerate_keys(
        self,
        layer: WarehouseLayer,
        instrument_id: str,
        timeframe: Timeframe,
        start: date,
        end: date,
    ) -> list[PartitionKey]:
        """Enumerate every PartitionKey whose partition overlaps [start, end]."""
        granularity = self.granularity_for(timeframe)
        keys: list[PartitionKey] = []
        if granularity == PartitionGranularity.YEARLY:
            for year in year_range(start, end):
                keys.append(PartitionKey(layer, instrument_id, timeframe, year, None))
        else:
            for year, month in year_month_range(start, end):
                keys.append(PartitionKey(layer, instrument_id, timeframe, year, month))
        return keys

    def discover_existing_partitions(
        self, layer: WarehouseLayer, instrument_id: str, timeframe: Timeframe
    ) -> list[PartitionKey]:
        """Scan the filesystem and return PartitionKeys for every partition
        file that actually exists on disk for this instrument/timeframe."""
        base = self.instrument_timeframe_dir(layer, instrument_id, timeframe)
        if not base.exists():
            return []

        granularity = self.granularity_for(timeframe)
        found: list[PartitionKey] = []
        for year_dir in sorted(base.glob("year=*")):
            try:
                year = int(year_dir.name.split("=", 1)[1])
            except (IndexError, ValueError):
                continue
            if granularity == PartitionGranularity.YEARLY:
                if (year_dir / f"part{PARQUET_FILE_EXTENSION}").exists():
                    found.append(PartitionKey(layer, instrument_id, timeframe, year, None))
            else:
                for month_dir in sorted(year_dir.glob("month=*")):
                    try:
                        month = int(month_dir.name.split("=", 1)[1])
                    except (IndexError, ValueError):
                        continue
                    if (month_dir / f"part{PARQUET_FILE_EXTENSION}").exists():
                        found.append(PartitionKey(layer, instrument_id, timeframe, year, month))
        return found

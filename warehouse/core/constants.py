"""
warehouse.core.constants
=========================

Single source of truth for every fixed vocabulary used across the warehouse
foundation. No module outside this file should redefine a timeframe string,
a layer name, or a status literal — everything imports from here.

Adding a new instrument, timeframe, or layer in the future means editing
THIS file (and, for layers, adding a schema in storage/schema.py) — nothing
else in the foundation hardcodes these lists.
"""

from __future__ import annotations

from enum import Enum


# ---------------------------------------------------------------------------
# Warehouse layers (per approved 6-layer storage architecture, v1.0 §4)
# ---------------------------------------------------------------------------
class WarehouseLayer(str, Enum):
    """
    The six approved storage layers. Layers 1-6 are the v1.0 baseline.
    Addendum v1.1 layers (e.g. Knowledge Graph) are intentionally NOT added
    here yet — they land as their own layer only when their module is built,
    per the addendum's "optional, bolted-on" design.
    """

    RAW_OHLCV = "raw_ohlcv"                # Layer 1 — raw candle data, all timeframes
    DERIVED_TIMEFRAMES = "derived_timeframes"  # Layer 2 — resampled/derived timeframes
    INDICATORS = "indicators"              # Layer 3 — computed technical indicators (future)
    MARKET_CONTEXT = "market_context"      # Layer 4 — regime/context snapshots (future)
    INSTRUMENT_DNA = "instrument_dna"      # Layer 5 — instrument behavioral profiles (future)
    RESEARCH_ARTIFACTS = "research_artifacts"  # Layer 6 — soft-linked research outputs (future)


# Layers this foundation module (NGWH-001) actually creates physical storage
# for today. Later layers exist as reserved namespace only (directories +
# schema stubs may exist, but no writer is implemented until that module
# ships). This distinction is enforced by WarehouseBootstrap.
FOUNDATION_ACTIVE_LAYERS: tuple[WarehouseLayer, ...] = (
    WarehouseLayer.RAW_OHLCV,
    WarehouseLayer.DERIVED_TIMEFRAMES,
)

FOUNDATION_RESERVED_LAYERS: tuple[WarehouseLayer, ...] = (
    WarehouseLayer.INDICATORS,
    WarehouseLayer.MARKET_CONTEXT,
    WarehouseLayer.INSTRUMENT_DNA,
    WarehouseLayer.RESEARCH_ARTIFACTS,
)


# ---------------------------------------------------------------------------
# Timeframes
# ---------------------------------------------------------------------------
class Timeframe(str, Enum):
    """
    Canonical timeframe identifiers. Values match the folder/partition naming
    convention used on disk (see PartitionManager).
    """

    MIN_1 = "1min"
    MIN_5 = "5min"
    MIN_15 = "15min"
    MIN_30 = "30min"
    HOUR_1 = "1hour"
    DAY_1 = "1day"
    WEEK_1 = "1week"

    @property
    def seconds(self) -> int:
        mapping = {
            Timeframe.MIN_1: 60,
            Timeframe.MIN_5: 300,
            Timeframe.MIN_15: 900,
            Timeframe.MIN_30: 1800,
            Timeframe.HOUR_1: 3600,
            Timeframe.DAY_1: 86400,
            Timeframe.WEEK_1: 604800,
        }
        return mapping[self]


# The timeframe the downloader (future module) is expected to fetch directly
# from source. All other timeframes are DERIVED_TIMEFRAMES, produced by
# resampling this base timeframe. Kept here (not hardcoded downstream) so
# changing the base granularity later is a one-line change.
BASE_DOWNLOAD_TIMEFRAME = Timeframe.MIN_30


# ---------------------------------------------------------------------------
# Asset classes (mirrors existing Instrument Master classification, NGSP-003A.1)
# ---------------------------------------------------------------------------
class AssetClass(str, Enum):
    EQUITY = "equity"
    COMMODITY_FUTURES = "commodity_futures"
    INDEX = "index"


# ---------------------------------------------------------------------------
# Job lifecycle status (used by JobManager; job *execution* is a future module,
# this foundation only defines and persists the state machine)
# ---------------------------------------------------------------------------
class JobStatus(str, Enum):
    PENDING = "pending"
    RUNNING = "running"
    PAUSED = "paused"
    COMPLETED = "completed"
    FAILED = "failed"
    CANCELLED = "cancelled"


class JobType(str, Enum):
    """
    Enumerates job types the JobManager can track. Only the type label is
    defined here — the foundation does not implement any job's actual work.
    """

    BACKFILL_DOWNLOAD = "backfill_download"
    INCREMENTAL_UPDATE = "incremental_update"
    TIMEFRAME_DERIVATION = "timeframe_derivation"
    SCHEMA_MIGRATION = "schema_migration"
    HEALTH_CHECK = "health_check"


# ---------------------------------------------------------------------------
# Checkpoint scope — what unit of work a checkpoint represents
# ---------------------------------------------------------------------------
class CheckpointScope(str, Enum):
    INSTRUMENT = "instrument"
    INSTRUMENT_TIMEFRAME = "instrument_timeframe"
    INSTRUMENT_TIMEFRAME_PARTITION = "instrument_timeframe_partition"


# ---------------------------------------------------------------------------
# Health check status
# ---------------------------------------------------------------------------
class HealthStatus(str, Enum):
    HEALTHY = "healthy"
    DEGRADED = "degraded"
    UNHEALTHY = "unhealthy"
    UNKNOWN = "unknown"


# ---------------------------------------------------------------------------
# Partition granularity — how Parquet files are chunked on disk
# ---------------------------------------------------------------------------
class PartitionGranularity(str, Enum):
    YEARLY = "yearly"
    MONTHLY = "monthly"


# Default partitioning strategy per timeframe bucket. Intraday timeframes
# generate far more rows/day than daily+ timeframes, so they partition more
# finely to keep individual Parquet files in a sane size range.
DEFAULT_PARTITION_GRANULARITY: dict[Timeframe, PartitionGranularity] = {
    Timeframe.MIN_1: PartitionGranularity.MONTHLY,
    Timeframe.MIN_5: PartitionGranularity.MONTHLY,
    Timeframe.MIN_15: PartitionGranularity.MONTHLY,
    Timeframe.MIN_30: PartitionGranularity.MONTHLY,
    Timeframe.HOUR_1: PartitionGranularity.YEARLY,
    Timeframe.DAY_1: PartitionGranularity.YEARLY,
    Timeframe.WEEK_1: PartitionGranularity.YEARLY,
}


# ---------------------------------------------------------------------------
# Data lifecycle tiers (Addendum v1.1 §12 — Hot/Warm/Cold/Archive)
# Defined here now (as vocabulary only) so the metadata schema does not need
# a breaking migration when the lifecycle policy module is actually built.
# ---------------------------------------------------------------------------
class DataTier(str, Enum):
    HOT = "hot"
    WARM = "warm"
    COLD = "cold"
    ARCHIVE = "archive"


# ---------------------------------------------------------------------------
# Misc fixed values
# ---------------------------------------------------------------------------
SCHEMA_REGISTRY_VERSION = 1          # bump when schema.py's PyArrow schemas change shape
METADATA_DB_FILENAME = "warehouse_metadata.duckdb"
CHECKPOINT_TABLE = "checkpoints"
JOB_TABLE = "jobs"
METADATA_CATALOG_TABLE = "warehouse_catalog"
SCHEMA_VERSION_TABLE = "schema_versions"

PARQUET_COMPRESSION = "zstd"
PARQUET_FILE_EXTENSION = ".parquet"

# Soft-reference field names expected on the existing Instrument Master
# SQLite table. These are READ-ONLY expectations — the foundation never
# writes to the instrument master DB. Verified against the live
# instrument_master/schema.py in ng-signal-app (2026-07-05) — the real
# primary key column is `instrument_key`, the symbol column is
# `trading_symbol`, and activity is a TEXT enum column `active_status`
# with values "ACTIVE"/"INACTIVE" (not a boolean flag).
INSTRUMENT_MASTER_ID_FIELD = "instrument_key"
INSTRUMENT_MASTER_SYMBOL_FIELD = "trading_symbol"
INSTRUMENT_MASTER_ASSET_CLASS_FIELD = "asset_class"
INSTRUMENT_MASTER_ACTIVE_FLAG_FIELD = "active_status"
INSTRUMENT_MASTER_ACTIVE_VALUE = "ACTIVE"

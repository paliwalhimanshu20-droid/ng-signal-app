"""
NG Signal Pro — Historical Intelligence Warehouse (NGWH-001)
==============================================================

This package is the permanent institutional foundation for the Historical
Intelligence Warehouse, as specified in NGSP-003A.3 (Architecture v1.0 +
Addendum v1.1).

Scope of this package (Foundation only — NGWH-001):
    - Configuration & validation
    - Directory / bootstrap management
    - DuckDB catalog initialization
    - Parquet storage manager
    - Partition manager
    - Schema definitions (OHLCV, multi-timeframe ready)
    - Warehouse metadata manager
    - Version manager
    - Checkpoint manager (resume-after-interruption)
    - Job manager (job lifecycle, not job *execution*)
    - Instrument registry interface (soft-reference into existing
      Instrument Master SQLite — NGSP-003A.1)
    - Warehouse health checker
    - Exception hierarchy, constants, logging, utilities

Explicitly OUT of scope for this package (future modules, not built here):
    - Downloader / Upstox fetching / any API calls
    - Indicator calculation
    - Market Context, Instrument DNA, Market DNA
    - AI Strategy Optimizer, Meta-Learning
    - Replay Engine
    - Backtesting

Nothing in this package makes network calls or reaches into
research_learning.db / instrument master except through the soft-reference
`InstrumentRegistry` interface, which performs read-only lookups.

Public surface is re-exported here so downstream modules (the downloader,
Market Context, DNA engines, etc.) have one stable import path:

    from warehouse import (
        WarehouseConfig, load_config,
        WarehouseBootstrap,
        DuckDBManager, ParquetStorageManager, PartitionManager,
        WarehouseMetadataManager, VersionManager, CheckpointManager, JobManager,
        InstrumentRegistry,
        WarehouseHealthChecker,
    )
"""

from warehouse.version import __version__

from warehouse.config import WarehouseConfig, load_config, validate_configuration
from warehouse.bootstrap import WarehouseBootstrap, WarehouseHandles, WarehouseHealthChecker, HealthReport
from warehouse.storage import (
    DuckDBManager,
    ParquetStorageManager,
    PartitionManager,
    PartitionKey,
    OHLCV_SCHEMA,
)
from warehouse.metadata import (
    WarehouseMetadataManager,
    VersionManager,
    CheckpointManager,
    JobManager,
)
from warehouse.registry import InstrumentRegistry

__all__ = [
    "__version__",
    "WarehouseConfig",
    "load_config",
    "validate_configuration",
    "WarehouseBootstrap",
    "WarehouseHandles",
    "WarehouseHealthChecker",
    "HealthReport",
    "DuckDBManager",
    "ParquetStorageManager",
    "PartitionManager",
    "PartitionKey",
    "OHLCV_SCHEMA",
    "WarehouseMetadataManager",
    "VersionManager",
    "CheckpointManager",
    "JobManager",
    "InstrumentRegistry",
]

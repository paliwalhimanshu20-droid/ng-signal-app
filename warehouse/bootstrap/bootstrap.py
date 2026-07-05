"""
warehouse.bootstrap.bootstrap
================================

The single entry point that takes a WarehouseConfig and turns it into a
fully-initialized, ready-to-use warehouse: directory tree created,
metadata DuckDB initialized with all four operational tables, current
schema version registered, and a pre-flight validation pass run first so
failures are caught before anything is created.

This is idempotent — running bootstrap() again on an already-initialized
warehouse is safe and simply confirms everything is in place (it does not
delete or reset existing data).

Usage:
    from warehouse.config import load_config
    from warehouse.bootstrap import WarehouseBootstrap

    config = load_config()
    bootstrap = WarehouseBootstrap(config)
    handles = bootstrap.run()
    # handles.duckdb_manager, handles.metadata_manager, handles.version_manager,
    # handles.checkpoint_manager, handles.job_manager, handles.partition_manager,
    # handles.parquet_manager are all ready to use.
"""

from __future__ import annotations

from dataclasses import dataclass

from warehouse.config.validation import validate_configuration
from warehouse.config.warehouse_config import WarehouseConfig
from warehouse.core.constants import (
    FOUNDATION_ACTIVE_LAYERS,
    FOUNDATION_RESERVED_LAYERS,
    SCHEMA_REGISTRY_VERSION,
)
from warehouse.core.exceptions import BootstrapError, DirectoryCreationError
from warehouse.core.logging_config import configure_logging, get_logger
from warehouse.core.utils import ensure_directory
from warehouse.metadata import CheckpointManager, JobManager, VersionManager, WarehouseMetadataManager
from warehouse.storage import DuckDBManager, ParquetStorageManager, PartitionManager

logger = get_logger(__name__)


@dataclass
class WarehouseHandles:
    """Bundle of ready-to-use manager instances returned by bootstrap. Every
    future module (downloader, Market Context, DNA, etc.) is expected to
    receive this bundle rather than constructing its own managers."""

    config: WarehouseConfig
    partition_manager: PartitionManager
    parquet_manager: ParquetStorageManager
    duckdb_manager: DuckDBManager
    metadata_manager: WarehouseMetadataManager
    version_manager: VersionManager
    checkpoint_manager: CheckpointManager
    job_manager: JobManager


class WarehouseBootstrap:
    """Idempotent warehouse initialization."""

    def __init__(self, config: WarehouseConfig):
        self._config = config

    def run(self, *, skip_validation: bool = False) -> WarehouseHandles:
        logger.info(f"Bootstrapping warehouse (environment={self._config.environment})")

        resolved = self._config.resolved_paths()

        # 1. Logging must be configured before anything else logs meaningfully.
        configure_logging(
            log_dir=resolved.root_dir / self._config.paths.logs_subdir,
            level=self._config.logging.level,
            fmt=self._config.logging.format,
            max_bytes=self._config.logging.max_bytes,
            backup_count=self._config.logging.backup_count,
            also_console=self._config.logging.also_console,
        )

        # 2. Pre-flight validation (disk space, permissions, sanity checks).
        if not skip_validation:
            report = validate_configuration(self._config)
            for w in report.warnings:
                logger.warning(f"Bootstrap validation warning: {w.message}")
            if not report.is_valid:
                errors = "; ".join(e.message for e in report.errors)
                raise BootstrapError(f"Pre-flight validation failed: {errors}")

        # 3. Create directory tree.
        self._create_directories(resolved)

        # 4. Initialize storage/metadata managers.
        partition_manager = PartitionManager(self._config)
        parquet_manager = ParquetStorageManager(self._config, partition_manager)
        duckdb_manager = DuckDBManager(self._config, partition_manager)

        metadata_manager = WarehouseMetadataManager(duckdb_manager)
        version_manager = VersionManager(duckdb_manager)
        checkpoint_manager = CheckpointManager(duckdb_manager)
        job_manager = JobManager(duckdb_manager)

        # 5. Register the current schema version (idempotent).
        version_manager.register_version(
            SCHEMA_REGISTRY_VERSION,
            "NGWH-001 foundation: base OHLCV schema (raw + derived timeframes)",
        )

        logger.info(
            f"Warehouse bootstrap complete. root_dir={resolved.root_dir}, "
            f"active_layers={[l.value for l in FOUNDATION_ACTIVE_LAYERS]}, "
            f"reserved_layers={[l.value for l in FOUNDATION_RESERVED_LAYERS]}"
        )

        return WarehouseHandles(
            config=self._config,
            partition_manager=partition_manager,
            parquet_manager=parquet_manager,
            duckdb_manager=duckdb_manager,
            metadata_manager=metadata_manager,
            version_manager=version_manager,
            checkpoint_manager=checkpoint_manager,
            job_manager=job_manager,
        )

    def _create_directories(self, resolved) -> None:
        directories = [
            resolved.root_dir,
            resolved.root_dir / self._config.paths.raw_ohlcv_subdir,
            resolved.root_dir / self._config.paths.derived_timeframes_subdir,
            resolved.root_dir / self._config.paths.metadata_subdir,
            resolved.root_dir / self._config.paths.checkpoints_subdir,
            resolved.root_dir / self._config.paths.logs_subdir,
            resolved.root_dir / self._config.paths.tmp_subdir,
        ]
        # Reserved layers get their directory created too (empty, namespace
        # reserved) so future modules never need to worry about first-run
        # directory creation for a layer they didn't build the bootstrap for.
        for subdir in (
            self._config.paths.indicators_subdir,
            self._config.paths.market_context_subdir,
            self._config.paths.instrument_dna_subdir,
            self._config.paths.research_artifacts_subdir,
        ):
            directories.append(resolved.root_dir / subdir)

        for d in directories:
            try:
                ensure_directory(d)
            except OSError as exc:
                raise DirectoryCreationError(
                    f"Failed to create warehouse directory: {d}", context={"path": str(d)}
                ) from exc
        logger.debug(f"Created/verified {len(directories)} warehouse directories")

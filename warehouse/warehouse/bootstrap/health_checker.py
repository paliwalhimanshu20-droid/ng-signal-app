"""
warehouse.bootstrap.health_checker
=====================================

Post-bootstrap health checks for the warehouse foundation. This is the
NGWH-001 counterpart to the existing Validation Center (NGSP-003B.1) —
same design language (health score, per-category status, SKIPPED for
unavailable checks) applied to the warehouse's own infrastructure rather
than signal/backtest correctness. This module is intentionally built to be
easy to wire into that same Validation Center dashboard later as another
category, rather than becoming a second, inconsistent health-reporting
system.

Categories checked:
    - directories: every expected subdirectory exists and is writable
    - metadata_db: the metadata DuckDB file opens and has all 4 tables
    - schema_version: a current schema version is registered
    - instrument_master: soft-reference DB reachable (SKIPPED if absent —
      the warehouse's own health does not depend on another module's DB)
    - disk_space: free space above the configured minimum
    - catalog_consistency: spot-checks that a sample of catalog entries'
      referenced Parquet files actually exist on disk
"""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path

from warehouse.config.validation import MIN_FREE_DISK_BYTES
from warehouse.config.warehouse_config import WarehouseConfig
from warehouse.core.constants import CHECKPOINT_TABLE, HealthStatus, JOB_TABLE, METADATA_CATALOG_TABLE, SCHEMA_VERSION_TABLE
from warehouse.core.logging_config import get_logger
from warehouse.metadata.metadata_manager import WarehouseMetadataManager
from warehouse.metadata.version_manager import VersionManager
from warehouse.registry.instrument_registry import InstrumentRegistry
from warehouse.storage.duckdb_manager import DuckDBManager
from warehouse.storage.partition_manager import PartitionManager

logger = get_logger(__name__)

_EXPECTED_METADATA_TABLES = (METADATA_CATALOG_TABLE, SCHEMA_VERSION_TABLE, CHECKPOINT_TABLE, JOB_TABLE)


@dataclass
class CategoryResult:
    name: str
    status: HealthStatus
    detail: str
    context: dict = field(default_factory=dict)


@dataclass
class HealthReport:
    categories: list[CategoryResult] = field(default_factory=list)

    @property
    def health_score(self) -> float:
        """Percentage healthy among categories that weren't SKIPPED. Mirrors
        the Validation Center's convention of excluding skipped categories
        from the score so an unavailable optional dependency doesn't
        artificially tank the score."""
        scored = [c for c in self.categories if c.status != HealthStatus.UNKNOWN]
        if not scored:
            return 100.0
        healthy = sum(1 for c in scored if c.status == HealthStatus.HEALTHY)
        return round(100.0 * healthy / len(scored), 1)

    @property
    def overall_status(self) -> HealthStatus:
        statuses = {c.status for c in self.categories}
        if HealthStatus.UNHEALTHY in statuses:
            return HealthStatus.UNHEALTHY
        if HealthStatus.DEGRADED in statuses:
            return HealthStatus.DEGRADED
        if statuses <= {HealthStatus.HEALTHY, HealthStatus.UNKNOWN}:
            return HealthStatus.HEALTHY
        return HealthStatus.UNKNOWN

    def as_text_report(self) -> str:
        lines = [f"Warehouse Health Report — overall: {self.overall_status.value.upper()} ({self.health_score}%)"]
        for c in self.categories:
            lines.append(f"  [{c.status.value.upper():9s}] {c.name}: {c.detail}")
        return "\n".join(lines)


class WarehouseHealthChecker:
    """Runs the full suite of foundation health checks."""

    def __init__(
        self,
        config: WarehouseConfig,
        duckdb_manager: DuckDBManager,
        partition_manager: PartitionManager | None = None,
    ):
        self._config = config
        self._db = duckdb_manager
        self._partitions = partition_manager or PartitionManager(config)

    def run(self, *, catalog_sample_size: int = 25) -> HealthReport:
        report = HealthReport()
        report.categories.append(self._check_directories())
        report.categories.append(self._check_metadata_db())
        report.categories.append(self._check_schema_version())
        report.categories.append(self._check_instrument_master())
        report.categories.append(self._check_disk_space())
        report.categories.append(self._check_catalog_consistency(catalog_sample_size))
        return report

    def _check_directories(self) -> CategoryResult:
        resolved = self._config.resolved_paths()
        required = [
            resolved.root_dir,
            resolved.root_dir / self._config.paths.raw_ohlcv_subdir,
            resolved.root_dir / self._config.paths.derived_timeframes_subdir,
            resolved.root_dir / self._config.paths.metadata_subdir,
            resolved.root_dir / self._config.paths.checkpoints_subdir,
        ]
        missing = [str(p) for p in required if not p.exists()]
        if missing:
            return CategoryResult(
                "directories", HealthStatus.UNHEALTHY,
                f"{len(missing)} expected directories are missing — run WarehouseBootstrap.run()",
                context={"missing": missing},
            )
        return CategoryResult("directories", HealthStatus.HEALTHY, f"All {len(required)} core directories present")

    def _check_metadata_db(self) -> CategoryResult:
        try:
            with self._db.metadata_cursor() as con:
                tables = {r[0] for r in con.execute("SHOW TABLES").fetchall()}
        except Exception as exc:
            return CategoryResult("metadata_db", HealthStatus.UNHEALTHY, f"Could not query metadata DB: {exc}")

        missing = [t for t in _EXPECTED_METADATA_TABLES if t not in tables]
        if missing:
            return CategoryResult(
                "metadata_db", HealthStatus.DEGRADED,
                f"Metadata DB reachable but missing tables: {missing}",
                context={"missing_tables": missing},
            )
        return CategoryResult("metadata_db", HealthStatus.HEALTHY, f"All {len(_EXPECTED_METADATA_TABLES)} operational tables present")

    def _check_schema_version(self) -> CategoryResult:
        try:
            version_manager = VersionManager(self._db)
            current = version_manager.current_version()
        except Exception as exc:
            return CategoryResult("schema_version", HealthStatus.UNHEALTHY, f"Failed to read schema version: {exc}")

        if current is None:
            return CategoryResult("schema_version", HealthStatus.UNHEALTHY, "No current schema version registered")
        return CategoryResult(
            "schema_version", HealthStatus.HEALTHY,
            f"Schema version {current.schema_version} current ({current.description})",
        )

    def _check_instrument_master(self) -> CategoryResult:
        db_path = self._config.resolved_paths().instrument_master_db_path
        if not db_path.exists():
            return CategoryResult(
                "instrument_master", HealthStatus.UNKNOWN,
                f"Instrument Master DB not found at {db_path} — check skipped (not a warehouse-owned dependency)",
            )
        registry = InstrumentRegistry(db_path)
        if registry.is_available():
            return CategoryResult("instrument_master", HealthStatus.HEALTHY, "Instrument Master DB reachable")
        return CategoryResult("instrument_master", HealthStatus.DEGRADED, "Instrument Master DB present but not queryable")

    def _check_disk_space(self) -> CategoryResult:
        import shutil

        root = self._config.resolved_paths().root_dir
        probe = root
        while not probe.exists() and probe.parent != probe:
            probe = probe.parent
        try:
            usage = shutil.disk_usage(probe)
        except OSError as exc:
            return CategoryResult("disk_space", HealthStatus.UNKNOWN, f"Could not determine disk usage: {exc}")

        if usage.free < MIN_FREE_DISK_BYTES:
            return CategoryResult(
                "disk_space", HealthStatus.UNHEALTHY,
                f"Only {usage.free / (1024**3):.2f} GB free (minimum {MIN_FREE_DISK_BYTES / (1024**3):.1f} GB)",
            )
        if usage.free < MIN_FREE_DISK_BYTES * 5:
            return CategoryResult(
                "disk_space", HealthStatus.DEGRADED,
                f"{usage.free / (1024**3):.2f} GB free — getting low relative to target scale",
            )
        return CategoryResult("disk_space", HealthStatus.HEALTHY, f"{usage.free / (1024**3):.2f} GB free")

    def _check_catalog_consistency(self, sample_size: int) -> CategoryResult:
        try:
            metadata_manager = WarehouseMetadataManager(self._db)
            entries = metadata_manager.list_entries()
        except Exception as exc:
            return CategoryResult("catalog_consistency", HealthStatus.UNHEALTHY, f"Could not read catalog: {exc}")

        if not entries:
            return CategoryResult("catalog_consistency", HealthStatus.UNKNOWN, "Catalog is empty — nothing to check yet")

        sample = entries[:sample_size]
        missing_files: list[str] = []
        from warehouse.core.constants import WarehouseLayer, Timeframe
        from warehouse.storage.partition_manager import PartitionKey

        for entry in sample:
            key = PartitionKey(
                layer=WarehouseLayer(entry.layer),
                instrument_id=entry.instrument_id,
                timeframe=Timeframe(entry.timeframe),
                year=entry.year,
                month=entry.month,
            )
            path = self._partitions.partition_file(key)
            if not path.exists():
                missing_files.append(str(path))

        if missing_files:
            return CategoryResult(
                "catalog_consistency", HealthStatus.UNHEALTHY,
                f"{len(missing_files)}/{len(sample)} sampled catalog entries reference missing Parquet files",
                context={"missing_files": missing_files[:10]},
            )
        return CategoryResult(
            "catalog_consistency", HealthStatus.HEALTHY,
            f"Sampled {len(sample)}/{len(entries)} catalog entries — all referenced files present",
        )

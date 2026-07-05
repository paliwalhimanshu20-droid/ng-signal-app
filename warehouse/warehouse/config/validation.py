"""
warehouse.config.validation
==============================

Pre-flight validation that goes beyond what Pydantic's field validators can
check in isolation — things that require touching the filesystem or
reasoning about the machine the warehouse is about to run on.

This is deliberately separate from `warehouse_config.py`'s Pydantic
validators: Pydantic validation answers "is this configuration internally
well-formed?"; this module answers "is this MACHINE ready to run this
configuration?" (disk space, write permissions, existing DB reachability).

Called by WarehouseBootstrap before it creates anything, and exposed
standalone so a health-check job (future) or a CLI ("ngwh doctor") can run
it independently.
"""

from __future__ import annotations

import shutil
from dataclasses import dataclass, field
from pathlib import Path

from warehouse.config.warehouse_config import WarehouseConfig
from warehouse.core.exceptions import ConfigValidationError
from warehouse.core.logging_config import get_logger

logger = get_logger(__name__)

# Minimum free disk space we insist on before bootstrap proceeds. This is a
# conservative floor, not a scale estimate — see ScaleConfig / docs for real
# sizing guidance at 100 instruments x 10 years.
MIN_FREE_DISK_BYTES = 1 * 1024 * 1024 * 1024  # 1 GB


@dataclass
class ValidationIssue:
    severity: str  # "error" | "warning"
    message: str
    context: dict = field(default_factory=dict)


@dataclass
class ValidationReport:
    issues: list[ValidationIssue] = field(default_factory=list)

    @property
    def errors(self) -> list[ValidationIssue]:
        return [i for i in self.issues if i.severity == "error"]

    @property
    def warnings(self) -> list[ValidationIssue]:
        return [i for i in self.issues if i.severity == "warning"]

    @property
    def is_valid(self) -> bool:
        return len(self.errors) == 0

    def add_error(self, message: str, **context) -> None:
        self.issues.append(ValidationIssue("error", message, context))

    def add_warning(self, message: str, **context) -> None:
        self.issues.append(ValidationIssue("warning", message, context))

    def raise_if_invalid(self) -> None:
        if not self.is_valid:
            details = "; ".join(i.message for i in self.errors)
            raise ConfigValidationError(
                f"Configuration/storage validation failed: {details}",
                context={"error_count": len(self.errors)},
            )


def validate_configuration(config: WarehouseConfig) -> ValidationReport:
    """
    Run all pre-flight checks against a validated WarehouseConfig.
    Does NOT create any directories or files — purely read-only inspection.
    """
    report = ValidationReport()
    resolved = config.resolved_paths()

    _check_root_dir_writable(resolved.root_dir, report)
    _check_disk_space(resolved.root_dir, report)
    _check_instrument_master_reachable(resolved.instrument_master_db_path, report)
    _check_scale_sanity(config, report)

    for issue in report.issues:
        level = "warning" if issue.severity == "warning" else "error"
        getattr(logger, level)(f"Validation {issue.severity}: {issue.message}", extra={"context": issue.context})

    return report


def _check_root_dir_writable(root_dir: Path, report: ValidationReport) -> None:
    # Walk up to the nearest existing ancestor and check writability there,
    # since root_dir itself likely doesn't exist yet on first run.
    probe = root_dir
    while not probe.exists():
        if probe.parent == probe:
            report.add_error("Could not find any existing ancestor directory to check permissions on", root_dir=str(root_dir))
            return
        probe = probe.parent

    if not probe.is_dir():
        report.add_error(f"Path exists but is not a directory: {probe}", path=str(probe))
        return

    test_file = probe / ".ngwh_write_test"
    try:
        test_file.touch()
        test_file.unlink()
    except OSError as exc:
        report.add_error(f"Root directory ancestor is not writable: {probe} ({exc})", path=str(probe))


def _check_disk_space(root_dir: Path, report: ValidationReport) -> None:
    probe = root_dir
    while not probe.exists() and probe.parent != probe:
        probe = probe.parent
    try:
        usage = shutil.disk_usage(probe)
    except OSError as exc:
        report.add_warning(f"Could not determine disk usage for {probe}: {exc}")
        return

    if usage.free < MIN_FREE_DISK_BYTES:
        report.add_error(
            f"Insufficient free disk space at {probe}: "
            f"{usage.free / (1024**3):.2f} GB free, minimum required is "
            f"{MIN_FREE_DISK_BYTES / (1024**3):.2f} GB",
            free_bytes=usage.free,
        )


def _check_instrument_master_reachable(db_path: Path, report: ValidationReport) -> None:
    if not db_path.exists():
        report.add_warning(
            f"Instrument Master DB not found at {db_path} — instrument registry lookups "
            "will fail until this exists. This is only a warning because the warehouse "
            "foundation itself does not require it to bootstrap.",
            path=str(db_path),
        )


def _check_scale_sanity(config: WarehouseConfig, report: ValidationReport) -> None:
    if config.scale.target_instrument_count > 500:
        report.add_warning(
            f"target_instrument_count={config.scale.target_instrument_count} is unusually high; "
            "confirm DuckDB memory_limit/threads are sized accordingly.",
        )
    if config.storage.duckdb_threads > 32:
        report.add_warning(
            f"duckdb_threads={config.storage.duckdb_threads} is unusually high for typical deployment targets."
        )

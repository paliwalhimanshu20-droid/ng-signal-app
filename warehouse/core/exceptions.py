"""
warehouse.core.exceptions
===========================

Structured exception hierarchy for the entire warehouse foundation.

Design rules followed throughout this package:
    1. Every raised exception is a subclass of WarehouseError — callers can
       always catch `WarehouseError` to mean "something in the warehouse
       layer went wrong" without needing to know the specific subsystem.
    2. Each subsystem (config, storage, metadata, registry, bootstrap) gets
       its own intermediate exception class so callers CAN narrow if they
       want to.
    3. Every exception carries a machine-readable `.code` and an optional
       `.context` dict, so structured logging (core/logging_config.py) can
       emit consistent, greppable log lines instead of free-text messages.
    4. No exception swallows the original cause — always re-raise with
       `raise NewError(...) from original_exception` at call sites.
"""

from __future__ import annotations

from typing import Any, Optional


class WarehouseError(Exception):
    """Base class for every exception raised by the warehouse package."""

    code: str = "WAREHOUSE_ERROR"

    def __init__(
        self,
        message: str,
        *,
        context: Optional[dict[str, Any]] = None,
        code: Optional[str] = None,
    ) -> None:
        self.context = context or {}
        if code:
            self.code = code
        super().__init__(message)

    def __str__(self) -> str:
        base = super().__str__()
        if self.context:
            ctx = ", ".join(f"{k}={v!r}" for k, v in self.context.items())
            return f"[{self.code}] {base} ({ctx})"
        return f"[{self.code}] {base}"


# ---------------------------------------------------------------------------
# Configuration errors
# ---------------------------------------------------------------------------
class ConfigurationError(WarehouseError):
    code = "CONFIG_ERROR"


class ConfigValidationError(ConfigurationError):
    code = "CONFIG_VALIDATION_ERROR"


class ConfigFileNotFoundError(ConfigurationError):
    code = "CONFIG_FILE_NOT_FOUND"


# ---------------------------------------------------------------------------
# Storage errors (Parquet + DuckDB)
# ---------------------------------------------------------------------------
class StorageError(WarehouseError):
    code = "STORAGE_ERROR"


class ParquetWriteError(StorageError):
    code = "PARQUET_WRITE_ERROR"


class ParquetReadError(StorageError):
    code = "PARQUET_READ_ERROR"


class SchemaMismatchError(StorageError):
    code = "SCHEMA_MISMATCH"


class DuckDBConnectionError(StorageError):
    code = "DUCKDB_CONNECTION_ERROR"


class DuckDBQueryError(StorageError):
    code = "DUCKDB_QUERY_ERROR"


class PartitionError(StorageError):
    code = "PARTITION_ERROR"


class PartitionNotFoundError(PartitionError):
    code = "PARTITION_NOT_FOUND"


# ---------------------------------------------------------------------------
# Metadata errors (catalog, version, checkpoint, job)
# ---------------------------------------------------------------------------
class MetadataError(WarehouseError):
    code = "METADATA_ERROR"


class CatalogEntryNotFoundError(MetadataError):
    code = "CATALOG_ENTRY_NOT_FOUND"


class VersionConflictError(MetadataError):
    code = "VERSION_CONFLICT"


class CheckpointError(MetadataError):
    code = "CHECKPOINT_ERROR"


class CheckpointNotFoundError(CheckpointError):
    code = "CHECKPOINT_NOT_FOUND"


class JobError(MetadataError):
    code = "JOB_ERROR"


class JobNotFoundError(JobError):
    code = "JOB_NOT_FOUND"


class InvalidJobStateTransitionError(JobError):
    code = "INVALID_JOB_STATE_TRANSITION"


# ---------------------------------------------------------------------------
# Registry errors (soft-reference into Instrument Master)
# ---------------------------------------------------------------------------
class RegistryError(WarehouseError):
    code = "REGISTRY_ERROR"


class InstrumentNotFoundError(RegistryError):
    code = "INSTRUMENT_NOT_FOUND"


class InstrumentMasterUnavailableError(RegistryError):
    code = "INSTRUMENT_MASTER_UNAVAILABLE"


# ---------------------------------------------------------------------------
# Bootstrap / health errors
# ---------------------------------------------------------------------------
class BootstrapError(WarehouseError):
    code = "BOOTSTRAP_ERROR"


class DirectoryCreationError(BootstrapError):
    code = "DIRECTORY_CREATION_ERROR"


class HealthCheckError(WarehouseError):
    code = "HEALTH_CHECK_ERROR"

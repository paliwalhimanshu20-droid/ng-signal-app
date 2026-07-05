"""
warehouse.config.warehouse_config
====================================

The single, validated configuration object every warehouse module reads
from. No module below this one should hardcode a path, a partition
strategy, a compression codec, or a scale limit — it comes from here.

Configuration is loaded from (in order of precedence, highest wins):
    1. Explicit keyword overrides passed to `load_config(**overrides)`
    2. Environment variables (prefix `NGWH_`)
    3. A YAML file (default: `config/warehouse.yaml`, override via
       `NGWH_CONFIG_FILE` env var or `config_file=` argument)
    4. Built-in defaults defined on the Pydantic models below

This mirrors the existing NGSP pattern (config.py / risk_config.py /
market_config.py) of "everything tunable lives in one importable object",
extended with Pydantic validation since this module governs on-disk data
integrity at multi-year, multi-instrument scale — mistakes here are far
more expensive to unwind than in the earlier config modules.
"""

from __future__ import annotations

import os
from pathlib import Path
from typing import Optional

import yaml
from pydantic import BaseModel, Field, field_validator, model_validator

from warehouse.core.constants import (
    PARQUET_COMPRESSION,
    PartitionGranularity,
)
from warehouse.core.exceptions import ConfigFileNotFoundError, ConfigValidationError

ENV_PREFIX = "NGWH_"
DEFAULT_CONFIG_FILENAME = "warehouse.yaml"


# ---------------------------------------------------------------------------
# Sub-sections
# ---------------------------------------------------------------------------
class PathsConfig(BaseModel):
    """
    All warehouse paths are derived from a single `root_dir`, so relocating
    the entire warehouse (e.g. moving from local disk to a mounted volume)
    is a one-value change.
    """

    root_dir: Path = Field(
        default=Path("./data/warehouse"),
        description="Root directory for all warehouse-owned data (Parquet + DuckDB catalog).",
    )
    raw_ohlcv_subdir: str = Field(default="raw_ohlcv")
    derived_timeframes_subdir: str = Field(default="derived_timeframes")
    indicators_subdir: str = Field(default="indicators")          # reserved, unused by NGWH-001
    market_context_subdir: str = Field(default="market_context")  # reserved, unused by NGWH-001
    instrument_dna_subdir: str = Field(default="instrument_dna")  # reserved, unused by NGWH-001
    research_artifacts_subdir: str = Field(default="research_artifacts")  # reserved, unused by NGWH-001
    metadata_subdir: str = Field(default="_metadata")
    checkpoints_subdir: str = Field(default="_checkpoints")
    logs_subdir: str = Field(default="_logs")
    tmp_subdir: str = Field(default="_tmp")

    # Existing SQLite databases (owned by OTHER modules — NGSP-003A.1 and
    # NGSP-003A.2). The warehouse only ever performs read-only soft-reference
    # lookups against these; it never writes to them and never enforces a
    # foreign key against them.
    instrument_master_db_path: Path = Field(
        default=Path("./data/instrument_master.db"),
        description="Path to the EXISTING Instrument Master SQLite DB (NGSP-003A.1). Read-only.",
    )
    research_learning_db_path: Path = Field(
        default=Path("./data/research_learning.db"),
        description="Path to the EXISTING Research & Learning SQLite DB (NGSP-003A.2). Not written to by NGWH-001.",
    )

    def resolve(self, relative_to: Optional[Path] = None) -> "PathsConfig":
        """Return a copy with all paths resolved to absolute paths."""
        base = relative_to or Path.cwd()

        def _abs(p: Path) -> Path:
            p = Path(p)
            return p if p.is_absolute() else (base / p).resolve()

        data = self.model_dump()
        data["root_dir"] = _abs(self.root_dir)
        data["instrument_master_db_path"] = _abs(self.instrument_master_db_path)
        data["research_learning_db_path"] = _abs(self.research_learning_db_path)
        return PathsConfig(**data)


class StorageConfig(BaseModel):
    """Parquet + DuckDB physical storage tuning."""

    parquet_compression: str = Field(default=PARQUET_COMPRESSION)
    row_group_size: int = Field(
        default=122_880,
        description="Target rows per Parquet row group. Tuned for DuckDB scan efficiency at candle scale.",
        gt=0,
    )
    duckdb_memory_limit: str = Field(
        default="2GB",
        description="DuckDB memory_limit PRAGMA value, e.g. '2GB'.",
    )
    duckdb_threads: int = Field(default=4, gt=0)
    default_partition_granularity_intraday: PartitionGranularity = Field(
        default=PartitionGranularity.MONTHLY
    )
    default_partition_granularity_daily_plus: PartitionGranularity = Field(
        default=PartitionGranularity.YEARLY
    )

    @field_validator("parquet_compression")
    @classmethod
    def _validate_codec(cls, v: str) -> str:
        allowed = {"zstd", "snappy", "gzip", "brotli", "lz4", "none"}
        if v.lower() not in allowed:
            raise ValueError(f"parquet_compression must be one of {sorted(allowed)}, got {v!r}")
        return v.lower()


class ScaleConfig(BaseModel):
    """
    Declares the scale envelope this deployment is designed for. These are
    not hard limits enforced at write time (that would defeat "future
    expansion without redesign") — they are used by the health checker and
    capacity warnings to flag when actual usage is approaching what the
    current hardware/tuning was sized for.
    """

    target_instrument_count: int = Field(default=100, gt=0)
    target_years_history: int = Field(default=10, gt=0)
    max_parallel_jobs: int = Field(
        default=8,
        gt=0,
        description="Ceiling on concurrent warehouse write jobs (enforced by JobManager), to bound DuckDB/disk contention.",
    )


class LoggingConfig(BaseModel):
    level: str = Field(default="INFO")
    format: str = Field(default="text", description="'text' or 'json'")
    max_bytes: int = Field(default=10 * 1024 * 1024, gt=0)
    backup_count: int = Field(default=5, ge=0)
    also_console: bool = Field(default=True)

    @field_validator("level")
    @classmethod
    def _validate_level(cls, v: str) -> str:
        allowed = {"DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"}
        if v.upper() not in allowed:
            raise ValueError(f"logging.level must be one of {sorted(allowed)}, got {v!r}")
        return v.upper()

    @field_validator("format")
    @classmethod
    def _validate_format(cls, v: str) -> str:
        allowed = {"text", "json"}
        if v.lower() not in allowed:
            raise ValueError(f"logging.format must be one of {sorted(allowed)}, got {v!r}")
        return v.lower()


class CheckpointConfig(BaseModel):
    """Resume-after-interruption behavior. No downloader exists yet in this
    module, but the checkpoint contract downloaders will rely on is fixed here."""

    enabled: bool = Field(default=True)
    autosave_interval_seconds: int = Field(default=30, gt=0)
    retain_completed_checkpoints_days: int = Field(
        default=30,
        ge=0,
        description="How long to keep COMPLETED checkpoints before they're eligible for pruning. 0 = keep forever.",
    )


class WarehouseConfig(BaseModel):
    """Top-level configuration object. Import and use this everywhere."""

    environment: str = Field(default="development", description="'development' | 'staging' | 'production'")
    paths: PathsConfig = Field(default_factory=PathsConfig)
    storage: StorageConfig = Field(default_factory=StorageConfig)
    scale: ScaleConfig = Field(default_factory=ScaleConfig)
    logging: LoggingConfig = Field(default_factory=LoggingConfig)
    checkpoint: CheckpointConfig = Field(default_factory=CheckpointConfig)

    model_config = {"validate_assignment": True}

    @field_validator("environment")
    @classmethod
    def _validate_env(cls, v: str) -> str:
        allowed = {"development", "staging", "production"}
        if v.lower() not in allowed:
            raise ValueError(f"environment must be one of {sorted(allowed)}, got {v!r}")
        return v.lower()

    @model_validator(mode="after")
    def _cross_field_checks(self) -> "WarehouseConfig":
        if self.paths.root_dir == self.paths.instrument_master_db_path:
            raise ValueError("root_dir must not equal instrument_master_db_path")
        return self

    # -- Derived path helpers ------------------------------------------------
    def resolved_paths(self) -> PathsConfig:
        return self.paths.resolve()

    def layer_dir(self, subdir_name: str) -> Path:
        return self.resolved_paths().root_dir / subdir_name

    def metadata_db_path(self) -> Path:
        from warehouse.core.constants import METADATA_DB_FILENAME
        return self.layer_dir(self.paths.metadata_subdir) / METADATA_DB_FILENAME


# ---------------------------------------------------------------------------
# Loader
# ---------------------------------------------------------------------------
def _load_yaml(path: Path) -> dict:
    if not path.exists():
        raise ConfigFileNotFoundError(
            f"Config file not found: {path}",
            context={"path": str(path)},
        )
    with open(path, "r", encoding="utf-8") as f:
        data = yaml.safe_load(f) or {}
    if not isinstance(data, dict):
        raise ConfigValidationError(
            "Config file root must be a mapping/dict",
            context={"path": str(path)},
        )
    return data


def _collect_env_overrides() -> dict:
    """
    Collect NGWH_-prefixed environment variables into a nested dict matching
    WarehouseConfig's shape, using double-underscore as the nesting separator,
    e.g. NGWH_STORAGE__DUCKDB_THREADS=8 -> {"storage": {"duckdb_threads": "8"}}
    """
    overrides: dict = {}
    for key, value in os.environ.items():
        if not key.startswith(ENV_PREFIX):
            continue
        path = key[len(ENV_PREFIX):].lower().split("__")
        cursor = overrides
        for part in path[:-1]:
            cursor = cursor.setdefault(part, {})
        cursor[path[-1]] = value
    return overrides


def _deep_merge(base: dict, override: dict) -> dict:
    result = dict(base)
    for k, v in override.items():
        if isinstance(v, dict) and isinstance(result.get(k), dict):
            result[k] = _deep_merge(result[k], v)
        else:
            result[k] = v
    return result


def load_config(
    config_file: Optional[Path | str] = None,
    *,
    require_file: bool = False,
    **overrides,
) -> WarehouseConfig:
    """
    Build a validated WarehouseConfig from (in increasing precedence):
    built-in defaults -> YAML file -> environment variables -> explicit
    keyword overrides.

    Args:
        config_file: Explicit path to a YAML config file. Falls back to the
            NGWH_CONFIG_FILE env var, then to `config/warehouse.yaml` relative
            to the current working directory if that file happens to exist.
        require_file: If True, raise ConfigFileNotFoundError when no config
            file is found anywhere. If False (default), silently proceed
            with defaults + env + overrides only.
        **overrides: Nested dict overrides applied last, e.g.
            load_config(storage={"duckdb_threads": 16})

    Raises:
        ConfigValidationError: if the merged configuration fails validation.
    """
    file_data: dict = {}
    resolved_file = Path(config_file) if config_file else (
        Path(os.environ["NGWH_CONFIG_FILE"]) if "NGWH_CONFIG_FILE" in os.environ else None
    )
    if resolved_file is None:
        default_candidate = Path.cwd() / "config" / DEFAULT_CONFIG_FILENAME
        if default_candidate.exists():
            resolved_file = default_candidate

    if resolved_file is not None:
        file_data = _load_yaml(resolved_file)
    elif require_file:
        raise ConfigFileNotFoundError(
            "No config file specified and none found at default location",
            context={"searched": str(Path.cwd() / "config" / DEFAULT_CONFIG_FILENAME)},
        )

    merged = _deep_merge(file_data, _collect_env_overrides())
    merged = _deep_merge(merged, overrides)

    try:
        return WarehouseConfig(**merged)
    except Exception as exc:  # pydantic.ValidationError, primarily
        raise ConfigValidationError(
            f"Warehouse configuration failed validation: {exc}",
            context={"config_file": str(resolved_file) if resolved_file else None},
        ) from exc

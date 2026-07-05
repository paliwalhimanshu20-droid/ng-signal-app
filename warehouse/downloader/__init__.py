"""
NGWH-002 — Historical Downloader
===================================

Institutional-grade ingestion layer for the Historical Intelligence
Warehouse, built entirely on top of the NGWH-001 foundation (Parquet
storage, DuckDB metadata, checkpointing, job tracking, instrument
registry). This package does not duplicate any of that — it consumes it.

Public surface:
    run_historical_backfill()      — full/partial historical backfill, resumable
    run_daily_incremental_update() — small daily catch-up window

Also exported for advanced/direct use:
    DownloaderConfig, RateLimitConfig, RetryConfig, IntervalPolicyConfig
    BatchRunner, BatchResult
    DownloadOrchestrator, OrchestratorResult
    UpstoxHistoricalClient
    Timeframe (re-exported from warehouse.core.constants for convenience)
"""

from warehouse.core.constants import Timeframe

from warehouse.downloader.api import (
    resolve_instrument_universe,
    run_daily_incremental_update,
    run_historical_backfill,
)
from warehouse.downloader.batch_runner import BatchResult, BatchRunner, InstrumentTimeframeFailure
from warehouse.downloader.download_orchestrator import DownloadOrchestrator, OrchestratorResult
from warehouse.downloader.downloader_config import (
    DownloaderConfig,
    IntervalPolicyConfig,
    IntervalPolicyEntry,
    RateLimitConfig,
    RetryConfig,
)
from warehouse.downloader.http_client import UpstoxHistoricalClient
from warehouse.downloader.rate_limiter import TokenBucketRateLimiter

__all__ = [
    "run_historical_backfill",
    "run_daily_incremental_update",
    "resolve_instrument_universe",
    "BatchRunner",
    "BatchResult",
    "InstrumentTimeframeFailure",
    "DownloadOrchestrator",
    "OrchestratorResult",
    "DownloaderConfig",
    "RateLimitConfig",
    "RetryConfig",
    "IntervalPolicyConfig",
    "IntervalPolicyEntry",
    "UpstoxHistoricalClient",
    "TokenBucketRateLimiter",
    "Timeframe",
]

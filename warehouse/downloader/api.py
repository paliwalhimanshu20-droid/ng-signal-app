"""
warehouse.downloader.api
============================

The intended integration surface for NGWH-002. The rest of ng-signal-app
(an Admin Center button, a scheduled GitHub Action, a one-off backfill
script) should call these functions rather than reaching into
`BatchRunner`/`DownloadOrchestrator` directly — this keeps one stable
entrypoint even as the internals evolve.

Usage from the Streamlit app (mirrors how `upstox_client.py` reads its
token — this module stays Streamlit-free and takes the token as a plain
argument):

    import streamlit as st
    from warehouse import load_config, WarehouseBootstrap
    from warehouse.downloader import DownloaderConfig, Timeframe, run_historical_backfill

    handles = WarehouseBootstrap(load_config()).run()
    result = run_historical_backfill(
        handles,
        access_token=st.secrets["UPSTOX_ACCESS_TOKEN"],
        instrument_ids=["NSE_EQ|INE002A01018", ...],
        timeframes=[Timeframe.DAY_1, Timeframe.MIN_30],
        start_date=date(2016, 1, 1),
        end_date=date.today(),
    )

Or let the downloader resolve which instruments to fetch directly from the
Instrument Master (NGSP-003A.1) instead of hand-listing them:

    result = run_historical_backfill(
        handles,
        access_token=st.secrets["UPSTOX_ACCESS_TOKEN"],
        instrument_ids=None,           # resolve from Instrument Master
        asset_class="equity",          # optional filter
        timeframes=[Timeframe.DAY_1],
        start_date=date(2016, 1, 1),
        end_date=date.today(),
    )
"""

from __future__ import annotations

from datetime import date
from typing import Optional

from warehouse.bootstrap.bootstrap import WarehouseHandles
from warehouse.core.constants import Timeframe
from warehouse.core.logging_config import get_logger, log_with_context
from warehouse.downloader.downloader_config import DownloaderConfig
from warehouse.downloader.batch_runner import BatchResult, BatchRunner
from warehouse.downloader.exceptions import DownloaderError
from warehouse.registry.instrument_registry import InstrumentRegistry

logger = get_logger(__name__)


def resolve_instrument_universe(
    handles: WarehouseHandles,
    *,
    asset_class: Optional[str] = None,
) -> list[str]:
    """
    Resolve the list of instrument_keys to download by querying the
    EXISTING Instrument Master (NGSP-003A.1) for active instruments, via
    NGWH-001's read-only `InstrumentRegistry` — this is the sanctioned
    soft-reference path; nothing here opens the Instrument Master DB
    directly.

    Args:
        asset_class: optional filter (e.g. "equity", "commodity_futures"),
            matching the Instrument Master's `asset_class` column. None
            returns every active instrument regardless of asset class.

    Raises:
        DownloaderError: if the Instrument Master DB is unavailable —
            wrapping InstrumentMasterUnavailableError so downloader callers
            only need to know one exception hierarchy.
    """
    from warehouse.core.exceptions import InstrumentMasterUnavailableError

    db_path = handles.config.resolved_paths().instrument_master_db_path
    registry = InstrumentRegistry(db_path)
    try:
        records = registry.list_active_instruments(asset_class=asset_class)
    except InstrumentMasterUnavailableError as exc:
        raise DownloaderError(
            f"Cannot resolve instrument universe — Instrument Master unavailable at {db_path}. "
            "Pass an explicit instrument_ids list instead, or ensure the Instrument Master DB exists.",
            context={"path": str(db_path), "asset_class": asset_class},
        ) from exc

    instrument_ids = [r.instrument_id for r in records]
    log_with_context(
        logger, 20, "Resolved instrument universe from Instrument Master",
        asset_class=asset_class, count=len(instrument_ids),
    )
    return instrument_ids


def _resolve_instrument_ids(
    handles: WarehouseHandles,
    instrument_ids: Optional[list[str]],
    asset_class: Optional[str],
) -> list[str]:
    if instrument_ids is not None:
        if asset_class is not None:
            raise ValueError("Pass either instrument_ids or asset_class, not both.")
        return instrument_ids
    return resolve_instrument_universe(handles, asset_class=asset_class)


def run_historical_backfill(
    handles: WarehouseHandles,
    access_token: str,
    instrument_ids: Optional[list[str]],
    timeframes: list[Timeframe],
    start_date: date,
    end_date: date,
    *,
    asset_class: Optional[str] = None,
    downloader_config: Optional[DownloaderConfig] = None,
    force_refresh: bool = False,
) -> BatchResult:
    """
    Run (or resume) a historical backfill across every (instrument,
    timeframe) combination requested. Safe to call repeatedly with the same
    arguments — already-covered ranges are skipped via the coverage
    planner, and a prior interrupted run's completed chunks are skipped via
    checkpoints.

    Pass `instrument_ids` explicitly, or pass None (optionally with
    `asset_class` set) to have the instrument list resolved automatically
    from the Instrument Master's active instruments.
    """
    resolved_ids = _resolve_instrument_ids(handles, instrument_ids, asset_class)
    runner = BatchRunner(handles, downloader_config or DownloaderConfig(), access_token)
    return runner.run_backfill(resolved_ids, timeframes, start_date, end_date, force_refresh=force_refresh)


def run_daily_incremental_update(
    handles: WarehouseHandles,
    access_token: str,
    instrument_ids: Optional[list[str]],
    timeframes: list[Timeframe],
    *,
    asset_class: Optional[str] = None,
    downloader_config: Optional[DownloaderConfig] = None,
    lookback_days: int = 5,
) -> BatchResult:
    """
    Run a small incremental update covering the last `lookback_days`
    calendar days. Intended to be called on a daily schedule (e.g. a
    GitHub Actions workflow, mirroring the existing `check_signals.yml`
    pattern) once NGWH-001/002 are live in production.

    Pass `instrument_ids` explicitly, or pass None (optionally with
    `asset_class` set) to have the instrument list resolved automatically
    from the Instrument Master's active instruments.
    """
    resolved_ids = _resolve_instrument_ids(handles, instrument_ids, asset_class)
    runner = BatchRunner(handles, downloader_config or DownloaderConfig(), access_token)
    return runner.run_incremental_update(resolved_ids, timeframes, lookback_days=lookback_days)

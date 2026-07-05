"""
warehouse_admin/downloader_page.py

The Historical Downloader UI. Every actual download call routes through
NGWH-002's public API (`warehouse.downloader.run_historical_backfill` /
`run_daily_incremental_update`) — nothing here reimplements download
logic, chunking, retries, or checkpointing.

Execution model note (see job_management.py's docstring for the full
version): clicking a backfill button here BLOCKS this Streamlit script
run until the batch completes, because NGWH-002's BatchRunner is
synchronous. For a handful of instruments and a modest date range this is
fine (seconds to low minutes). For a genuine 100-instrument x 10-year
backfill, this page's "Full Historical Backfill" will work but will hold
the browser tab/session open for a long time — the "Pilot Backfill" mode
exists specifically so that can be tried safely first, and
`scripts/run_warehouse_backfill.py` exists as a CLI alternative that runs
outside a Streamlit session entirely (recommended for the real full run).
"""

from __future__ import annotations

import datetime as dt

import streamlit as st

from config import UPSTOX_ACCESS_TOKEN
from warehouse.core.constants import Timeframe
from warehouse.downloader import DownloaderConfig, run_daily_incremental_update, run_historical_backfill
from warehouse.downloader.api import resolve_instrument_universe
from warehouse_admin.resource import get_downloader_config

_DOWNLOADABLE_TIMEFRAMES = [Timeframe.DAY_1, Timeframe.MIN_30, Timeframe.WEEK_1]
_PILOT_INSTRUMENT_LIMIT = 5
_PILOT_LOOKBACK_DAYS = 30


def _resolve_instrument_selection(handles, mode: str, asset_class: str | None, custom_text: str) -> list[str]:
    if mode == "All active instruments":
        return resolve_instrument_universe(handles, asset_class=None)
    if mode == "By asset class":
        return resolve_instrument_universe(handles, asset_class=asset_class or None)
    # Custom list — one instrument_key per line, blank lines/comments ignored.
    return [
        line.strip() for line in custom_text.splitlines()
        if line.strip() and not line.strip().startswith("#")
    ]


def render_historical_downloader(handles) -> None:
    st.markdown('<div class="section-eyebrow">⬇️ Historical Downloader</div>', unsafe_allow_html=True)

    if not UPSTOX_ACCESS_TOKEN:
        st.error(
            "UPSTOX_ACCESS_TOKEN is not configured (Settings → Secrets). "
            "The downloader cannot make requests until this is set."
        )
        return

    # ---- Instrument selection ----
    st.markdown("**1. Instrument Selection**")
    selection_mode = st.radio(
        "Source", ["All active instruments", "By asset class", "Custom list"],
        horizontal=True, key="wh_dl_selection_mode",
    )
    asset_class = None
    custom_text = ""
    if selection_mode == "By asset class":
        asset_class = st.selectbox(
            "Asset class", ["equity", "commodity_futures", "index"], key="wh_dl_asset_class"
        )
    elif selection_mode == "Custom list":
        custom_text = st.text_area(
            "Instrument keys (one per line)", key="wh_dl_custom_list",
            placeholder="NSE_EQ|INE002A01018\nNSE_EQ|INE467B01029",
            height=100,
        )

    try:
        preview_ids = _resolve_instrument_selection(handles, selection_mode, asset_class, custom_text)
        st.caption(f"{len(preview_ids)} instrument(s) currently match this selection.")
    except Exception as e:
        st.warning(f"Could not resolve instrument selection yet: {type(e).__name__}: {e}")
        preview_ids = []

    # ---- Timeframes ----
    st.markdown("**2. Timeframes**")
    selected_timeframes = st.multiselect(
        "Timeframes to download directly (5min/15min/1hour are derived, not fetched here)",
        options=_DOWNLOADABLE_TIMEFRAMES, default=[Timeframe.DAY_1],
        format_func=lambda t: t.value, key="wh_dl_timeframes",
    )

    # ---- Mode ----
    st.markdown("**3. Download Mode**")
    mode = st.radio(
        "Mode",
        ["Pilot Backfill", "Full Historical Backfill", "Incremental Daily Update"],
        key="wh_dl_mode",
        help=(
            "Pilot Backfill: capped to a handful of instruments and a short date range — "
            "the safe way to confirm everything works before a large run."
        ),
    )

    start_date = end_date = None
    lookback_days = _PILOT_LOOKBACK_DAYS
    force_refresh = False

    if mode in ("Pilot Backfill", "Full Historical Backfill"):
        col1, col2 = st.columns(2)
        default_start = dt.date.today() - dt.timedelta(days=_PILOT_LOOKBACK_DAYS if mode == "Pilot Backfill" else 365 * 5)
        with col1:
            start_date = st.date_input("Start date", value=default_start, key="wh_dl_start_date")
        with col2:
            end_date = st.date_input("End date", value=dt.date.today(), key="wh_dl_end_date")
        force_refresh = st.checkbox(
            "Force refresh (re-download even if already covered)", value=False, key="wh_dl_force_refresh"
        )
    else:
        lookback_days = st.slider("Lookback window (days)", min_value=1, max_value=30, value=5, key="wh_dl_lookback_days")

    # ---- Worker / retry configuration ----
    with st.expander("⚙️ Advanced: parallel workers & retry policy"):
        st.slider("Parallel workers", min_value=1, max_value=16, value=4, key="wh_max_parallel_downloads")
        st.slider("Max retries per request", min_value=0, max_value=10, value=5, key="wh_retry_max_retries")
        st.slider("Retry backoff base (seconds)", min_value=0.5, max_value=10.0, value=1.0, step=0.5, key="wh_retry_backoff_base")
        st.slider("Rate limit (requests/second)", min_value=0.5, max_value=10.0, value=2.0, step=0.5, key="wh_rate_limit_rps")

    if mode == "Full Historical Backfill" and len(preview_ids) > 20:
        st.warning(
            f"This selection covers {len(preview_ids)} instruments. Running this from the browser will "
            "block this tab until it finishes, which can take a long time at full scale. Consider "
            "`scripts/run_warehouse_backfill.py` from a terminal or a scheduled GitHub Action instead — "
            "it uses the exact same NGWH-002 code path and checkpoints identically."
        )

    st.markdown("---")

    button_label = {
        "Pilot Backfill": "🚀 Start Pilot Backfill",
        "Full Historical Backfill": "🚀 Start Full Historical Backfill",
        "Incremental Daily Update": "🔄 Run Incremental Update",
    }[mode]

    if st.button(button_label, type="primary"):
        if not selected_timeframes:
            st.error("Select at least one timeframe.")
            return
        if not preview_ids:
            st.error("No instruments matched the current selection.")
            return

        instrument_ids = preview_ids
        if mode == "Pilot Backfill":
            instrument_ids = instrument_ids[:_PILOT_INSTRUMENT_LIMIT]

        downloader_config: DownloaderConfig = get_downloader_config()

        with st.spinner(f"Running {mode.lower()} for {len(instrument_ids)} instrument(s)..."):
            try:
                if mode == "Incremental Daily Update":
                    result = run_daily_incremental_update(
                        handles, UPSTOX_ACCESS_TOKEN, instrument_ids, selected_timeframes,
                        downloader_config=downloader_config, lookback_days=lookback_days,
                    )
                else:
                    result = run_historical_backfill(
                        handles, UPSTOX_ACCESS_TOKEN, instrument_ids, selected_timeframes,
                        start_date, end_date,
                        downloader_config=downloader_config, force_refresh=force_refresh,
                    )
            except Exception as e:
                st.error(f"Download run failed: {type(e).__name__}: {e}")
                return

        st.session_state["wh_last_batch_result"] = result

        if result.is_fully_successful:
            st.success(
                f"Completed. {len(result.successes)} instrument/timeframe combination(s) succeeded, "
                f"{result.total_rows_written:,} candle(s) written."
            )
        else:
            st.warning(
                f"Completed with {len(result.failures)} failure(s) out of "
                f"{len(result.successes) + len(result.failures)} combination(s). "
                f"{result.total_rows_written:,} candle(s) written from the successful ones. "
                "See the Progress Monitoring tab for details."
            )

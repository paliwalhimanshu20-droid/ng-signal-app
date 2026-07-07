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

=== TEMPORARY TIMING INSTRUMENTATION (this session) ===
time.perf_counter() calls and print() timing reports have been added to
render_historical_downloader() and _count_instrument_selection() to
diagnose a 2-3 minute UI freeze on selection-mode change. No business
logic, SQL, widgets, caching, or return values were changed. Remove the
`import time` and all timing blocks once the slow stage is identified.
"""

from __future__ import annotations

import datetime as dt
import sqlite3
import time  # added: needed for perf_counter timing only

import streamlit as st

from config import UPSTOX_ACCESS_TOKEN
from warehouse.core.constants import (
    Timeframe,
    INSTRUMENT_MASTER_ACTIVE_FLAG_FIELD, INSTRUMENT_MASTER_ACTIVE_VALUE,
    INSTRUMENT_MASTER_ASSET_CLASS_FIELD,
)
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


# PERFORMANCE (audit finding, this session): render_historical_downloader()
# used to call _resolve_instrument_selection() unconditionally on every
# single render just to show a live "N instrument(s) match" caption —
# for "All active instruments" (Streamlit's st.radio() default, so this
# fired without the user touching anything) that meant fetching and
# materializing every matching row via resolve_instrument_universe() ->
# InstrumentRegistry.list_active_instruments(), measured at ~607ms per
# call against the real 126,644-row Instrument Master, on every rerun
# anywhere in the app (same st.tabs()-renders-everything cause as the
# Dashboard/Statistics pages fixed in the prior audit).
#
# The caption only ever needs a COUNT. This does a direct SQL COUNT(*)
# instead of materializing InstrumentRecord objects — mirrors the exact
# same WHERE-clause semantics as InstrumentRegistry.list_active_instruments()
# (same active-flag field/value, same optional asset_class filter, same
# read-only URI connection convention) so the number shown is always
# identical to what len(_resolve_instrument_selection(...)) would have
# been. This function does NOT touch warehouse/downloader/api.py,
# instrument_registry.py, or any Instrument Master code — it's a
# standalone, additive, UI-layer-only helper.
#
# Correctness note: this cached count is used ONLY for the live preview
# caption and the ">20 instruments" warning threshold. The actual
# download button always calls the real, unmodified
# _resolve_instrument_selection() at click time (see below) to get the
# real instrument_ids list — so a stale cached count can never cause a
# stale/wrong set of instruments to actually be downloaded, only a
# preview number that's at most a few seconds out of date.
@st.cache_data(ttl=10, show_spinner=False)
def _count_instrument_selection(_handles, mode: str, asset_class: str | None, custom_text: str) -> int:
    if mode == "Custom list":
        # No DB involved for this mode either way — counting the same
        # parsed lines _resolve_instrument_selection() would return.
        return len([
            line.strip() for line in custom_text.splitlines()
            if line.strip() and not line.strip().startswith("#")
        ])

    effective_asset_class = asset_class or None if mode == "By asset class" else None
    db_path = _handles.config.resolved_paths().instrument_master_db_path

    clause = f"WHERE {INSTRUMENT_MASTER_ACTIVE_FLAG_FIELD} = ?"
    params: tuple = (INSTRUMENT_MASTER_ACTIVE_VALUE,)
    if effective_asset_class is not None:
        clause += f" AND {INSTRUMENT_MASTER_ASSET_CLASS_FIELD} = ?"
        params = params + (effective_asset_class,)

    _sql_text = f"SELECT COUNT(*) FROM instruments {clause}"

    # Safe defaults so the timing/print block in `finally` can never raise
    # NameError if an exception happens partway through — none of this
    # affects the real control flow or return value.
    _t_exec = _t_fetch = None
    result = None

    # ---- TIMING: Open SQLite connection ----
    _t0 = time.perf_counter()
    conn = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
    _t_open = time.perf_counter()

    try:
        # ---- TIMING: Execute SQL ----
        cur = conn.execute(_sql_text, params)
        _t_exec = time.perf_counter()

        # ---- TIMING: Fetch result ----
        result = cur.fetchone()[0]
        _t_fetch = time.perf_counter()

        return result
    finally:
        # ---- TIMING: Close connection ----
        conn.close()
        _t_close = time.perf_counter()

        _open_ms = (_t_open - _t0) * 1000
        _exec_ms = ((_t_exec - _t_open) * 1000) if _t_exec is not None else float("nan")
        _fetch_ms = ((_t_fetch - _t_exec) * 1000) if (_t_fetch is not None and _t_exec is not None) else float("nan")
        _close_ms = (_t_close - (_t_fetch or _t_exec or _t_open)) * 1000
        _total_ms = (_t_close - _t0) * 1000

        print(
            "================ COUNT TIMING ================\n"
            f"selection mode:              {mode}\n"
            f"asset class:                 {effective_asset_class}\n"
            f"SQL query:                   {_sql_text}\n"
            f"SQL params:                  {params}\n"
            f"returned count:              {result}\n"
            "\n"
            f"Open SQLite connection      {_open_ms:7.1f} ms\n"
            f"Execute SQL                 {_exec_ms:7.1f} ms\n"
            f"Fetch result                {_fetch_ms:7.1f} ms\n"
            f"Close connection            {_close_ms:7.1f} ms\n"
            "\n"
            f"TOTAL COUNT                 {_total_ms:7.1f} ms\n"
            "=============================================="
        )


def render_historical_downloader(handles) -> None:
    _page_t0 = time.perf_counter()

    # ---------------- TIMING Step 1: Build page/header ----------------
    _s1_t0 = time.perf_counter()
    st.markdown('<div class="section-eyebrow">⬇️ Historical Downloader</div>', unsafe_allow_html=True)

    if not UPSTOX_ACCESS_TOKEN:
        st.error(
            "UPSTOX_ACCESS_TOKEN is not configured (Settings → Secrets). "
            "The downloader cannot make requests until this is set."
        )
        return
    _s1_ms = (time.perf_counter() - _s1_t0) * 1000

    # ---------------- TIMING Step 2: Read Streamlit widget values ----------------
    _s2_t0 = time.perf_counter()
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
    _s2_ms = (time.perf_counter() - _s2_t0) * 1000

    # ---------------- TIMING Step 3: _count_instrument_selection() ----------------
    _s3_t0 = time.perf_counter()
    try:
        match_count = _count_instrument_selection(handles, selection_mode, asset_class, custom_text)
        st.caption(f"{match_count} instrument(s) currently match this selection.")
    except Exception as e:
        st.warning(f"Could not resolve instrument selection yet: {type(e).__name__}: {e}")
        match_count = 0
    _s3_ms = (time.perf_counter() - _s3_t0) * 1000

    # ---------------- TIMING Step 4: Build remaining widgets ----------------
    _s4_t0 = time.perf_counter()
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
    _s4_ms = (time.perf_counter() - _s4_t0) * 1000

    # ---------------- TIMING Step 5: Asset class/custom processing ----------------
    _s5_t0 = time.perf_counter()
    if mode == "Full Historical Backfill" and match_count > 20:
        st.warning(
            f"This selection covers {match_count} instruments. Running this from the browser will "
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
    _s5_ms = (time.perf_counter() - _s5_t0) * 1000

    # ---------------- TIMING Step 6: Final page render ----------------
    _s6_t0 = time.perf_counter()
    button_clicked = st.button(button_label, type="primary")
    _s6_ms = (time.perf_counter() - _s6_t0) * 1000

    _total_ms = (time.perf_counter() - _page_t0) * 1000

    print(
        "================ DOWNLOADER PAGE TIMING ================\n"
        f"Step 1: Build page/header               {_s1_ms:7.1f} ms\n"
        f"Step 2: Read Streamlit widget values     {_s2_ms:7.1f} ms\n"
        f"Step 3: _count_instrument_selection()    {_s3_ms:7.1f} ms\n"
        f"Step 4: Build remaining widgets          {_s4_ms:7.1f} ms\n"
        f"Step 5: Asset class/custom processing    {_s5_ms:7.1f} ms\n"
        f"Step 6: Final page render                {_s6_ms:7.1f} ms\n"
        "\n"
        f"TOTAL PAGE TIME                          {_total_ms:7.1f} ms\n"
        "========================================================"
    )

    # PERFORMANCE (this session): the real, full instrument_ids list —
    # via the exact same unmodified _resolve_instrument_selection() the
    # old code called on every rerun — is now only computed right here,
    # at actual button-click time, instead of on every single render.
    # This is the ONLY place the full list is materialized; everything
    # above (caption, >20 warning) uses the cheap cached count instead.
    # Functionally identical to before: Streamlit reruns this whole
    # script on the click anyway, so resolving the list here is exactly
    # as fresh as resolving it eagerly at the top ever was.
    if button_clicked:
        if not selected_timeframes:
            st.error("Select at least one timeframe.")
            return

        try:
            preview_ids = _resolve_instrument_selection(handles, selection_mode, asset_class, custom_text)
        except Exception as e:
            st.error(f"Could not resolve instrument selection: {type(e).__name__}: {e}")
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
            

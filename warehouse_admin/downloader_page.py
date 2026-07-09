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

=== TEMPORARY, EXHAUSTIVE TIMING INSTRUMENTATION (this session) ===
Every stage, every widget call, every DB query, every filesystem/cache
access below is wrapped with a before/after timestamp and duration via
the `_timed()` context manager. Nothing about logic, SQL, caching, or
widget behavior is changed — `_timed()` only measures wall-clock time
around the exact same calls that were already there. Widgets/stages
taking longer than 50ms are flagged inline as SLOW. A full report,
sorted slowest-first, prints at the end of render_historical_downloader().
Remove `_timed`, `_print_timing_summary`, `_SLOW_THRESHOLD_MS`, the
`import time`/`contextmanager`/`datetime` lines, and every
`with _timed(...)` wrapper once the slow stage is identified — none of
this is meant to be permanent.
"""

from __future__ import annotations

import datetime as dt
import sqlite3
import time  # added: perf_counter timing only
from contextlib import contextmanager  # added: _timed() diagnostic wrapper only
from datetime import datetime as _dt  # added: wall-clock timestamps for before/after prints

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

# ============================================================
# DIAGNOSTIC-ONLY TIMING INFRASTRUCTURE — remove after diagnosis
# ============================================================
_SLOW_THRESHOLD_MS = 50.0


def _now_iso() -> str:
    return _dt.now().isoformat(timespec="milliseconds")


@contextmanager
def _timed(label: str, bucket: list):
    """
    Diagnostic-only. Prints a timestamped BEFORE line, yields (runs the
    exact original code unchanged), then prints a timestamped AFTER line
    with the elapsed duration, flagging anything over _SLOW_THRESHOLD_MS.
    Appends (label, elapsed_ms) to `bucket` for the end-of-page summary.
    Never touches return values, arguments, or control flow of the code
    it wraps.
    """
    t0 = time.perf_counter()
    print(f"[TIMING START] {label} @ {_now_iso()}")
    try:
        yield
    finally:
        elapsed_ms = (time.perf_counter() - t0) * 1000
        bucket.append((label, elapsed_ms))
        flag = "  <<< SLOW (>50ms)" if elapsed_ms > _SLOW_THRESHOLD_MS else ""
        print(f"[TIMING END] {label} = {elapsed_ms:.2f} ms{flag} @ {_now_iso()}")


def _print_timing_summary(title: str, bucket: list, total_ms: float) -> None:
    ordered = sorted(bucket, key=lambda pair: pair[1], reverse=True)
    slow = [p for p in ordered if p[1] > _SLOW_THRESHOLD_MS]
    header = f"================ {title} ================"
    lines = [header]
    for label, ms in ordered:
        marker = " <<< SLOW" if ms > _SLOW_THRESHOLD_MS else ""
        lines.append(f"{label:<45s} {ms:9.2f} ms{marker}")
    lines.append("")
    lines.append(f"TOTAL{'':<41s}{total_ms:9.2f} ms")
    lines.append("")
    if slow:
        lines.append(f"--- {len(slow)} operation(s) over {_SLOW_THRESHOLD_MS:.0f}ms, slowest first ---")
        for label, ms in slow:
            lines.append(f"  {label:<43s} {ms:9.2f} ms")
    else:
        lines.append(f"--- no operation exceeded {_SLOW_THRESHOLD_MS:.0f}ms ---")
    lines.append("=" * len(header))
    print("\n".join(lines))


# ============================================================
# END DIAGNOSTIC INFRASTRUCTURE
# ============================================================


def _resolve_instrument_selection(handles, mode: str, asset_class: str | None, custom_text: str) -> list[str]:
    _timings: list = []
    _t0 = time.perf_counter()

    if mode == "All active instruments":
        with _timed("InstrumentRegistry: resolve_instrument_universe(all)", _timings):
            result = resolve_instrument_universe(handles, asset_class=None)
        _print_timing_summary("RESOLVE SELECTION TIMING", _timings, (time.perf_counter() - _t0) * 1000)
        return result

    if mode == "By asset class":
        with _timed(f"InstrumentRegistry: resolve_instrument_universe({asset_class})", _timings):
            result = resolve_instrument_universe(handles, asset_class=asset_class or None)
        _print_timing_summary("RESOLVE SELECTION TIMING", _timings, (time.perf_counter() - _t0) * 1000)
        return result

    # Custom list — one instrument_key per line, blank lines/comments ignored.
    with _timed("Custom list: parse lines (no DB)", _timings):
        result = [
            line.strip() for line in custom_text.splitlines()
            if line.strip() and not line.strip().startswith("#")
        ]
    _print_timing_summary("RESOLVE SELECTION TIMING", _timings, (time.perf_counter() - _t0) * 1000)
    return result


# PERFORMANCE (audit finding, prior session): render_historical_downloader()
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
# _resolve_instrument_selection() at click time (see above) to get the
# real instrument_ids list — so a stale cached count can never cause a
# stale/wrong set of instruments to actually be downloaded, only a
# preview number that's at most a few seconds out of date.
#
# NOTE on cache lookups (diagnostic): @st.cache_data intercepts the call
# BEFORE this function body runs. On a cache HIT, none of the code below
# executes at all — you will see the outer "_count_instrument_selection()
# [cache lookup + possible execution]" line in the page-level report (its
# duration on a hit is just the cache-key hash+lookup, typically <1ms),
# but NONE of the inner COUNT TIMING lines will print, because the
# function body — including every DB call below — was skipped entirely.
# On a cache MISS, the body runs and the full inner breakdown prints.
@st.cache_data(ttl=10, show_spinner=False)
def _count_instrument_selection(_handles, mode: str, asset_class: str | None, custom_text: str) -> int:
    _timings: list = []
    _fn_t0 = time.perf_counter()

    if mode == "Custom list":
        # No DB involved for this mode either way — counting the same
        # parsed lines _resolve_instrument_selection() would return.
        with _timed("Custom list: parse lines (no DB)", _timings):
            result = len([
                line.strip() for line in custom_text.splitlines()
                if line.strip() and not line.strip().startswith("#")
            ])
        _print_timing_summary("COUNT TIMING (custom list, no DB)", _timings, (time.perf_counter() - _fn_t0) * 1000)
        return result

    with _timed("Filesystem: _handles.config.resolved_paths()", _timings):
        effective_asset_class = asset_class or None if mode == "By asset class" else None
        db_path = _handles.config.resolved_paths().instrument_master_db_path

    with _timed("Build SQL clause/params (in-memory, no I/O)", _timings):
        clause = f"WHERE {INSTRUMENT_MASTER_ACTIVE_FLAG_FIELD} = ?"
        params: tuple = (INSTRUMENT_MASTER_ACTIVE_VALUE,)
        if effective_asset_class is not None:
            clause += f" AND {INSTRUMENT_MASTER_ASSET_CLASS_FIELD} = ?"
            params = params + (effective_asset_class,)
        sql_text = f"SELECT COUNT(*) FROM instruments {clause}"

    print(f"[QUERY]         selection_mode={mode!r} asset_class={effective_asset_class!r}")
    print(f"[QUERY]         SQL = {sql_text}")
    print(f"[QUERY]         params = {params}")

    result = None
    with _timed("DB: sqlite3.connect(mode=ro)", _timings):
        conn = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
    try:
        with _timed("DB: execute SELECT COUNT(*)", _timings):
            cur = conn.execute(sql_text, params)
        with _timed("DB: fetchone()", _timings):
            result = cur.fetchone()[0]
        print(f"[QUERY]         returned count = {result}")
        return result
    finally:
        with _timed("DB: connection.close()", _timings):
            conn.close()
        _print_timing_summary("COUNT TIMING (DB path)", _timings, (time.perf_counter() - _fn_t0) * 1000)


def render_historical_downloader(handles) -> None:
    _page_timings: list = []
    _page_t0 = time.perf_counter()
    print(f"\n########## render_historical_downloader() START @ {_now_iso()} ##########")

    # ---- Build page/header ----
    with _timed("st.markdown(eyebrow header)", _page_timings):
        st.markdown('<div class="section-eyebrow">⬇️ Historical Downloader</div>', unsafe_allow_html=True)

    with _timed("Read UPSTOX_ACCESS_TOKEN + gate check", _page_timings):
        token_missing = not UPSTOX_ACCESS_TOKEN
    if token_missing:
        with _timed("st.error(token missing)", _page_timings):
            st.error(
                "UPSTOX_ACCESS_TOKEN is not configured (Settings → Secrets). "
                "The downloader cannot make requests until this is set."
            )
        _total_ms = (time.perf_counter() - _page_t0) * 1000
        _print_timing_summary("DOWNLOADER PAGE TIMING (early return: no token)", _page_timings, _total_ms)
        return

    # ---- Instrument selection ----
    with _timed("st.markdown('1. Instrument Selection')", _page_timings):
        st.markdown("**1. Instrument Selection**")

    with _timed("st.radio(selection_mode)", _page_timings):
        selection_mode = st.radio(
            "Source", ["All active instruments", "By asset class", "Custom list"],
            horizontal=True, key="wh_dl_selection_mode",
        )

    asset_class = None
    custom_text = ""
    if selection_mode == "By asset class":
        with _timed("st.selectbox(asset_class)", _page_timings):
            asset_class = st.selectbox(
                "Asset class", ["equity", "commodity_futures", "index"], key="wh_dl_asset_class"
            )
    elif selection_mode == "Custom list":
        with _timed("st.text_area(custom_list)", _page_timings):
            custom_text = st.text_area(
                "Instrument keys (one per line)", key="wh_dl_custom_list",
                placeholder="NSE_EQ|INE002A01018\nNSE_EQ|INE467B01029",
                height=100,
            )

    print(f"[STATE]         selection_mode={selection_mode!r} asset_class={asset_class!r}")

    with _timed("_count_instrument_selection() [cache lookup + possible execution]", _page_timings):
        try:
            match_count = _count_instrument_selection(handles, selection_mode, asset_class, custom_text)
            with _timed("st.caption(match_count)", _page_timings):
                st.caption(f"{match_count} instrument(s) currently match this selection.")
        except Exception as e:
            with _timed("st.warning(count exception)", _page_timings):
                st.warning(f"Could not resolve instrument selection yet: {type(e).__name__}: {e}")
            match_count = 0

    # ---- Timeframes ----
    with _timed("st.markdown('2. Timeframes')", _page_timings):
        st.markdown("**2. Timeframes**")

    with _timed("st.multiselect(timeframes)", _page_timings):
        selected_timeframes = st.multiselect(
            "Timeframes to download directly (5min/15min/1hour are derived, not fetched here)",
            options=_DOWNLOADABLE_TIMEFRAMES, default=[Timeframe.DAY_1],
            format_func=lambda t: t.value, key="wh_dl_timeframes",
        )

    # ---- Mode ----
    with _timed("st.markdown('3. Download Mode')", _page_timings):
        st.markdown("**3. Download Mode**")

    with _timed("st.radio(mode)", _page_timings):
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
        with _timed("st.columns(2) [date row]", _page_timings):
            col1, col2 = st.columns(2)
        with _timed("compute default_start (in-memory)", _page_timings):
            default_start = dt.date.today() - dt.timedelta(days=_PILOT_LOOKBACK_DAYS if mode == "Pilot Backfill" else 365 * 5)
        with col1:
            with _timed("st.date_input(start_date)", _page_timings):
                start_date = st.date_input("Start date", value=default_start, key="wh_dl_start_date")
        with col2:
            with _timed("st.date_input(end_date)", _page_timings):
                end_date = st.date_input("End date", value=dt.date.today(), key="wh_dl_end_date")
        with _timed("st.checkbox(force_refresh)", _page_timings):
            force_refresh = st.checkbox(
                "Force refresh (re-download even if already covered)", value=False, key="wh_dl_force_refresh"
            )
    else:
        with _timed("st.slider(lookback_days)", _page_timings):
            lookback_days = st.slider("Lookback window (days)", min_value=1, max_value=30, value=5, key="wh_dl_lookback_days")

    # ---- Worker / retry configuration ----
    with _timed("st.expander(advanced) [full block]", _page_timings):
        with st.expander("⚙️ Advanced: parallel workers & retry policy"):
            with _timed("  st.slider(max_parallel_downloads)", _page_timings):
                st.slider("Parallel workers", min_value=1, max_value=16, value=4, key="wh_max_parallel_downloads")
            with _timed("  st.slider(retry_max_retries)", _page_timings):
                st.slider("Max retries per request", min_value=0, max_value=10, value=5, key="wh_retry_max_retries")
            with _timed("  st.slider(retry_backoff_base)", _page_timings):
                st.slider("Retry backoff base (seconds)", min_value=0.5, max_value=10.0, value=1.0, step=0.5, key="wh_retry_backoff_base")
            with _timed("  st.slider(rate_limit_rps)", _page_timings):
                st.slider("Rate limit (requests/second)", min_value=0.5, max_value=10.0, value=2.0, step=0.5, key="wh_rate_limit_rps")

    if mode == "Full Historical Backfill" and match_count > 20:
        with _timed("st.warning(>20 instruments)", _page_timings):
            st.warning(
                f"This selection covers {match_count} instruments. Running this from the browser will "
                "block this tab until it finishes, which can take a long time at full scale. Consider "
                "`scripts/run_warehouse_backfill.py` from a terminal or a scheduled GitHub Action instead — "
                "it uses the exact same NGWH-002 code path and checkpoints identically."
            )

    with _timed("st.markdown('---')", _page_timings):
        st.markdown("---")

    with _timed("resolve button_label (in-memory dict lookup)", _page_timings):
        button_label = {
            "Pilot Backfill": "🚀 Start Pilot Backfill",
            "Full Historical Backfill": "🚀 Start Full Historical Backfill",
            "Incremental Daily Update": "🔄 Run Incremental Update",
        }[mode]

    with _timed("st.button(download button)", _page_timings):
        button_clicked = st.button(button_label, type="primary")

    _total_ms = (time.perf_counter() - _page_t0) * 1000
    _print_timing_summary("DOWNLOADER PAGE TIMING", _page_timings, _total_ms)
    print(f"########## render_historical_downloader() END   @ {_now_iso()} ##########\n")

    # PERFORMANCE (prior session): the real, full instrument_ids list — via
    # the exact same unmodified _resolve_instrument_selection() the old code
    # called on every rerun — is now only computed right here, at actual
    # button-click time, instead of on every single render. This is the
    # ONLY place the full list is materialized; everything above (caption,
    # >20 warning) uses the cheap cached count instead. Functionally
    # identical to before: Streamlit reruns this whole script on the click
    # anyway, so resolving the list here is exactly as fresh as resolving
    # it eagerly at the top ever was.
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

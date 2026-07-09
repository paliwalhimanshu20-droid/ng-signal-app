"""
warehouse_admin/render.py

The single entry point app.py should call to render the entire Warehouse
section inside the Admin Center — mirrors ui_components.render_validation_summary()
being the one call site for the Validation Center. Composes the five
NGWH-003 pages into a sub-tab layout, and handles first-time initialization
(the warehouse may not be bootstrapped yet on a fresh deployment).

=== TEMPORARY TIMING INSTRUMENTATION (this session) ===
Wraps get_warehouse_handles() and each of the five tab bodies with a
before/after timestamp + duration. No logic changed — every call below is
identical to the original; only timing prints were added. Remove
`_timed`/`import time`/`_now_iso` and every `with _timed(...)` block once
the slow stage is identified.
"""

from __future__ import annotations

import time
from contextlib import contextmanager
from datetime import datetime as _dt

import streamlit as st

from warehouse_admin.resource import get_warehouse_handles
from warehouse_admin.render_dashboard import render_warehouse_dashboard
from warehouse_admin.render_statistics import render_warehouse_statistics
from warehouse_admin.render_job_management import render_job_management
from warehouse_admin.render_progress import render_progress_monitor
from warehouse_admin.downloader_page import render_historical_downloader

_SLOW_THRESHOLD_MS = 50.0


def _now_iso() -> str:
    return _dt.now().isoformat(timespec="milliseconds")


@contextmanager
def _timed(label: str):
    t0 = time.perf_counter()
    print(f"[TIMING START] {label} @ {_now_iso()}")
    try:
        yield
    finally:
        ms = (time.perf_counter() - t0) * 1000
        flag = "  <<< SLOW (>50ms)" if ms > _SLOW_THRESHOLD_MS else ""
        print(f"[TIMING END] {label} = {ms:.2f} ms{flag} @ {_now_iso()}")


def render_warehouse_center() -> None:
    """
    Renders the complete Warehouse Operations Center. Call this from
    app.py's Admin Center tab, below the existing Reports/Tools columns
    and Validation Center report — it's fully self-contained and does not
    read or write any Scanner/Performance/Settings state.
    """
    _center_t0 = time.perf_counter()
    print(f"\n########## render_warehouse_center() START @ {_now_iso()} ##########")

    st.markdown('<div class="section-eyebrow">🏛️ Warehouse Operations Center</div>', unsafe_allow_html=True)
    st.caption(
        "Historical Intelligence Warehouse (NGSP-003A.3) — storage foundation, downloader, "
        "and operations. Independent of the live Scanner/Performance/Settings tabs."
    )

    try:
        with _timed("get_warehouse_handles() [cache_resource lookup + possible bootstrap]"):
            handles = get_warehouse_handles()
    except Exception as e:
        st.error(f"Warehouse initialization failed: {type(e).__name__}: {e}")
        st.caption(
            "This does not affect the Scanner, Performance, or Settings tabs — "
            "the warehouse is a fully independent module."
        )
        return

    dash_tab, downloader_tab, progress_tab, jobs_tab, stats_tab = st.tabs([
        "📦 Dashboard", "⬇️ Downloader", "📡 Progress", "🗂️ Jobs", "📊 Statistics",
    ])

    with dash_tab:
        with _timed("TAB: render_warehouse_dashboard()"):
            render_warehouse_dashboard(handles)
    with downloader_tab:
        with _timed("TAB: render_historical_downloader()"):
            render_historical_downloader(handles)
    with progress_tab:
        with _timed("TAB: render_progress_monitor()"):
            render_progress_monitor()
    with jobs_tab:
        with _timed("TAB: render_job_management()"):
            render_job_management(handles)
    with stats_tab:
        with _timed("TAB: render_warehouse_statistics()"):
            render_warehouse_statistics(handles)

    _center_total_ms = (time.perf_counter() - _center_t0) * 1000
    flag = "  <<< SLOW" if _center_total_ms > _SLOW_THRESHOLD_MS else ""
    print(f"########## render_warehouse_center() END   @ {_now_iso()}  TOTAL={_center_total_ms:9.2f} ms{flag} ##########\n")

"""
warehouse_admin/render.py

The single entry point app.py should call to render the entire Warehouse
section inside the Admin Center — mirrors ui_components.render_validation_summary()
being the one call site for the Validation Center. Composes the five
NGWH-003 pages into a sub-tab layout, and handles first-time initialization
(the warehouse may not be bootstrapped yet on a fresh deployment).
"""

from __future__ import annotations

import streamlit as st

from warehouse_admin.resource import get_warehouse_handles
from warehouse_admin.render_dashboard import render_warehouse_dashboard
from warehouse_admin.render_statistics import render_warehouse_statistics
from warehouse_admin.render_job_management import render_job_management
from warehouse_admin.render_progress import render_progress_monitor
from warehouse_admin.downloader_page import render_historical_downloader


def render_warehouse_center() -> None:
    """
    Renders the complete Warehouse Operations Center. Call this from
    app.py's Admin Center tab, below the existing Reports/Tools columns
    and Validation Center report — it's fully self-contained and does not
    read or write any Scanner/Performance/Settings state.
    """
    st.markdown('<div class="section-eyebrow">🏛️ Warehouse Operations Center</div>', unsafe_allow_html=True)
    st.caption(
        "Historical Intelligence Warehouse (NGSP-003A.3) — storage foundation, downloader, "
        "and operations. Independent of the live Scanner/Performance/Settings tabs."
    )

    try:
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
        render_warehouse_dashboard(handles)
    with downloader_tab:
        render_historical_downloader(handles)
    with progress_tab:
        render_progress_monitor()
    with jobs_tab:
        render_job_management(handles)
    with stats_tab:
        render_warehouse_statistics(handles)

"""
warehouse_admin/render_dashboard.py

Renders the Warehouse Dashboard tab. Pure rendering — all numbers come
from `stats.compute_dashboard_stats()`; this file only formats them,
mirroring ui_components.py's compute/render split used everywhere else in
this app.
"""

from __future__ import annotations

import streamlit as st

from ui_components import render_stat_cards
from warehouse_admin.stats import DashboardStats, compute_dashboard_stats, format_bytes

_STATUS_COLOR = {
    "healthy": "#166534", "pass": "#166534",
    "degraded": "#92400E", "warning": "#92400E",
    "unhealthy": "#9F1239", "fail": "#9F1239",
    "unknown": "#6B7280", "skipped": "#6B7280",
}


# PERFORMANCE (audit finding): compute_dashboard_stats() was measured at
# ~475-575ms per call on the real production Instrument Master (126,644
# rows) — and it was being called unconditionally on EVERY Streamlit
# rerun anywhere in the app (st.tabs() renders every tab body every
# rerun, not just the visible one). ttl=10 means at most one real
# computation every 10 seconds; every other rerun in that window reuses
# the cached DashboardStats instantly. 10s (not the 15s used for the
# signal log) because this panel includes live job-progress-adjacent
# fields (jobs_running, jobs_failed) — tune down further, or drop the
# decorator entirely, if you want this panel always perfectly live at
# the cost of the original per-rerun cost.
#
# `_handles` (leading underscore) tells st.cache_data to skip hashing
# this argument — required because WarehouseHandles holds a live DuckDB
# connection, which isn't hashable. This is the officially documented
# Streamlit pattern for passing an unhashable resource into a cached
# function; get_warehouse_handles() itself already uses the equivalent
# st.cache_resource pattern for the same underlying reason.
@st.cache_data(ttl=10, show_spinner=False)
def _cached_dashboard_stats(_handles) -> DashboardStats:
    return compute_dashboard_stats(_handles)


def _status_pill(label: str) -> str:
    color = _STATUS_COLOR.get(label.lower(), "#6B7280")
    return f'<span style="color:{color};font-weight:700;">{label.upper()}</span>'


def render_warehouse_dashboard(handles) -> None:
    st.markdown('<div class="section-eyebrow">📦 Warehouse Dashboard</div>', unsafe_allow_html=True)

    with st.spinner("Computing warehouse statistics..."):
        try:
            dash: DashboardStats = _cached_dashboard_stats(handles)
        except Exception as e:
            st.error(f"Could not compute dashboard statistics: {type(e).__name__}: {e}")
            return

    # ---- Overall health banner ----
    health_col1, health_col2 = st.columns([1, 3])
    with health_col1:
        st.metric("Warehouse Health", f"{dash.health_score_percent:.0f}%")
    with health_col2:
        st.markdown(
            f'<div style="padding-top:14px;font-size:1.1rem;">'
            f'Overall status: {_status_pill(dash.overall_status)}</div>',
            unsafe_allow_html=True,
        )

    if not dash.instrument_master_available:
        st.warning(
            "Instrument Master database is not reachable — instrument counts and coverage% "
            "cannot be computed until it exists. This does not affect already-downloaded warehouse data."
        )

    st.markdown("")

    # ---- Instrument + coverage cards ----
    render_stat_cards([
        ("Total Instruments", str(dash.total_instruments) if dash.total_instruments is not None else "N/A", "default"),
        ("Active Instruments", str(dash.active_instruments) if dash.active_instruments is not None else "N/A", "default"),
        ("Total Candles", f"{dash.total_candles:,}", "default"),
        ("Years of Coverage", f"{dash.years_of_coverage:g}", "default"),
    ])

    st.markdown("")

    # ---- Storage + partitions ----
    render_stat_cards([
        ("Storage Used", format_bytes(dash.storage_used_bytes), "default"),
        ("Partitions", f"{dash.partition_count:,}", "default"),
        ("DuckDB Status", dash.duckdb_status.upper(), "pos" if dash.duckdb_status == "healthy" else "neg"),
        ("Metadata DB Status", dash.metadata_db_status.upper(), "pos" if dash.metadata_db_status == "healthy" else "neg"),
    ])

    st.markdown("")

    # ---- Jobs + checkpoints ----
    render_stat_cards([
        ("Running Jobs", str(dash.jobs_running), "default" if dash.jobs_running == 0 else "pos"),
        ("Completed Jobs", str(dash.jobs_completed), "pos"),
        ("Failed Jobs", str(dash.jobs_failed), "default" if dash.jobs_failed == 0 else "neg"),
        ("Pending Checkpoints", str(dash.resume_checkpoints_pending), "default"),
    ])

    st.markdown("")

    # ---- Last activity ----
    last_dl = dash.last_successful_download.strftime("%Y-%m-%d %H:%M UTC") if dash.last_successful_download else "Never"
    last_inc = dash.last_incremental_update.strftime("%Y-%m-%d %H:%M UTC") if dash.last_incremental_update else "Never"
    render_stat_cards([
        ("Last Successful Download", last_dl, "default"),
        ("Last Incremental Update", last_inc, "default"),
    ])

    if dash.jobs_failed > 0:
        st.info(f"{dash.jobs_failed} job(s) have failed — see the Job Management tab to review or restart them.")
    if dash.resume_checkpoints_pending > 0:
        st.info(
            f"{dash.resume_checkpoints_pending} checkpoint(s) are pending completion — "
            "a prior backfill or update may not have finished. See Job Management to resume."
        )

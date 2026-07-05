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


def _status_pill(label: str) -> str:
    color = _STATUS_COLOR.get(label.lower(), "#6B7280")
    return f'<span style="color:{color};font-weight:700;">{label.upper()}</span>'


def render_warehouse_dashboard(handles) -> None:
    st.markdown('<div class="section-eyebrow">📦 Warehouse Dashboard</div>', unsafe_allow_html=True)

    with st.spinner("Computing warehouse statistics..."):
        try:
            dash: DashboardStats = compute_dashboard_stats(handles)
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

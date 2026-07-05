"""
warehouse_admin/render_statistics.py

Renders the Warehouse Statistics tab — summary cards plus a missing-data
table. Pure rendering over `stats.compute_warehouse_statistics()`.
"""

from __future__ import annotations

import streamlit as st

from ui_components import render_stat_cards
from warehouse_admin.stats import WarehouseStatistics, compute_warehouse_statistics, format_bytes


# PERFORMANCE (audit finding): compute_warehouse_statistics() was measured
# at ~1000-1050ms per call on the real production Instrument Master
# (126,644 rows) — combined with render_dashboard's equivalent cost, that
# was ~1.5s of pure backend computation on EVERY Streamlit rerun anywhere
# in the app, most of it for a page nobody was even looking at. ttl=15
# (slightly longer than the dashboard's 10s — this panel is aggregate
# coverage/storage stats, not live job progress) means at most one real
# computation every 15 seconds; every other rerun reuses the cached
# WarehouseStatistics instantly.
#
# `_handles` (leading underscore) — see render_dashboard.py's identical
# comment; same reason (WarehouseHandles holds a live, unhashable DuckDB
# connection, so it must be excluded from st.cache_data's hash key).
@st.cache_data(ttl=15, show_spinner=False)
def _cached_warehouse_statistics(_handles) -> WarehouseStatistics:
    return compute_warehouse_statistics(_handles)


def render_warehouse_statistics(handles) -> None:
    st.markdown('<div class="section-eyebrow">📊 Warehouse Statistics</div>', unsafe_allow_html=True)

    with st.spinner("Computing warehouse statistics..."):
        try:
            stats: WarehouseStatistics = _cached_warehouse_statistics(handles)
        except Exception as e:
            st.error(f"Could not compute warehouse statistics: {type(e).__name__}: {e}")
            return

    render_stat_cards([
        ("Total Partitions", f"{stats.total_partitions:,}", "default"),
        ("Total Files", f"{stats.total_files:,}", "default"),
        ("Storage Size", format_bytes(stats.storage_size_bytes), "default"),
        ("Avg Partition Size", format_bytes(int(stats.average_partition_size_bytes)), "default"),
    ])

    st.markdown("")

    coverage_display = f"{stats.coverage_percent:g}%" if stats.coverage_percent is not None else "N/A"
    earliest_display = stats.earliest_date.strftime("%Y-%m-%d") if stats.earliest_date else "N/A"
    latest_display = stats.latest_date.strftime("%Y-%m-%d") if stats.latest_date else "N/A"

    render_stat_cards([
        ("Coverage %", coverage_display, "pos" if (stats.coverage_percent or 0) >= 90 else "default"),
        ("Earliest Date", earliest_display, "default"),
        ("Latest Date", latest_display, "default"),
        ("Missing Instruments", str(len(stats.missing_instruments)), "default" if not stats.missing_instruments else "neg"),
    ])

    st.markdown("")
    st.markdown("**Data Quality Summary**")
    st.info(stats.data_quality_summary)

    if stats.missing_instruments:
        with st.expander(f"⚠ {len(stats.missing_instruments)} active instrument(s) with no downloaded data"):
            for instrument_id in stats.missing_instruments:
                st.markdown(f"- `{instrument_id}`")
            st.caption(
                "These are active in the Instrument Master but have no coverage in the warehouse yet. "
                "Run a backfill from the Historical Downloader tab to fill this in."
            )

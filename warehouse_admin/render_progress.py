"""
warehouse_admin/render_progress.py

Renders the Progress Monitoring tab. See progress_monitor.py's docstring
for the honest limitation: live progress is only observable while a
backfill is actually blocking the current script run (rare to catch mid-
render given Streamlit's synchronous model), so this also shows the last
completed run's summary from session_state, which is what most users will
actually see most of the time.
"""

from __future__ import annotations

import streamlit as st

from warehouse_admin.progress_monitor import build_progress_view


def render_progress_monitor() -> None:
    st.markdown('<div class="section-eyebrow">📡 Progress Monitoring</div>', unsafe_allow_html=True)

    last_result = st.session_state.get("wh_last_batch_result")
    view = build_progress_view(last_result)

    if view.is_live:
        st.markdown(f"**Current job:** {view.label}")
        st.progress(min(view.percent_complete / 100.0, 1.0))
        c1, c2, c3, c4 = st.columns(4)
        with c1:
            st.metric("Progress", f"{view.percent_complete:.1f}%")
        with c2:
            st.metric("Completed", f"{view.completed_units}/{view.total_units}")
        with c3:
            st.metric("Elapsed", f"{view.elapsed_seconds:.0f}s")
        with c4:
            eta = f"{view.eta_seconds:.0f}s" if view.eta_seconds is not None else "—"
            st.metric("ETA", eta)
        return

    if view.last_run_summary is None:
        st.info(
            "No download has been run in this session yet. Start a Pilot Backfill, Full Backfill, "
            "or Incremental Update from the Historical Downloader tab to see progress here."
        )
        return

    summary = view.last_run_summary
    st.markdown(f"**Last completed run:** `{summary['job_id'][:8]}`")

    c1, c2, c3 = st.columns(3)
    with c1:
        st.metric("Instruments Completed", summary["instruments_completed"])
    with c2:
        st.metric("Instruments Failed", summary["instruments_failed"])
    with c3:
        st.metric("Candles Downloaded", f"{summary['total_candles_downloaded']:,}")

    if summary["is_fully_successful"]:
        st.success("This run completed with no failures.")
    else:
        st.warning(f"{summary['instruments_failed']} instrument/timeframe combination(s) failed during this run.")
        with st.expander("View failures"):
            for f in summary["failures"]:
                st.error(f"{f['instrument_id']} ({f['timeframe']}): {f['error']}")

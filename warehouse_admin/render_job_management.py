"""
warehouse_admin/render_job_management.py

Renders the Job Management tab: full job history table plus per-job
actions (Pause, Cancel, Restart Failed). See job_management.py's module
docstring for the honest scope of what Pause/Cancel mean in this app's
synchronous execution model — that explanation is surfaced to the user
here too, not just in code comments.
"""

from __future__ import annotations

import pandas as pd
import streamlit as st

from warehouse.core.constants import JobStatus, JobType
from warehouse_admin.job_management import (
    build_resume_plan,
    find_stale_running_jobs,
    list_job_history,
    request_cancel,
    request_pause,
    restart_failed_job_as_new,
)

_STATUS_BADGE = {
    "pending": "🕐 PENDING", "running": "🔵 RUNNING", "paused": "⏸️ PAUSED",
    "completed": "✅ COMPLETED", "failed": "🔴 FAILED", "cancelled": "⚪ CANCELLED",
}


def render_job_management(handles) -> None:
    st.markdown('<div class="section-eyebrow">🗂️ Job Management</div>', unsafe_allow_html=True)

    st.caption(
        "This app runs downloads synchronously within a single browser session — there is no "
        "separate always-on worker process. A job shown as RUNNING almost always means a previous "
        "run was interrupted (tab closed, app restarted) rather than something executing right now. "
        "**Pause** and **Cancel** update the job's recorded status; **Resume** re-runs the same "
        "backfill, which — thanks to checkpointing — only re-fetches what wasn't already completed."
    )

    stale = find_stale_running_jobs(handles, stale_after_minutes=30)
    if stale:
        st.warning(
            f"{len(stale)} job(s) have been RUNNING for over 30 minutes with no recent activity — "
            "these are likely from an interrupted session. Consider marking them Paused or Cancelled below."
        )

    jobs = list_job_history(handles)
    if not jobs:
        st.info("No warehouse jobs have been created yet. Start a backfill from the Historical Downloader tab.")
        return

    table_rows = [
        {
            "Job ID": j.job_id[:8],
            "Type": j.job_type,
            "Status": _STATUS_BADGE.get(j.status, j.status),
            "Created": j.created_at_utc.strftime("%Y-%m-%d %H:%M") if j.created_at_utc else "",
            "Finished": j.finished_at_utc.strftime("%Y-%m-%d %H:%M") if j.finished_at_utc else "—",
            "Error": (j.error_message or "")[:60],
        }
        for j in jobs
    ]
    st.dataframe(pd.DataFrame(table_rows), use_container_width=True, hide_index=True)

    st.markdown("---")
    st.markdown("**Job Actions**")

    job_lookup = {j.job_id: j for j in jobs}
    selected_short_id = st.selectbox(
        "Select a job (by ID) to act on",
        options=[j.job_id for j in jobs],
        format_func=lambda jid: f"{jid[:8]} — {job_lookup[jid].job_type} — {job_lookup[jid].status}",
    )
    job = job_lookup[selected_short_id]
    status = JobStatus(job.status)

    col1, col2, col3 = st.columns(3)

    with col1:
        if st.button("⏸️ Pause", disabled=(status != JobStatus.RUNNING), use_container_width=True):
            result = request_pause(handles, job.job_id)
            (st.success if result.success else st.error)(result.message)
            st.rerun()

    with col2:
        if st.button(
            "🚫 Cancel", disabled=(status not in (JobStatus.RUNNING, JobStatus.PENDING, JobStatus.PAUSED)),
            use_container_width=True,
        ):
            result = request_cancel(handles, job.job_id)
            (st.success if result.success else st.error)(result.message)
            st.rerun()

    with col3:
        if st.button("🔁 Restart (as new job)", disabled=(status != JobStatus.FAILED), use_container_width=True):
            new_job_id, result = restart_failed_job_as_new(handles, job)
            (st.success if result.success else st.error)(result.message)
            if new_job_id:
                st.rerun()

    if status in (JobStatus.PAUSED, JobStatus.RUNNING):
        plan = build_resume_plan(handles, job)
        if plan is not None:
            with st.expander("▶️ Resume this job"):
                st.write(f"**Instruments:** {len(plan.instrument_ids)}")
                st.write(f"**Timeframes:** {', '.join(t.value for t in plan.timeframes)}")
                if plan.start_date:
                    st.write(f"**Date range:** {plan.start_date} → {plan.end_date}")
                if plan.lookback_days:
                    st.write(f"**Lookback:** {plan.lookback_days} days")
                st.write(f"**Pending checkpoints:** {plan.pending_checkpoints}")
                st.caption(
                    "Go to the Historical Downloader tab and re-run with the same selections above — "
                    "already-completed chunks will be skipped automatically."
                )

"""
jarvis.interface.renderer

ResponseRenderer: pure formatting functions producing consistent,
human-readable console output. No side effects, no I/O — every method
returns a string; jarvis.interface.console decides when and how to print
it. Keeping rendering pure is what makes it trivially testable without
capturing stdout.
"""

from __future__ import annotations

from jarvis.approval.models import ApprovalDecision
from jarvis.intake.models import Task, TaskStatus
from jarvis.interface.approval_interface import ApprovalPrompt
from jarvis.kernel.kernel import KernelHealthReports


class ResponseRenderer:
    def render_task_result(self, task: Task) -> str:
        if task.status is TaskStatus.COMPLETED:
            return self.render_success(task)
        if task.status is TaskStatus.FAILED:
            return self.render_failure(task)
        if task.status is TaskStatus.WAITING_APPROVAL:
            return "Approval required — see prompt above."
        if task.status is TaskStatus.PLANNING:
            return self.render_warning(
                f"I need clarification: {task.intent.confidence_reason}"
            )
        return f"Task '{task.task_id}' is at status: {task.status.value}."

    def render_success(self, task: Task) -> str:
        result = task.metadata.get("execution_result", {})
        lines = [
            "SUCCESS",
            f"  {result.get('message', 'Completed.')}",
        ]
        evidence = result.get("evidence") or []
        if evidence:
            lines.append(f"  Evidence: {', '.join(evidence)}")
        return "\n".join(lines)

    def render_failure(self, task: Task) -> str:
        result = task.metadata.get("execution_result")
        permission = task.metadata.get("permission_decision")
        lines = ["FAILURE"]
        if result:
            lines.append(f"  {result.get('message', 'Execution failed.')}")
            errors = result.get("errors") or []
            if errors:
                lines.append(f"  Errors: {', '.join(errors)}")
        elif permission and not permission.get("allowed", True):
            lines.append(f"  Blocked: {permission.get('reason')}")
        else:
            lines.append("  Task failed before execution.")
        return "\n".join(lines)

    def render_warning(self, message: str) -> str:
        return f"WARNING\n  {message}"

    def render_approval_prompt(self, prompt: ApprovalPrompt) -> str:
        lines = [
            "APPROVAL REQUIRED",
            f"  Reason: {prompt.reason}",
            f"  Tier:   {prompt.tier.name}",
            f"  Risk:   {prompt.risk}",
        ]
        if prompt.requires_confirmation_phrase:
            lines.append(
                f"  This is a Tier 3 action. Type the exact phrase to confirm:\n"
                f"    {prompt.confirmation_phrase}"
            )
        else:
            lines.append("  Respond: yes / no / approve / reject / confirm / cancel")
        return "\n".join(lines)

    def render_approval_decision(self, decision: ApprovalDecision, task: Task) -> str:
        if decision.approved:
            header = "APPROVAL GRANTED"
            body = f"  Approved by {decision.approved_by}. Resuming workflow..."
        else:
            header = "APPROVAL REJECTED"
            body = f"  Rejected by {decision.approved_by}. Task will not execute."
        return f"{header}\n{body}"

    def render_audit_summary(self, entries) -> str:
        if not entries:
            return "AUDIT HISTORY\n  (no entries yet)"
        lines = ["AUDIT HISTORY (most recent last)"]
        for entry in entries:
            lines.append(f"  [{entry.timestamp}] {entry.event_type}: {entry.message}")
        return "\n".join(lines)

    def render_health(self, health: KernelHealthReports) -> str:
        overall = "HEALTHY" if health.healthy else "UNHEALTHY"
        parts = [
            f"OVERALL STATUS: {overall}",
            health.intake.summary(),
            health.execution.summary(),
            health.permission.summary(),
            health.approval.summary(),
            health.governance.summary(),
        ]
        return "\n\n".join(parts)

"""
jarvis.agents.engineering_agent

The Sprint-1B placeholder Engineering Agent.

Design reference: JARVIS-003 §6 (Engineering Agent — mission, boundaries).
This is explicitly a PLACEHOLDER per this sprint's scope: no repository
access, no GitHub calls, no static/runtime analysis. It exists to prove
the routing and execution lifecycle end-to-end with a real (if trivial)
agent, exactly as the acceptance scenario specifies.

SPRINT-1C/1D ADDITION: execute() now REQUIRES governance clearance —
per that sprint's explicit "Agent may never execute directly. Attempting
direct execution must fail" requirement. Clearance is represented by
`task.metadata["governance_cleared"] is True`, set ONLY by
jarvis.execution.workflow.TaskExecutionWorkflow after the task has
genuinely passed the Permission Engine and (if required) the Approval
Engine. A direct call to execute() on a task that never went through
that pipeline raises GovernanceViolationError — a distinct failure mode
from a normal capability mismatch (which still returns a FAILED
ExecutionResult, not an exception), because bypassing governance is a
security-relevant violation, not an ordinary business outcome.
"""

from __future__ import annotations

import time

from jarvis.agents.base import BaseAgent
from jarvis.agents.models import ExecutionResult, ExecutionStatus
from jarvis.intake.models import Task

CAPABILITIES: tuple[str, ...] = ("engineering", "repository-analysis", "code-review")


class GovernanceViolationError(Exception):
    """Raised when execute() is called on a task that was never cleared by Permission + Approval."""


class EngineeringAgent(BaseAgent):
    """
    Placeholder Engineering Agent.

    Declares the "engineering" domain and the three capabilities this
    sprint's brief specifies. `execute()` performs no real work — it
    validates governance clearance FIRST (Sprint-1C/1D), then that the
    task genuinely matches its capabilities (never trusting that the
    Router already checked, per BaseAgent.execute's contract), and
    returns a structured, honest ExecutionResult either way.
    """

    def __init__(self, agent_id: str = "engineering-agent-001") -> None:
        super().__init__(
            agent_id=agent_id,
            domain="engineering",
            capabilities=CAPABILITIES,
            version="0.1.0",
            display_name="Engineering Agent",
        )

    def execute(self, task: Task) -> ExecutionResult:
        if not task.metadata.get("governance_cleared"):
            raise GovernanceViolationError(
                f"EngineeringAgent '{self.agent_id}' refused to execute task "
                f"'{task.task_id}': it was not cleared by the Permission and "
                "Approval Engines. Agents may never execute directly — this is "
                "a constitutional violation (Article V), not a normal failure."
            )

        start = time.perf_counter()

        if not self.can_execute(task):
            elapsed = time.perf_counter() - start
            return ExecutionResult.new(
                status=ExecutionStatus.FAILED,
                message="Engineering Agent cannot execute this task — capability mismatch.",
                executed_by=self.agent_id,
                execution_time=elapsed,
                errors=("capability_mismatch",),
                metadata={"task_id": task.task_id},
            )

        # Placeholder execution: no repository access, no GitHub calls, no
        # static/runtime analysis — exactly as this sprint's scope requires.
        elapsed = time.perf_counter() - start
        return ExecutionResult.new(
            status=ExecutionStatus.SUCCESS,
            message="Engineering Agent placeholder executed successfully.",
            executed_by=self.agent_id,
            execution_time=elapsed,
            evidence=(
                "capability matched",
                "placeholder execution",
                "no external integrations used",
                "governance clearance verified",
            ),
            metadata={
                "task_id": task.task_id,
                "intent_type": task.intent.intent_type.value,
            },
        )

"""
jarvis.agents.engineering_agent

The Sprint-1B placeholder Engineering Agent.

Design reference: JARVIS-003 §6 (Engineering Agent — mission, boundaries).
This is explicitly a PLACEHOLDER per this sprint's scope: no repository
access, no GitHub calls, no static/runtime analysis. It exists to prove
the routing and execution lifecycle end-to-end with a real (if trivial)
agent, exactly as the acceptance scenario specifies.
"""

from __future__ import annotations

import time

from jarvis.agents.base import BaseAgent
from jarvis.agents.models import ExecutionResult, ExecutionStatus
from jarvis.intake.models import Task

CAPABILITIES: tuple[str, ...] = ("engineering", "repository-analysis", "code-review")


class EngineeringAgent(BaseAgent):
    """
    Placeholder Engineering Agent.

    Declares the "engineering" domain and the three capabilities this
    sprint's brief specifies. `execute()` performs no real work — it
    validates that the task genuinely matches its capabilities (never
    trusting that the Router already checked, per BaseAgent.execute's
    contract) and returns a structured, honest ExecutionResult either way.
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
            ),
            metadata={
                "task_id": task.task_id,
                "intent_type": task.intent.intent_type.value,
            },
        )

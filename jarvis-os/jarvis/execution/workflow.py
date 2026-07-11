"""
jarvis.execution.workflow

TaskExecutionWorkflow: drives a single READY_FOR_ROUTING Task through
routing (jarvis.routing.TaskRouter) and execution (the selected agent's
BaseAgent.execute()) to COMPLETED or FAILED, auditing every transition
per this sprint's explicit requirement.

Design reference: this sprint's Workflow Engine section — CREATED ->
PLANNING -> READY_FOR_ROUTING -> ROUTING -> EXECUTING -> COMPLETED/FAILED,
every transition audited.
"""

from __future__ import annotations

from jarvis.agents.models import ExecutionStatus
from jarvis.audit import AuditLedger
from jarvis.intake.models import Task, TaskStatus
from jarvis.logging_ import get_logger
from jarvis.registry import AgentRegistry
from jarvis.routing import RoutingStatus, TaskRouter

logger = get_logger(__name__)


class WorkflowError(Exception):
    """Raised when execute() is called on a Task not in a state this workflow can act on."""


class TaskExecutionWorkflow:
    """
    The Sprint-1B Workflow Engine.

    Mutates the given Task in place (status, metadata) and returns it —
    the same mutability pattern Task already uses for its Sprint-1A
    lifecycle progression (TaskPlanner does the same). `task.metadata`
    carries the RoutingDecision and ExecutionResult after this runs,
    stored as plain dicts (via their own to_dict()-equivalent shape)
    rather than adding new dataclass fields to Task — keeping Sprint-1A's
    Task model completely unmodified, per this sprint's compatibility
    requirement.
    """

    def __init__(self, router: TaskRouter, registry: AgentRegistry, audit_ledger: AuditLedger) -> None:
        self._router = router
        self._registry = registry
        self._audit_ledger = audit_ledger

    def execute(self, task: Task) -> Task:
        if task.status is not TaskStatus.READY_FOR_ROUTING:
            raise WorkflowError(
                f"Cannot execute task '{task.task_id}': status is "
                f"{task.status.value}, expected {TaskStatus.READY_FOR_ROUTING.value}. "
                "Per Article III, this workflow never guesses at routing a task "
                "that hasn't genuinely reached routing readiness."
            )

        task.status = TaskStatus.ROUTING
        self._audit_ledger.record(
            event_type="task.routing_started",
            message="Task routing started.",
            details={"task_id": task.task_id},
        )

        decision = self._router.route(task)
        task.metadata["routing_decision"] = {
            "status": decision.status.value,
            "selected_agent_id": decision.selected_agent_id,
            "candidate_agent_ids": list(decision.candidate_agent_ids),
            "reason": decision.reason,
        }

        if decision.status is RoutingStatus.NO_CAPABLE_AGENT:
            task.status = TaskStatus.FAILED
            logger.info("Execution Failed: task_id=%s reason=%r", task.task_id, decision.reason)
            self._audit_ledger.record(
                event_type="task.failed",
                message="Task failed: no capable agent available.",
                details={"task_id": task.task_id, "reason": decision.reason},
            )
            logger.info("Audit Updated: task_id=%s", task.task_id)
            return task

        agent_record = self._registry.get(decision.selected_agent_id)  # type: ignore[arg-type]

        task.status = TaskStatus.EXECUTING
        logger.info("Execution Started: task_id=%s agent_id=%s", task.task_id, agent_record.agent_id)
        self._audit_ledger.record(
            event_type="execution.started",
            message="Agent execution started.",
            details={"task_id": task.task_id, "agent_id": agent_record.agent_id},
        )

        result = agent_record.instance.execute(task)

        logger.info(
            "Placeholder Execution Complete: task_id=%s status=%s",
            task.task_id,
            result.status.value,
        )
        logger.info("Execution Result Generated: result_id=%s", result.result_id)
        self._audit_ledger.record(
            event_type="execution.finished",
            message="Agent execution finished.",
            details={
                "task_id": task.task_id,
                "result_id": result.result_id,
                "status": result.status.value,
                "evidence": list(result.evidence),
                "warnings": list(result.warnings),
                "errors": list(result.errors),
            },
        )

        task.metadata["execution_result"] = result.to_dict()

        if result.status is ExecutionStatus.SUCCESS:
            task.status = TaskStatus.COMPLETED
            logger.info("Task Completed: task_id=%s", task.task_id)
            self._audit_ledger.record(
                event_type="task.completed",
                message="Task completed successfully.",
                details={"task_id": task.task_id, "result_id": result.result_id},
            )
        else:
            task.status = TaskStatus.FAILED
            logger.info("Task Failed: task_id=%s", task.task_id)
            self._audit_ledger.record(
                event_type="task.failed",
                message="Task failed during execution.",
                details={"task_id": task.task_id, "result_id": result.result_id, "errors": list(result.errors)},
            )

        logger.info("Audit Updated: task_id=%s", task.task_id)
        return task

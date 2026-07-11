"""
jarvis.execution.workflow

TaskExecutionWorkflow: drives a single READY_FOR_ROUTING Task through
routing, permission checking, approval gating, and execution, to
COMPLETED or FAILED, auditing every transition.

SPRINT-1C/1D EXTENSION, sanctioned and required by this sprint's own
"Workflow Integration" section — unlike Sprint-1B's boundary around
Sprint-0's orchestrator stubs, this workflow (introduced in Sprint-1B) is
explicitly this sprint's subject to extend. The lifecycle is now:

    READY_FOR_ROUTING -> ROUTING -> PERMISSION_CHECK ->
        (WAITING_APPROVAL -> APPROVED) or (APPROVED directly, if no
        approval required) -> EXECUTING -> COMPLETED / FAILED

Two public entry points, deliberately split (see jarvis.approval.engine's
module docstring for why): `execute()` runs a task as far as it can go
without owner input — which may mean stopping at WAITING_APPROVAL, per
this sprint's Acceptance Scenarios 2 and 3 ("No execution occurs" /
"Execution blocked until explicit confirmation"). `resume()` continues a
WAITING_APPROVAL task once the owner has actually responded.
"""

from __future__ import annotations

from jarvis.agents.models import ExecutionStatus
from jarvis.approval import ApprovalEngine, ApprovalError, ApprovalStatus
from jarvis.audit import AuditLedger
from jarvis.intake.models import Task, TaskStatus
from jarvis.logging_ import get_logger
from jarvis.permission import PermissionEngine
from jarvis.registry import AgentRegistry
from jarvis.routing import RoutingStatus, TaskRouter

logger = get_logger(__name__)


class WorkflowError(Exception):
    """Raised when execute()/resume() is called on a Task not in a state this workflow can act on."""


class TaskExecutionWorkflow:
    """
    The Sprint-1B/1C/1D Workflow Engine.

    Mutates the given Task in place (status, metadata) and returns it —
    the same mutability pattern established in Sprint-1B. `task.metadata`
    accumulates the routing decision, permission decision, approval
    request/decision, and execution result as plain dicts, never as new
    Task dataclass fields — keeping Sprint-1A's Task model completely
    unmodified across three sprints now.
    """

    def __init__(
        self,
        router: TaskRouter,
        registry: AgentRegistry,
        audit_ledger: AuditLedger,
        permission_engine: PermissionEngine,
        approval_engine: ApprovalEngine,
    ) -> None:
        self._router = router
        self._registry = registry
        self._audit_ledger = audit_ledger
        self._permission_engine = permission_engine
        self._approval_engine = approval_engine

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

        return self._run_governance_and_execute(task, decision.selected_agent_id)  # type: ignore[arg-type]

    def resume(self, task: Task, approval_id: str, approved: bool, approved_by: str) -> Task:
        """
        Continue a task that stopped at WAITING_APPROVAL, once the owner
        has actually responded. Per Article V, this is the ONLY path by
        which a WAITING_APPROVAL task may proceed — there is no timeout-
        based or default-assumed approval anywhere in this class.
        """
        if task.status is not TaskStatus.WAITING_APPROVAL:
            raise WorkflowError(
                f"Cannot resume task '{task.task_id}': status is "
                f"{task.status.value}, expected {TaskStatus.WAITING_APPROVAL.value}."
            )

        try:
            approval_decision = self._approval_engine.confirm(approval_id, approved, approved_by)
        except ApprovalError as exc:
            task.status = TaskStatus.FAILED
            logger.info("Task Failed: task_id=%s reason=%r", task.task_id, str(exc))
            self._audit_ledger.record(
                event_type="task.failed",
                message=f"Approval could not be confirmed: {exc}",
                details={"task_id": task.task_id, "approval_id": approval_id},
            )
            return task

        task.metadata["approval_decision"] = {
            "approved": approval_decision.approved,
            "approved_by": approval_decision.approved_by,
            "confirmation_required": approval_decision.confirmation_required,
            "audit_reference": approval_decision.audit_reference,
        }

        if not approval_decision.approved:
            task.status = TaskStatus.FAILED
            logger.info("Task Failed: task_id=%s reason='approval rejected'", task.task_id)
            self._audit_ledger.record(
                event_type="task.failed",
                message="Task failed: approval was rejected.",
                details={"task_id": task.task_id, "approval_id": approval_id},
            )
            return task

        task.status = TaskStatus.APPROVED
        logger.info("Task Approved: task_id=%s approved_by=%s", task.task_id, approved_by)

        agent_id = task.metadata["routing_decision"]["selected_agent_id"]
        return self._execute_with_agent(task, agent_id)

    def _run_governance_and_execute(self, task: Task, agent_id: str) -> Task:
        task.status = TaskStatus.PERMISSION_CHECK
        logger.info("Permission Check Started: task_id=%s agent_id=%s", task.task_id, agent_id)

        permission_decision = self._permission_engine.evaluate(task, agent_id)
        task.metadata["permission_decision"] = {
            "allowed": permission_decision.allowed,
            "reason": permission_decision.reason,
            "required_approval": permission_decision.required_approval,
            "required_tier": permission_decision.required_tier.name if permission_decision.required_tier else None,
            "warnings": list(permission_decision.warnings),
        }

        if not permission_decision.allowed:
            task.status = TaskStatus.FAILED
            logger.info("Execution Blocked: task_id=%s reason=%r", task.task_id, permission_decision.reason)
            self._audit_ledger.record(
                event_type="task.failed",
                message=f"Execution blocked: {permission_decision.reason}",
                details={"task_id": task.task_id},
            )
            return task

        approval_request = self._approval_engine.evaluate(task, permission_decision)
        task.metadata["approval_request"] = {
            "approval_id": approval_request.approval_id,
            "status": approval_request.status.value,
            "reason": approval_request.reason,
            "expires_at": approval_request.expires_at,
        }

        if approval_request.status is ApprovalStatus.NOT_REQUIRED:
            task.status = TaskStatus.APPROVED
            logger.info("Approval Not Required: task_id=%s", task.task_id)
            return self._execute_with_agent(task, agent_id)

        # WAITING — stop here. No execution occurs until resume() is called
        # with an explicit owner decision (Acceptance Scenarios 2 and 3).
        task.status = TaskStatus.WAITING_APPROVAL
        logger.info(
            "Task Waiting Approval: task_id=%s approval_id=%s",
            task.task_id, approval_request.approval_id,
        )
        self._audit_ledger.record(
            event_type="task.waiting_approval",
            message="Task is waiting for owner approval.",
            details={"task_id": task.task_id, "approval_id": approval_request.approval_id},
        )
        logger.info("Audit Updated: task_id=%s", task.task_id)
        return task

    def _execute_with_agent(self, task: Task, agent_id: str) -> Task:
        agent_record = self._registry.get(agent_id)

        task.status = TaskStatus.EXECUTING
        logger.info("Execution Started: task_id=%s agent_id=%s", task.task_id, agent_record.agent_id)
        self._audit_ledger.record(
            event_type="execution.started",
            message="Agent execution started.",
            details={"task_id": task.task_id, "agent_id": agent_record.agent_id},
        )
        self._audit_ledger.record(
            event_type="execution.authorized",
            message="Execution authorized by governance pipeline.",
            details={"task_id": task.task_id, "agent_id": agent_record.agent_id},
        )

        # The ONLY place in the entire codebase that sets this marker —
        # per Sprint-1C/1D's "agent may never execute directly" requirement,
        # this is what makes governance clearance genuinely enforced rather
        # than merely documented.
        task.metadata["governance_cleared"] = True

        result = agent_record.instance.execute(task)

        logger.info(
            "Placeholder Execution Complete: task_id=%s status=%s",
            task.task_id, result.status.value,
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

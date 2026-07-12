"""
jarvis.kernel.kernel

ExecutiveKernel: wires together every prior sprint's pipeline component
and exposes exactly three operations to the Interface Layer:
submit_request(), resume(), and health_reports(). Nothing else in the
system (Registry, Router, Workflow, Permission/Approval Engines, the
Engineering Agent) is ever touched directly by anything in
jarvis.interface — this is what makes "never communicate directly with
agents" an enforced architectural property rather than a convention.
"""

from __future__ import annotations

from dataclasses import dataclass

from jarvis.agents.engineering_agent import EngineeringAgent
from jarvis.approval import ApprovalEngine
from jarvis.approval.health import ApprovalEngineHealth, run_approval_health_check
from jarvis.audit import AuditLedger
from jarvis.constitution import Constitution
from jarvis.execution import TaskExecutionWorkflow
from jarvis.execution.health import ExecutionLayerHealth, run_execution_health_check
from jarvis.governance import GovernanceLayerHealth, run_governance_health_check
from jarvis.intake import IntentProcessor, TaskPlanner
from jarvis.intake.health import IntakePipelineHealth, run_intake_health_check
from jarvis.intake.models import Task, TaskStatus
from jarvis.logging_ import get_logger
from jarvis.permission import PermissionEngine
from jarvis.permission.health import PermissionEngineHealth, run_permission_health_check
from jarvis.registry import AgentLifecycleState, AgentRecord, AgentRegistry

logger = get_logger(__name__)


@dataclass(frozen=True)
class KernelHealthReports:
    """Every health report the Kernel can produce, bundled for the Dashboard (Part 7)."""

    intake: IntakePipelineHealth
    execution: ExecutionLayerHealth
    permission: PermissionEngineHealth
    approval: ApprovalEngineHealth
    governance: GovernanceLayerHealth

    @property
    def healthy(self) -> bool:
        return (
            self.intake.healthy
            and self.execution.healthy
            and self.permission.healthy
            and self.approval.healthy
            and self.governance.healthy
        )


class ExecutiveKernel:
    """
    The Executive Kernel.

    Constructed once from an already-booted JarvisCore (Sprint-0). Builds
    and registers the placeholder Engineering Agent through its full
    JARVIS-002 §16 lifecycle exactly as every prior sprint's main.py did —
    that responsibility moves here now that main.py's job is to run the
    Interface Layer, not wire the pipeline by hand.
    """

    def __init__(self, constitution: Constitution, registry: AgentRegistry, audit_ledger: AuditLedger) -> None:
        self.constitution = constitution
        self.registry = registry
        self.audit_ledger = audit_ledger

        self.intent_processor = IntentProcessor(audit_ledger=audit_ledger)
        self.task_planner = TaskPlanner(audit_ledger=audit_ledger)

        self._register_engineering_agent()

        # Router import is local to avoid this module needing to know
        # about jarvis.routing at package-init time for anything other
        # than constructing it here — keeps the public import surface
        # (jarvis.kernel.ExecutiveKernel) minimal.
        from jarvis.routing import TaskRouter

        self.router = TaskRouter(registry=registry, audit_ledger=audit_ledger)
        self.permission_engine = PermissionEngine(registry=registry, audit_ledger=audit_ledger)
        self.approval_engine = ApprovalEngine(audit_ledger=audit_ledger)
        self.workflow = TaskExecutionWorkflow(
            router=self.router,
            registry=registry,
            audit_ledger=audit_ledger,
            permission_engine=self.permission_engine,
            approval_engine=self.approval_engine,
        )

    def _register_engineering_agent(self) -> None:
        agent = EngineeringAgent()
        self.registry.register(
            AgentRecord(
                agent_id=agent.agent_id,
                domain=agent.domain,
                parent_domain=None,
                capabilities=agent.capabilities(),
                version=agent.version(),
                instance=agent,
            )
        )
        self.registry.transition(agent.agent_id, AgentLifecycleState.REVIEWED)
        self.registry.transition(agent.agent_id, AgentLifecycleState.PROVISIONED)
        self.registry.transition(agent.agent_id, AgentLifecycleState.ACTIVE)

    def submit_request(self, raw_input: str) -> Task:
        """
        Run one owner input through Intake, then — if it reached
        READY_FOR_ROUTING — through Routing, Permission, Approval, and
        (if cleared) Execution. Returns the resulting Task in whatever
        state it landed: PLANNING (ambiguous), WAITING_APPROVAL,
        COMPLETED, or FAILED. Never raises for a normal governance
        outcome — every outcome this method can produce is a valid,
        representable Task state, per Article III.
        """
        intent = self.intent_processor.process(raw_input)
        task = self.task_planner.plan(intent)

        if task.status is not TaskStatus.READY_FOR_ROUTING:
            return task

        return self.workflow.execute(task)

    def resume(self, task: Task, approval_id: str, approved: bool, approved_by: str) -> Task:
        """
        Thin pass-through to TaskExecutionWorkflow.resume() — per this
        sprint's explicit "use existing ApprovalEngine.confirm(), resume
        existing Workflow, never create a new Task, never restart
        execution" requirement, this method adds no logic of its own,
        EXCEPT — SPRINT-3 — rehydrating the ApprovalEngine's record of
        this approval_id first, if this Kernel instance never saw it
        created (the task was restored from Session Memory after a
        restart, not routed through this process's own approval_engine.evaluate()).
        Without this, a restart-restored WAITING_APPROVAL task could
        never actually be confirmed, defeating Sprint-3's Acceptance
        Scenario 2.
        """
        self._rehydrate_approval_if_needed(task, approval_id)
        return self.workflow.resume(task, approval_id=approval_id, approved=approved, approved_by=approved_by)

    def _rehydrate_approval_if_needed(self, task: Task, approval_id: str) -> None:
        from jarvis.approval import ApprovalError
        from jarvis.approval.models import ApprovalRequest, ApprovalStatus

        try:
            self.approval_engine.get(approval_id)
            return  # already tracked by this process; nothing to do
        except ApprovalError:
            pass

        approval_meta = task.metadata.get("approval_request") or {}
        if approval_meta.get("approval_id") != approval_id:
            return  # nothing usable to rehydrate from; let resume() raise its own error

        request = ApprovalRequest(
            approval_id=approval_id,
            task_id=task.task_id,
            tier=task.tier,
            reason=approval_meta.get("reason", ""),
            status=ApprovalStatus(approval_meta.get("status", ApprovalStatus.WAITING.value)),
            created_at=task.created_at,
            expires_at=approval_meta.get("expires_at"),
        )
        self.approval_engine.register_existing(request)

    def health_reports(self) -> KernelHealthReports:
        return KernelHealthReports(
            intake=run_intake_health_check(self.intent_processor, self.task_planner),
            execution=run_execution_health_check(self.registry, self.router, self.workflow),
            permission=run_permission_health_check(self.permission_engine),
            approval=run_approval_health_check(self.approval_engine),
            governance=run_governance_health_check(self.permission_engine, self.approval_engine, self.workflow),
        )

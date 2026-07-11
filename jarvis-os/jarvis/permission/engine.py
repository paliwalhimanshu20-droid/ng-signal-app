"""
jarvis.permission.engine

PermissionEngine: evaluates every execution request against capability
validity, agent validity, and tier rules — independently of whatever the
Router already decided, per this codebase's recurring defense-in-depth
principle (BaseAgent.execute() already re-checks can_execute() rather
than trusting the Router; this is the same discipline one layer up).

Design reference: this sprint's explicit tier rules:
    Tier 0 — always permitted
    Tier 1 — capability validation
    Tier 2 — capability + owner approval required
    Tier 3 — capability + explicit approval + confirmation

"No shortcuts. No implicit approval." — this engine only ever GRANTS
`required_approval=False` for Tier 0 and Tier 1. Every Tier 2/3 decision
sets `required_approval=True`; the Approval Engine (jarvis.approval),
never this class, decides what satisfies that requirement.
"""

from __future__ import annotations

from jarvis.audit import AuditLedger
from jarvis.intake.models import Task
from jarvis.logging_ import get_logger
from jarvis.orchestrator.task_planner import Tier
from jarvis.permission.models import PermissionDecision, PermissionRequest
from jarvis.registry import AgentRegistry, RegistryError

logger = get_logger(__name__)

# Tiers that require owner approval before execution, per this sprint's
# explicit rules. Tier 0 and Tier 1 are absent — both are grantable on
# capability validation alone.
_APPROVAL_REQUIRED_TIERS: frozenset[Tier] = frozenset(
    {Tier.TIER_2_CONSEQUENTIAL_REVERSIBLE, Tier.TIER_3_IRREVERSIBLE_OR_HIGH_STAKES}
)


class PermissionEngine:
    """
    Evaluates a (Task, agent_id) pair into a PermissionDecision.

    Every call is audited twice: once for the request itself ("Permission
    Requested"), once for the outcome ("Permission Granted" or "Permission
    Denied") — per this sprint's explicit audit event list.
    """

    def __init__(self, registry: AgentRegistry, audit_ledger: AuditLedger) -> None:
        self._registry = registry
        self._audit_ledger = audit_ledger

    def evaluate(self, task: Task, agent_id: str) -> PermissionDecision:
        capability = self._resolve_capability(task, agent_id)
        request = PermissionRequest.new(
            task_id=task.task_id,
            agent_id=agent_id,
            requested_capability=capability or "unresolved",
            execution_tier=task.tier,
        )
        logger.info(
            "Permission Requested: task_id=%s agent_id=%s capability=%s tier=%s",
            task.task_id, agent_id, request.requested_capability, task.tier.name,
        )
        self._audit_ledger.record(
            event_type="permission.requested",
            message="Permission evaluation requested.",
            details={
                "request_id": request.request_id,
                "task_id": task.task_id,
                "agent_id": agent_id,
                "requested_capability": request.requested_capability,
                "execution_tier": task.tier.name,
            },
        )

        # --- Invalid agent -------------------------------------------------
        try:
            record = self._registry.get(agent_id)
        except RegistryError:
            return self._deny(request, f"Agent '{agent_id}' is not registered.")

        if not self._registry.is_available(agent_id):
            return self._deny(request, f"Agent '{agent_id}' is not ACTIVE and healthy.")

        # --- Unknown / unmatched capability ---------------------------------
        if capability is None or capability not in record.capabilities:
            return self._deny(
                request,
                f"Requested capability '{capability}' is not recognized for agent '{agent_id}'.",
            )

        # --- Tier rules ------------------------------------------------------
        required_approval = task.tier in _APPROVAL_REQUIRED_TIERS
        reason = (
            f"Capability '{capability}' validated for agent '{agent_id}' at {task.tier.name}."
        )
        return self._grant(request, reason=reason, required_approval=required_approval, tier=task.tier)

    def _resolve_capability(self, task: Task, agent_id: str) -> str | None:
        """
        Independently determine which capability this evaluation concerns
        — never trusted from the Router's own selection, computed fresh
        here from the Registry and the Task's own ExecutionPlan.
        """
        try:
            record = self._registry.get(agent_id)
        except RegistryError:
            return None

        plan = task.execution_plan
        if plan is None:
            return None

        overlap = [c for c in record.capabilities if c in plan.candidate_agents]
        if overlap:
            return overlap[0]
        if record.domain in plan.candidate_agents:
            return record.domain
        return None

    def _grant(self, request: PermissionRequest, reason: str, required_approval: bool, tier: Tier) -> PermissionDecision:
        decision = PermissionDecision.new(
            allowed=True,
            reason=reason,
            required_approval=required_approval,
            required_tier=tier,
        )
        logger.info(
            "Permission Granted: task_id=%s agent_id=%s required_approval=%s",
            request.task_id, request.agent_id, required_approval,
        )
        self._audit_ledger.record(
            event_type="permission.granted",
            message=reason,
            details={
                "decision_id": decision.decision_id,
                "task_id": request.task_id,
                "agent_id": request.agent_id,
                "required_approval": required_approval,
                "required_tier": tier.name,
            },
        )
        return decision

    def _deny(self, request: PermissionRequest, reason: str) -> PermissionDecision:
        decision = PermissionDecision.new(
            allowed=False,
            reason=reason,
            required_approval=False,
            required_tier=request.execution_tier,
        )
        logger.info("Permission Denied: task_id=%s agent_id=%s reason=%r", request.task_id, request.agent_id, reason)
        self._audit_ledger.record(
            event_type="permission.denied",
            message=reason,
            details={
                "decision_id": decision.decision_id,
                "task_id": request.task_id,
                "agent_id": request.agent_id,
            },
        )
        return decision

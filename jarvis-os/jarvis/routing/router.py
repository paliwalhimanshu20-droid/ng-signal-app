"""
jarvis.routing.router

TaskRouter: finds candidate agents for a Task, evaluates capability
match and health, and returns a structured RoutingDecision.

Design reference: JARVIS-001 §13, JARVIS-002 §17, this sprint's explicit
Router responsibilities (receive Task, find candidates, evaluate
capabilities, reject unhealthy agents, choose best candidate, return
routing decision — never fabricate an available agent).
"""

from __future__ import annotations

from jarvis.audit import AuditLedger
from jarvis.intake.models import Task
from jarvis.logging_ import get_logger
from jarvis.registry import AgentRegistry
from jarvis.routing.models import RoutingDecision, RoutingStatus

logger = get_logger(__name__)


class TaskRouter:
    """
    Routes a Task to a capable, healthy, ACTIVE agent.

    Selection rule when multiple candidates qualify: sorted by agent_id,
    first wins. This is a deliberately simple, fully deterministic
    placeholder — this sprint's brief asks for "choose best candidate"
    without specifying a ranking model, and JARVIS-002 §20's real Trust
    Model (which would be the principled ranking basis) is explicitly out
    of scope. Documented here, not hidden, so a future sprint replacing
    this with trust-tier-based selection knows exactly what it's
    replacing and why.
    """

    def __init__(self, registry: AgentRegistry, audit_ledger: AuditLedger) -> None:
        self._registry = registry
        self._audit_ledger = audit_ledger

    def route(self, task: Task) -> RoutingDecision:
        logger.info("Agent Search: task_id=%s", task.task_id)

        candidates = tuple(
            record for record in self._registry.discover_agents() if record.instance is not None
        )
        candidate_ids = tuple(record.agent_id for record in candidates)

        self._audit_ledger.record(
            event_type="task.candidate_search",
            message="Candidate agent search completed.",
            details={"task_id": task.task_id, "candidate_agent_ids": list(candidate_ids)},
        )

        capable = [record for record in candidates if record.instance.can_execute(task)]
        healthy_capable = [
            record for record in capable if self._registry.is_available(record.agent_id)
        ]

        if not healthy_capable:
            reason = self._build_failure_reason(candidates, capable)
            logger.info("No Capable Agent Found: task_id=%s reason=%r", task.task_id, reason)
            self._audit_ledger.record(
                event_type="routing.failed",
                message="No capable, healthy agent found for task.",
                details={"task_id": task.task_id, "reason": reason, "candidates": list(candidate_ids)},
            )
            return RoutingDecision(
                task_id=task.task_id,
                status=RoutingStatus.NO_CAPABLE_AGENT,
                selected_agent_id=None,
                candidate_agent_ids=candidate_ids,
                reason=reason,
            )

        chosen = sorted(healthy_capable, key=lambda record: record.agent_id)[0]
        display_name = chosen.instance.metadata().get("display_name", chosen.agent_id)
        logger.info("%s Selected: task_id=%s agent_id=%s", display_name, task.task_id, chosen.agent_id)

        self._audit_ledger.record(
            event_type="agent.selected",
            message=f"{display_name} selected for task.",
            details={
                "task_id": task.task_id,
                "agent_id": chosen.agent_id,
                "candidate_agent_ids": list(candidate_ids),
            },
        )

        return RoutingDecision(
            task_id=task.task_id,
            status=RoutingStatus.ROUTED,
            selected_agent_id=chosen.agent_id,
            candidate_agent_ids=candidate_ids,
            reason=f"{display_name} ({chosen.agent_id}) is capable and healthy.",
        )

    @staticmethod
    def _build_failure_reason(candidates: tuple, capable: list) -> str:
        if not candidates:
            return "No agents are registered and ACTIVE with a live instance attached."
        if not capable:
            return (
                f"{len(candidates)} active agent(s) considered, but none declared "
                "capabilities matching this task's execution plan."
            )
        return (
            f"{len(capable)} agent(s) matched this task's required capabilities, "
            "but none reported healthy."
        )

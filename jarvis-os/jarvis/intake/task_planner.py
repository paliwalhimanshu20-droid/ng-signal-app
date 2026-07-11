"""
jarvis.intake.task_planner

The Sprint-1A Task Planner: receives an Intent, validates it, determines
priority and tier, generates an ExecutionPlan, creates a Task, and audits
every step.

Design reference: JARVIS-001 §11 (Task Planning, including the max-tier
rollup rule this module's single-task scope doesn't yet need but stays
consistent with), JARVIS-002 §24 (Decision Framework — evidence-based,
never fabricated).

HONESTY NOTE on tier classification: because Sprint-1A has no agents
capable of consequential execution (Sprint-0's Agent Registry has zero
ACTIVE agents), no Task this module produces can ever actually cause a
Tier 2/3 consequence yet. Tier is still computed for real, from the same
kind of keyword evidence the Intent Processor uses — assigning a tier
that isn't grounded in *something* observable would be fabricating a
risk assessment, which Article III forbids regardless of whether the
assessed action can currently be executed. The tier this module computes
is forward-looking and advisory: it tells a future Approval Engine what
it would need to gate, once gating exists.
"""

from __future__ import annotations

from jarvis.audit import AuditLedger
from jarvis.intake.models import (
    ExecutionPlan,
    Intent,
    IntentType,
    Task,
    TaskPriority,
    TaskStatus,
)
from jarvis.logging_ import get_logger
from jarvis.orchestrator.task_planner import Tier

logger = get_logger(__name__)

# Keyword -> Tier evidence, per Tier definitions in JARVIS-001 §11.
# Scanned in descending severity order so the highest-tier match wins if
# an input happens to contain keywords from more than one list.
#
# SPRINT-2 CORRECTION: "deploy"/"production"/"release" were originally
# classified as Tier 3 (irreversible) in Sprint-1C/1D. This was a genuine
# misclassification, surfaced by this sprint's own acceptance scenarios:
# JARVIS-003 §8 (Deployment Agent) explicitly defines rollback as a core
# Deployment Agent capability — meaning a deployment is, by the
# Constitution's own definition, NOT irreversible. It belongs in Tier 2
# (consequential, reversible, approval required) alongside merge/commit/
# push, not Tier 3. Only genuinely irreversible actions (delete, buy,
# sell, trade, transfer) remain in Tier 3. This is a correction of a real
# defect, not a convenience change — see main.py's Acceptance Scenario 3
# (plain "yes" approval) vs. Scenario 4 (exact-phrase confirmation),
# which only both hold true under this corrected classification.
TIER_3_KEYWORDS: tuple[str, ...] = ("delete", "buy", "sell", "trade", "transfer")
TIER_2_KEYWORDS: tuple[str, ...] = ("merge", "commit", "push", "migrate", "update database", "deploy", "production", "release")
TIER_1_KEYWORDS: tuple[str, ...] = ("draft", "schedule", "create note")

URGENCY_KEYWORDS: tuple[str, ...] = ("urgent", "immediately", "asap", "right now")

# Candidate-agent domain hints, per JARVIS-003 Part I's named domains.
# Advisory only — see ExecutionPlan.candidate_agents docstring in models.py.
DOMAIN_HINTS: tuple[tuple[tuple[str, ...], tuple[str, ...]], ...] = (
    (("github", "repository", "repo", "pull request", "code"), ("engineering", "github")),
    (("deploy", "deployment"), ("engineering", "deployment")),
    (("database", "schema", "migration"), ("engineering", "database")),
    (("trade", "trading", "position", "natural gas"), ("research", "trading")),
    (("research", "market", "signal"), ("research",)),
)

_STRATEGY_TEMPLATES: dict[IntentType, tuple[str, int]] = {
    IntentType.ANALYZE: ("Single-pass read-only analysis.", 1),
    IntentType.INVESTIGATE: ("Evidence-gathering investigation (static and runtime evidence, per JARVIS-002 §25).", 2),
    IntentType.STATUS_CHECK: ("Status/health query.", 1),
    IntentType.RESEARCH: ("Research synthesis across available sources.", 2),
    IntentType.UNSUPPORTED: ("No execution strategy available — this capability is not implemented in the current scope.", 0),
    IntentType.UNKNOWN: ("Clarification required before a strategy can be generated.", 0),
}


class TaskPlanningError(Exception):
    """Raised when Task Planning receives a structurally invalid Intent."""


class TaskPlanner:
    """
    Turns a validated Intent into a Task with a generated ExecutionPlan.

    A Task whose Intent is ambiguous (is_ambiguous=True) is intentionally
    left at TaskStatus.PLANNING rather than advanced to READY_FOR_ROUTING
    — per Article III, this module never guesses missing information to
    push an under-specified request further along than the evidence
    actually supports. Everything else (including UNSUPPORTED intents,
    which are unambiguous — the system understood the request, it just
    can't act on it) reaches READY_FOR_ROUTING, honestly carrying a plan
    that says it can't be executed yet.
    """

    def __init__(self, audit_ledger: AuditLedger) -> None:
        self._audit_ledger = audit_ledger

    def plan(self, intent: Intent) -> Task:
        self._validate(intent)

        audit_entry = self._audit_ledger.record(
            event_type="task.created",
            message="Task created from a validated Intent.",
            details={"intent_id": intent.intent_id, "intent_type": intent.intent_type.value},
        )

        priority = self._determine_priority(intent)
        tier = self._determine_tier(intent)

        task = Task.new(
            intent=intent,
            priority=priority,
            tier=tier,
            audit_reference=audit_entry.entry_id,
        )
        logger.info(
            "Task Created: task_id=%s priority=%s tier=%s audit_reference=%s",
            task.task_id,
            priority.value,
            tier.name,
            task.audit_reference,
        )

        task.status = TaskStatus.PLANNING
        logger.info("Task Planning: task_id=%s status=%s", task.task_id, task.status.value)

        plan = self._generate_execution_plan(intent, tier)
        task.execution_plan = plan
        logger.info(
            "Execution Plan Generated: plan_id=%s strategy=%r estimated_steps=%d "
            "approval_required=%s approval_tier=%s candidate_agents=%s",
            plan.plan_id,
            plan.strategy,
            plan.estimated_steps,
            plan.approval_required,
            plan.approval_tier.name,
            plan.candidate_agents,
        )
        self._audit_ledger.record(
            event_type="execution_plan.generated",
            message="Execution plan generated for task.",
            details={
                "task_id": task.task_id,
                "plan_id": plan.plan_id,
                "strategy": plan.strategy,
                "estimated_steps": plan.estimated_steps,
                "approval_required": plan.approval_required,
                "approval_tier": plan.approval_tier.name,
                "candidate_agents": list(plan.candidate_agents),
            },
        )

        if intent.is_ambiguous:
            logger.info(
                "Task Held At Planning: task_id=%s reason='intent is ambiguous, "
                "clarification required before routing readiness'",
                task.task_id,
            )
            self._audit_ledger.record(
                event_type="task.held_at_planning",
                message="Task not advanced to READY_FOR_ROUTING: intent requires clarification.",
                details={"task_id": task.task_id, "intent_id": intent.intent_id},
            )
            return task

        task.status = TaskStatus.READY_FOR_ROUTING
        logger.info("Task Ready For Routing: task_id=%s", task.task_id)
        self._audit_ledger.record(
            event_type="task.ready_for_routing",
            message="Task reached READY_FOR_ROUTING.",
            details={"task_id": task.task_id},
        )
        return task

    @staticmethod
    def _validate(intent: Intent) -> None:
        if not isinstance(intent, Intent):
            raise TaskPlanningError(f"Expected an Intent instance, got {type(intent)!r}.")
        if not intent.raw_input or not intent.raw_input.strip():
            raise TaskPlanningError(
                "Cannot plan a Task from an Intent with empty raw_input. "
                "This is a structural validation failure, distinct from an "
                "ambiguous-but-well-formed Intent, which IS planned (see "
                "TaskPlanner.plan's handling of intent.is_ambiguous)."
            )

    @staticmethod
    def _determine_priority(intent: Intent) -> TaskPriority:
        if intent.intent_type == IntentType.UNSUPPORTED:
            return TaskPriority.LOW
        if intent.is_ambiguous:
            return TaskPriority.LOW
        lowered = intent.normalized_input.lower()
        if any(keyword in lowered for keyword in URGENCY_KEYWORDS):
            return TaskPriority.HIGH
        return TaskPriority.NORMAL

    @staticmethod
    def _determine_tier(intent: Intent) -> Tier:
        lowered = intent.normalized_input.lower()
        if any(keyword in lowered for keyword in TIER_3_KEYWORDS):
            return Tier.TIER_3_IRREVERSIBLE_OR_HIGH_STAKES
        if any(keyword in lowered for keyword in TIER_2_KEYWORDS):
            return Tier.TIER_2_CONSEQUENTIAL_REVERSIBLE
        if any(keyword in lowered for keyword in TIER_1_KEYWORDS):
            return Tier.TIER_1_REVERSIBLE_LOW_STAKES
        return Tier.TIER_0_INFORMATIONAL

    @staticmethod
    def _generate_execution_plan(intent: Intent, tier: Tier) -> ExecutionPlan:
        strategy, estimated_steps = _STRATEGY_TEMPLATES[intent.intent_type]
        candidate_agents = TaskPlanner._match_domain_hints(intent.normalized_input)
        return ExecutionPlan.new(
            strategy=strategy,
            estimated_steps=estimated_steps,
            approval_tier=tier,
            candidate_agents=candidate_agents,
        )

    @staticmethod
    def _match_domain_hints(normalized_input: str) -> tuple[str, ...]:
        lowered = normalized_input.lower()
        for keywords, domains in DOMAIN_HINTS:
            if any(keyword in lowered for keyword in keywords):
                return domains
        return ()

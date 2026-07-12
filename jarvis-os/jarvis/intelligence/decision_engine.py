"""
jarvis.intelligence.decision_engine

Sprint-4 Part 5 — Decision Engine.

Deterministically evaluates a Goal + Plan + Context and recommends one
of: Proceed, Ask Question, Request Approval, Reject, Escalate. This is
advisory output only — Article III/Sprint-1D precedent applies directly:
an agent's (here, the Intelligence Layer's) self-assessed decision is
input to any real governance gate (Permission/Approval Engines), never a
substitute for it. Nothing here calls those engines or executes anything;
Part 8's pipeline is explicit that the whole Reasoning Pipeline "Never
executes."
"""

from __future__ import annotations

from jarvis.audit import AuditLedger
from jarvis.intelligence.models import (
    Context,
    Decision,
    DecisionType,
    Goal,
    GoalCategory,
    Plan,
    RiskLevel,
    new_id,
    utc_now_iso,
)

# Same recognized-but-out-of-scope vocabulary spirit as
# jarvis.intake.intent_processor.UNSUPPORTED_KEYWORDS, kept separate
# (Intelligence Layer reasoning vs. Task Intake classification are
# different concerns, per models.GoalCategory's docstring) but serving
# the same honesty purpose: a request the system understands but will
# not recommend proceeding on gets an explicit REJECT, not a vague
# ESCALATE or a silent PROCEED.
_REJECT_KEYWORDS = ("delete production", "transfer money", "wire funds")

_LOW_CONFIDENCE_THRESHOLD = 0.4


class DecisionEngine:
    def __init__(self, audit_ledger: AuditLedger) -> None:
        self._audit = audit_ledger

    def decide(self, goal: Goal, plan: Plan, context: Context) -> Decision:
        missing_information = self._find_missing_information(goal, context)
        lowered = f"{goal.title} {goal.description}".lower()

        if any(kw in lowered for kw in _REJECT_KEYWORDS):
            decision = self._build(
                DecisionType.REJECT,
                reason="Goal text matches a recognized-but-out-of-scope, high-consequence action "
                "that this Intelligence Layer will not recommend proceeding on.",
                goal=goal,
                plan=plan,
                missing_information=missing_information,
            )
            return self._finalize(decision, goal)

        if goal.category is GoalCategory.GENERAL and goal.confidence < _LOW_CONFIDENCE_THRESHOLD:
            decision = self._build(
                DecisionType.ASK_QUESTION,
                reason=f"Goal classification confidence ({goal.confidence:.2f}) is below the threshold "
                f"for a general, uncategorized goal; clarification is needed before planning "
                f"can be trusted.",
                goal=goal,
                plan=plan,
                missing_information=missing_information,
            )
            return self._finalize(decision, goal)

        if plan.risk_assessment.level is RiskLevel.CRITICAL:
            decision = self._build(
                DecisionType.ESCALATE,
                reason="Plan risk level is CRITICAL; this exceeds what a deterministic recommendation "
                "should resolve on its own.",
                goal=goal,
                plan=plan,
                missing_information=missing_information,
            )
            return self._finalize(decision, goal)

        if plan.risk_assessment.level is RiskLevel.HIGH:
            decision = self._build(
                DecisionType.REQUEST_APPROVAL,
                reason="Plan risk level is HIGH; owner approval should be requested before any "
                "resulting action is taken.",
                goal=goal,
                plan=plan,
                missing_information=missing_information,
            )
            return self._finalize(decision, goal)

        if missing_information:
            decision = self._build(
                DecisionType.ASK_QUESTION,
                reason=f"Proceeding is otherwise reasonable, but required context is missing: "
                f"{list(missing_information)}.",
                goal=goal,
                plan=plan,
                missing_information=missing_information,
            )
            return self._finalize(decision, goal)

        decision = self._build(
            DecisionType.PROCEED,
            reason="No elevated risk, no missing required context, and goal classification "
            "confidence is adequate; recommending the plan proceed to specialist handoff.",
            goal=goal,
            plan=plan,
            missing_information=missing_information,
        )
        return self._finalize(decision, goal)

    @staticmethod
    def _find_missing_information(goal: Goal, context: Context) -> tuple[str, ...]:
        missing: list[str] = []
        if goal.category is GoalCategory.REVIEW and not context.current_task and not context.conversation_history:
            missing.append(
                "No current task or recent conversation history is available to review against."
            )
        if goal.confidence < 0.3:
            missing.append("Goal category confidence is very low; the underlying request may be misread.")
        return tuple(missing)

    @staticmethod
    def _build(
        decision_type: DecisionType,
        reason: str,
        goal: Goal,
        plan: Plan,
        missing_information: tuple[str, ...],
    ) -> Decision:
        return Decision(
            decision_id=new_id("decision"),
            decision_type=decision_type,
            reason=reason,
            confidence=goal.confidence,
            risk_level=plan.risk_assessment.level,
            missing_information=missing_information,
            created_at=utc_now_iso(),
        )

    def _finalize(self, decision: Decision, goal: Goal) -> Decision:
        self._audit.record(
            event_type="intelligence.decision_made",
            message=f"Decision '{decision.decision_type.value}' for goal '{goal.title}'.",
            details={
                "decision_id": decision.decision_id,
                "goal_id": goal.goal_id,
                "decision_type": decision.decision_type.value,
                "risk_level": decision.risk_level.value,
                "missing_information": list(decision.missing_information),
            },
        )
        return decision

    def is_healthy(self) -> bool:
        return True

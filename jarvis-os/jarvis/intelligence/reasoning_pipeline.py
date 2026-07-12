"""
jarvis.intelligence.reasoning_pipeline

Sprint-4 Part 8 — Reasoning Pipeline.

    Receive Task -> Build Context -> Analyze Goal -> Plan -> Risk Analysis
    -> Decision -> Specialist Selection -> Prompt Generation -> Return Recommendation

Every step always runs, in this fixed order, for every request — the
Decision step's OUTPUT (PROCEED/ASK_QUESTION/REQUEST_APPROVAL/REJECT/
ESCALATE) is informational content carried in the returned Recommendation,
never a branch that skips a later step. This is deliberate: since the
pipeline never executes anything regardless of the decision, there is no
"only build a prompt if X" condition worth adding — Article III's spirit
(never hide what the system actually did) is served better by always
producing the same shape of Recommendation and letting its `decision`
field carry the caveat, exactly as JARVIS-002 §24 requires a
recommendation's risk/approval fields to always be populated rather than
conditionally present.

"Risk Analysis" here re-examines the Plan's own risk assessment (Part 4)
against the fuller Context Planning didn't have (Part 4's PlanningEngine
only sees the Goal) — e.g. an unresolved pending approval already in
Working Memory is itself a reason to treat this request as higher-risk
than the Plan alone would suggest, since acting on a new goal while a
prior consequential action is still awaiting the owner's decision
compounds exposure rather than resolving it.
"""

from __future__ import annotations

from jarvis.audit import AuditLedger
from jarvis.intelligence.context_builder import ContextBuilder
from jarvis.intelligence.decision_engine import DecisionEngine
from jarvis.intelligence.goal_analyzer import classify
from jarvis.intelligence.goal_manager import GoalManager
from jarvis.intelligence.models import (
    Goal,
    Plan,
    Recommendation,
    RiskAssessment,
    RiskLevel,
    new_id,
    utc_now_iso,
)
from jarvis.intelligence.planning_engine import PlanningEngine
from jarvis.intelligence.prompt_builder import PromptBuilder
from jarvis.intelligence.specialist_coordinator import SpecialistCoordinator


class ReasoningPipeline:
    def __init__(
        self,
        context_builder: ContextBuilder,
        goal_manager: GoalManager,
        planning_engine: PlanningEngine,
        decision_engine: DecisionEngine,
        specialist_coordinator: SpecialistCoordinator,
        prompt_builder: PromptBuilder,
        audit_ledger: AuditLedger,
    ) -> None:
        self._context_builder = context_builder
        self._goal_manager = goal_manager
        self._planning_engine = planning_engine
        self._decision_engine = decision_engine
        self._specialist_coordinator = specialist_coordinator
        self._prompt_builder = prompt_builder
        self._audit = audit_ledger

    def run(self, raw_input: str) -> Recommendation:
        # Step 1: Receive Task
        normalized = " ".join(raw_input.split())
        self._audit.record(
            event_type="intelligence.request_received",
            message="Reasoning Pipeline received a request.",
            details={"raw_input": raw_input},
        )

        # Step 2: Build Context
        context = self._context_builder.build()

        # Step 3: Analyze Goal
        category, confidence, reason = classify(normalized)
        goal: Goal = self._goal_manager.create(
            title=normalized,
            description=f"Goal derived from owner request: {normalized!r}",
            category=category,
            confidence=confidence,
            confidence_reason=reason,
        )

        # Step 4: Plan
        plan: Plan = self._planning_engine.plan(goal)

        # Step 5: Risk Analysis (re-examines Plan's risk against fuller Context)
        plan = self._reanalyze_risk(plan, context)

        # Step 6: Decision
        decision = self._decision_engine.decide(goal, plan, context)

        # Step 7: Specialist Selection
        specialist, _specialist_reason = self._specialist_coordinator.select(normalized)

        # Step 8a: Prompt Generation
        prompt = self._prompt_builder.build(goal, context, plan)

        # Step 8b: Return Recommendation
        recommendation = Recommendation(
            recommendation_id=new_id("rec"),
            goal=goal,
            context=context,
            plan=plan,
            decision=decision,
            specialist=specialist,
            prompt=prompt,
            summary=self._summarize(goal, plan, decision, specialist),
            created_at=utc_now_iso(),
        )
        self._audit.record(
            event_type="intelligence.recommendation_returned",
            message="Reasoning Pipeline returned a recommendation.",
            details={
                "recommendation_id": recommendation.recommendation_id,
                "goal_id": goal.goal_id,
                "decision_type": decision.decision_type.value,
                "specialist": specialist.value,
            },
        )
        return recommendation

    @staticmethod
    def _reanalyze_risk(plan: Plan, context) -> Plan:
        if context.current_approval is None:
            return plan

        # A pending approval already exists — surface that as an
        # additional risk factor, never silently drop it. Only elevates,
        # never downgrades what Planning already determined (Part 5's
        # decision engine still has final say via risk_level).
        elevated_factors = plan.risk_assessment.factors + (
            "A pending approval already exists in Working Memory; a new goal is being "
            "reasoned about while a prior consequential action awaits the owner's decision.",
        )
        new_level = plan.risk_assessment.level
        if new_level in (RiskLevel.LOW, RiskLevel.MODERATE):
            new_level = RiskLevel.HIGH
        new_assessment = RiskAssessment(
            level=new_level,
            factors=elevated_factors,
            reason=f"Risk level '{new_level.value}' (elevated due to a pending approval already in flight).",
        )
        return Plan(
            plan_id=plan.plan_id,
            goal_id=plan.goal_id,
            tasks=plan.tasks,
            execution_order=plan.execution_order,
            risk_assessment=new_assessment,
            estimated_complexity=plan.estimated_complexity,
            estimated_duration=plan.estimated_duration,
            created_at=plan.created_at,
        )

    @staticmethod
    def _summarize(goal: Goal, plan: Plan, decision, specialist) -> str:
        return (
            f"Goal '{goal.title}' ({goal.category.value}) — {len(plan.tasks)} planned step(s), "
            f"risk {plan.risk_assessment.level.value}, estimated {plan.estimated_duration}. "
            f"Decision: {decision.decision_type.value}. Specialist: {specialist.value}."
        )

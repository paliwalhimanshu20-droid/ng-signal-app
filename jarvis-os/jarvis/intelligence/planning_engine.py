"""
jarvis.intelligence.planning_engine

Sprint-4 Part 4 — Planning Engine.

Given a Goal, deterministically generates a small, fixed task breakdown
per GoalCategory (a template, not a generated-from-nothing plan — this
is a planning FRAMEWORK, not a planning intelligence; a real per-domain
task decomposition is exactly the kind of thing a future AI integration
would improve on, per this sprint's own scope boundary). Every produced
Plan is inspectable and traceable to the template that produced it.

"Never execute. Only plan." — nothing in this module ever touches
jarvis.execution, jarvis.kernel, or any agent. It returns data.
"""

from __future__ import annotations

from jarvis.audit import AuditLedger
from jarvis.intelligence.models import (
    Goal,
    GoalCategory,
    Plan,
    PlanComplexity,
    PlannedTask,
    RiskAssessment,
    RiskLevel,
    new_id,
    utc_now_iso,
)

# Fixed per-category task templates. Each entry is a tuple of step
# titles, sequential by default (task N depends on task N-1) — real
# parallelism/branching is out of this sprint's scope (Part 4 asks for
# "Dependencies" and "Execution Order" to exist as concepts, not for a
# sophisticated DAG planner).
TASK_TEMPLATES: dict[GoalCategory, tuple[str, ...]] = {
    GoalCategory.REVIEW: (
        "Gather current state",
        "Identify key findings",
        "Summarize findings and open items",
    ),
    GoalCategory.BUILD: (
        "Clarify requirements and constraints",
        "Design the component's structure",
        "Implement the component",
        "Write tests",
        "Review against architecture requirements",
    ),
    GoalCategory.RESEARCH: (
        "Define evaluation criteria",
        "Identify candidate options",
        "Compare candidates against criteria",
        "Summarize findings and a recommendation",
    ),
    GoalCategory.INVESTIGATE: (
        "Reproduce or confirm the observed behavior",
        "Isolate the likely cause",
        "Propose a fix or next diagnostic step",
    ),
    GoalCategory.STATUS_CHECK: (
        "Collect current health/status signals",
        "Report status",
    ),
    GoalCategory.GENERAL: (
        "Clarify what is actually being requested",
    ),
}

# Categories whose template implies unreviewed, changeable, or
# irreversible-leaning work get a higher baseline risk than a pure
# read-only REVIEW/STATUS_CHECK/RESEARCH template.
_ELEVATED_RISK_CATEGORIES = frozenset({GoalCategory.BUILD})

_HIGH_RISK_KEYWORDS = ("production", "delete", "database", "deploy", "irreversible")


class PlanningEngine:
    def __init__(self, audit_ledger: AuditLedger) -> None:
        self._audit = audit_ledger

    def plan(self, goal: Goal) -> Plan:
        template = TASK_TEMPLATES[goal.category]
        task_ids = [new_id("ptask") for _ in template]
        tasks = tuple(
            PlannedTask(
                task_id=task_ids[index],
                title=title,
                depends_on=() if index == 0 else (task_ids[index - 1],),
                order=index,
            )
            for index, title in enumerate(template)
        )
        execution_order = tuple(t.task_id for t in sorted(tasks, key=lambda t: t.order))

        risk_assessment = self._assess_risk(goal, template)
        complexity = self._estimate_complexity(len(template), risk_assessment.level)
        duration = self._estimate_duration(complexity)

        plan = Plan(
            plan_id=new_id("plan"),
            goal_id=goal.goal_id,
            tasks=tasks,
            execution_order=execution_order,
            risk_assessment=risk_assessment,
            estimated_complexity=complexity,
            estimated_duration=duration,
            created_at=utc_now_iso(),
        )

        self._audit.record(
            event_type="intelligence.plan_generated",
            message=f"Plan generated for goal '{goal.title}'.",
            details={
                "plan_id": plan.plan_id,
                "goal_id": goal.goal_id,
                "task_count": len(tasks),
                "risk_level": risk_assessment.level.value,
                "estimated_complexity": complexity.value,
            },
        )
        return plan

    def _assess_risk(self, goal: Goal, template: tuple[str, ...]) -> RiskAssessment:
        factors: list[str] = []
        lowered = f"{goal.title} {goal.description}".lower()

        level = RiskLevel.LOW
        if goal.category in _ELEVATED_RISK_CATEGORIES:
            level = RiskLevel.MODERATE
            factors.append(f"Goal category '{goal.category.value}' involves creating or changing a component.")

        matched_high_risk = [kw for kw in _HIGH_RISK_KEYWORDS if kw in lowered]
        if matched_high_risk:
            level = RiskLevel.HIGH
            factors.append(f"Goal text matches high-risk keyword(s): {matched_high_risk}.")

        if goal.confidence < 0.5:
            factors.append(
                f"Goal classification confidence is low ({goal.confidence:.2f}); "
                "the plan may be based on a misread request."
            )

        if not factors:
            factors.append("No elevated-risk factors identified; goal appears read-only or informational.")

        reason = f"Risk level '{level.value}' based on: {'; '.join(factors)}"
        return RiskAssessment(level=level, factors=tuple(factors), reason=reason)

    @staticmethod
    def _estimate_complexity(task_count: int, risk_level: RiskLevel) -> PlanComplexity:
        if task_count <= 1:
            base = PlanComplexity.TRIVIAL
        elif task_count <= 2:
            base = PlanComplexity.LOW
        elif task_count <= 3:
            base = PlanComplexity.MODERATE
        elif task_count <= 4:
            base = PlanComplexity.HIGH
        else:
            base = PlanComplexity.VERY_HIGH

        order = [
            PlanComplexity.TRIVIAL,
            PlanComplexity.LOW,
            PlanComplexity.MODERATE,
            PlanComplexity.HIGH,
            PlanComplexity.VERY_HIGH,
        ]
        if risk_level in (RiskLevel.HIGH, RiskLevel.CRITICAL):
            idx = min(order.index(base) + 1, len(order) - 1)
            return order[idx]
        return base

    @staticmethod
    def _estimate_duration(complexity: PlanComplexity) -> str:
        return {
            PlanComplexity.TRIVIAL: "minutes",
            PlanComplexity.LOW: "under an hour",
            PlanComplexity.MODERATE: "a few hours",
            PlanComplexity.HIGH: "one or more sessions",
            PlanComplexity.VERY_HIGH: "multiple sessions, likely spanning days",
        }[complexity]

    def is_healthy(self) -> bool:
        return isinstance(TASK_TEMPLATES, dict) and len(TASK_TEMPLATES) > 0

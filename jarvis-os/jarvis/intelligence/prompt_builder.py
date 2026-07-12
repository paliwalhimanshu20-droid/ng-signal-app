"""
jarvis.intelligence.prompt_builder

Sprint-4 Part 7 — Prompt Builder.

Assembles a StructuredPrompt from Goal + Context + Memory + Constraints.
Purely a data-assembly step — no AI calls, no network calls, nothing
sent anywhere. The prompt is built FOR a future AI integration to
consume; this sprint only produces the structured input that
integration would need.
"""

from __future__ import annotations

from typing import Optional

from jarvis.audit import AuditLedger
from jarvis.intelligence.models import Context, Goal, Plan, StructuredPrompt, new_id, utc_now_iso


class PromptBuilder:
    def __init__(self, audit_ledger: AuditLedger) -> None:
        self._audit = audit_ledger

    def build(
        self,
        goal: Goal,
        context: Context,
        plan: Plan,
        constraints: Optional[tuple[str, ...]] = None,
    ) -> StructuredPrompt:
        memory_references = self._collect_memory_references(context)
        resolved_constraints = constraints or self._default_constraints(plan)

        prompt = StructuredPrompt(
            prompt_id=new_id("prompt"),
            goal_summary=f"{goal.title} — {goal.description}".strip(" —"),
            context_summary={
                "conversation_record_count": len(context.conversation_history),
                "has_current_task": context.current_task is not None,
                "has_pending_approval": context.current_approval is not None,
                "session_id": (context.session or {}).get("session_id"),
                "preference_count": len(context.preferences),
            },
            memory_references=memory_references,
            constraints=resolved_constraints,
            created_at=utc_now_iso(),
        )

        self._audit.record(
            event_type="intelligence.prompt_generated",
            message=f"Structured prompt generated for goal '{goal.title}'.",
            details={"prompt_id": prompt.prompt_id, "goal_id": goal.goal_id, "plan_id": plan.plan_id},
        )
        return prompt

    @staticmethod
    def _collect_memory_references(context: Context) -> tuple[str, ...]:
        refs: list[str] = []
        if context.current_task is not None:
            refs.append(f"working_memory.current_task={context.current_task.get('task_id', 'unknown')}")
        if context.session is not None:
            refs.append(f"session_memory.session_id={context.session.get('session_id', 'unknown')}")
        if context.conversation_history:
            refs.append(f"conversation_memory.recent_count={len(context.conversation_history)}")
        if context.preferences:
            refs.append(f"preference_memory.keys={sorted(context.preferences.keys())}")
        return tuple(refs)

    @staticmethod
    def _default_constraints(plan: Plan) -> tuple[str, ...]:
        constraints = [
            "Never execute any action directly; produce a recommendation only.",
            f"Risk level is '{plan.risk_assessment.level.value}' — reflect this in any proposed next step.",
        ]
        if plan.risk_assessment.level.value in ("high", "critical"):
            constraints.append("This goal requires explicit owner approval before any action is taken.")
        return tuple(constraints)

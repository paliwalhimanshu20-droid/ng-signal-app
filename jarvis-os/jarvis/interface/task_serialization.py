"""
jarvis.interface.task_serialization

Sprint-3 integration helper: Session Memory and Working Memory store
`current_task` as a plain dict (jarvis.memory has no knowledge of
jarvis.intake.models.Task — that would invert the dependency direction
every other memory field already respects, since Task is an Intake-layer
concept). This module is the one place that converts a live Task to that
dict shape and back, so the conversion logic isn't duplicated wherever
Session Memory gets written or restored (Interface Layer only).
"""

from __future__ import annotations

from dataclasses import asdict
from typing import Any, Optional

from jarvis.intake.models import ExecutionPlan, ExecutionPlanStatus, Intent, IntentType, Task, TaskPriority, TaskStatus
from jarvis.orchestrator.task_planner import Tier


def task_to_dict(task: Task) -> dict[str, Any]:
    """Convert a live Task to a plain, JSON-safe dict. Enums are stored as their `.value`; everything else is already JSON-safe (str/dict/list)."""
    data = asdict(task)
    data["status"] = task.status.value
    data["priority"] = task.priority.value
    data["tier"] = int(task.tier)
    data["intent"]["intent_type"] = task.intent.intent_type.value
    if task.execution_plan is not None:
        data["execution_plan"]["approval_tier"] = int(task.execution_plan.approval_tier)
        data["execution_plan"]["status"] = task.execution_plan.status.value
        data["execution_plan"]["candidate_agents"] = list(task.execution_plan.candidate_agents)
    return data


def task_from_dict(data: dict[str, Any]) -> Task:
    """Reconstruct a Task from the dict shape task_to_dict() produces. Raises KeyError/ValueError on malformed data — callers (Recovery) must treat that as a failed restore, never guess a Task's shape."""
    intent_data = dict(data["intent"])
    intent = Intent(
        intent_id=intent_data["intent_id"],
        raw_input=intent_data["raw_input"],
        normalized_input=intent_data["normalized_input"],
        intent_type=IntentType(intent_data["intent_type"]),
        confidence=intent_data["confidence"],
        confidence_reason=intent_data["confidence_reason"],
        detected_entities=intent_data["detected_entities"],
        is_ambiguous=intent_data["is_ambiguous"],
        requires_clarification=intent_data["requires_clarification"],
        timestamp=intent_data["timestamp"],
    )

    execution_plan: Optional[ExecutionPlan] = None
    if data.get("execution_plan") is not None:
        plan_data = dict(data["execution_plan"])
        execution_plan = ExecutionPlan(
            plan_id=plan_data["plan_id"],
            strategy=plan_data["strategy"],
            estimated_steps=plan_data["estimated_steps"],
            approval_required=plan_data["approval_required"],
            approval_tier=Tier(plan_data["approval_tier"]),
            candidate_agents=tuple(plan_data["candidate_agents"]),
            status=ExecutionPlanStatus(plan_data["status"]),
        )

    return Task(
        task_id=data["task_id"],
        created_at=data["created_at"],
        status=TaskStatus(data["status"]),
        priority=TaskPriority(data["priority"]),
        tier=Tier(data["tier"]),
        intent=intent,
        execution_plan=execution_plan,
        assigned_agent=data.get("assigned_agent"),
        audit_reference=data["audit_reference"],
        parent_task=data.get("parent_task"),
        child_tasks=list(data.get("child_tasks", [])),
        metadata=dict(data.get("metadata", {})),
    )

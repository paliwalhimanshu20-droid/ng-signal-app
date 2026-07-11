"""
jarvis.intake

Sprint-1A: Core Task Pipeline Foundation.

Transforms raw owner input into a validated, audited Task object ready
for routing. This package is deliberately NEW and SEPARATE from
jarvis.orchestrator (Sprint-0) — it does not modify, replace, or import
from jarvis.orchestrator.intent_processor or jarvis.orchestrator.task_planner,
both of which remain exactly as Sprint-0 left them (structural skeletons,
per JARVIS-001 §10/§11).

Why a separate package rather than "filling in" the Sprint-0 skeletons:
the Sprint-0 IntentProcessor/TaskPlanner are Orchestration Layer
components (JARVIS-001 §4) already wired into Orchestrator.health_check()
and the Bootstrap sequence — changing their behavior would be changing
Sprint-0 architecture, which this sprint's brief explicitly forbids.
jarvis.intake is a standalone pipeline Sprint-1A owns outright; wiring it
into the real Orchestrator (replacing the Sprint-0 stubs) is explicitly a
routing-adjacent decision left for a future sprint, consistent with this
sprint's "no routing" boundary.

Governing documents: JARVIS-001 §10 (Intent Processing), §11 (Task
Planning), Article III (Non-Fabrication — every confidence figure below
is deterministic, keyword-based, and documented as such; nothing here is
an LLM or a semantic understanding system, and this package never
pretends otherwise).
"""

from jarvis.intake.models import (
    ExecutionPlan,
    ExecutionPlanStatus,
    Intent,
    IntentType,
    Task,
    TaskPriority,
    TaskStatus,
)
from jarvis.intake.intent_processor import IntentProcessor
from jarvis.intake.task_planner import TaskPlanner, TaskPlanningError

__all__ = [
    "ExecutionPlan",
    "ExecutionPlanStatus",
    "Intent",
    "IntentProcessor",
    "IntentType",
    "Task",
    "TaskPlanner",
    "TaskPlanningError",
    "TaskPriority",
    "TaskStatus",
]

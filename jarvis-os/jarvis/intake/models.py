"""
jarvis.intake.models

Data models for Sprint-1A's Core Task Pipeline: Intent, ExecutionPlan, Task.

Design reference: JARVIS-001 §10 (Intent Processing — three mandatory
outputs: structured interpretation, confidence, explicit ambiguity flag),
JARVIS-001 §11 (Task Planning — tier classification, graph/task shape),
Article III (every confidence figure carries a mandatory, human-readable
explanation — enforced structurally below, not just by convention).

Tier is imported from jarvis.orchestrator.task_planner rather than
redefined here — Sprint-0 already established the four-tier model and its
ordering (JARVIS-001 §11); Sprint-1A reuses it rather than creating a
second, competing tier concept. This is a deliberate architectural
decision, not an oversight — see the Sprint-1A summary for the reasoning.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
from typing import Any, Optional
from uuid import uuid4

from jarvis.orchestrator.task_planner import Tier

__all__ = [
    "ExecutionPlan",
    "ExecutionPlanStatus",
    "Intent",
    "IntentType",
    "Task",
    "TaskPriority",
    "TaskStatus",
]


def _new_id(prefix: str) -> str:
    return f"{prefix}-{uuid4()}"


def _utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


class IntentType(str, Enum):
    """
    Sprint-1A's controlled intent vocabulary.

    Deliberately small and keyword-derived — there is no LLM or semantic
    model in this sprint (explicitly out of scope). Each value corresponds
    to a small, documented keyword list in intent_processor.py. UNKNOWN and
    UNSUPPORTED are first-class values, not error states — per Article
    III, a request the system genuinely cannot classify or cannot act on
    must be represented honestly, never forced into a best-guess category.
    """

    ANALYZE = "analyze"
    INVESTIGATE = "investigate"
    STATUS_CHECK = "status_check"
    RESEARCH = "research"
    UNSUPPORTED = "unsupported"
    UNKNOWN = "unknown"


class TaskStatus(str, Enum):
    """
    Task lifecycle states.

    CREATED, PLANNING, READY_FOR_ROUTING (Sprint-1A) and ROUTING,
    EXECUTING, COMPLETED, FAILED (Sprint-1B) are unchanged in name and
    string value. PERMISSION_CHECK, WAITING_APPROVAL, and APPROVED are
    SPRINT-1C/1D ADDITIONS, extending the lifecycle now that governance
    gating exists. Purely additive — no prior status was renamed, removed,
    or given a different string value.
    """

    CREATED = "created"
    PLANNING = "planning"
    READY_FOR_ROUTING = "ready_for_routing"
    ROUTING = "routing"
    PERMISSION_CHECK = "permission_check"
    WAITING_APPROVAL = "waiting_approval"
    APPROVED = "approved"
    EXECUTING = "executing"
    COMPLETED = "completed"
    FAILED = "failed"


class TaskPriority(str, Enum):
    """Scheduling urgency — deliberately distinct from Tier (JARVIS-001 §11), which measures approval stakes, not urgency."""

    LOW = "low"
    NORMAL = "normal"
    HIGH = "high"


class ExecutionPlanStatus(str, Enum):
    GENERATED = "generated"


@dataclass(frozen=True)
class Intent:
    """
    A single, fully-processed owner input.

    Frozen: an Intent is a point-in-time determination. If input needs
    reprocessing, a new Intent is produced — never mutated in place, which
    would blur the audit trail's "what did we actually conclude, and when"
    property (Article IV).

    Every field below is mandatory and always populated — there is no
    optional/None confidence or confidence_reason. Per Article III, "I
    don't know" is representable (IntentType.UNKNOWN, confidence near
    zero) but it is never *unstated*.
    """

    intent_id: str
    raw_input: str
    normalized_input: str
    intent_type: IntentType
    confidence: float
    confidence_reason: str
    detected_entities: dict[str, str]
    is_ambiguous: bool
    requires_clarification: bool
    timestamp: str

    @staticmethod
    def new(
        raw_input: str,
        normalized_input: str,
        intent_type: IntentType,
        confidence: float,
        confidence_reason: str,
        detected_entities: dict[str, str],
        is_ambiguous: bool,
        requires_clarification: bool,
    ) -> "Intent":
        return Intent(
            intent_id=_new_id("intent"),
            raw_input=raw_input,
            normalized_input=normalized_input,
            intent_type=intent_type,
            confidence=confidence,
            confidence_reason=confidence_reason,
            detected_entities=detected_entities,
            is_ambiguous=is_ambiguous,
            requires_clarification=requires_clarification,
            timestamp=_utc_now_iso(),
        )


@dataclass(frozen=True)
class ExecutionPlan:
    """
    Sprint-1A's plan output: WHAT approach would be taken and WHAT it
    would require — never routing, never execution.

    `candidate_agents` names JARVIS-003 Part I domain names the request
    plausibly belongs to. This is advisory metadata only: Sprint-0's
    Agent Registry currently has zero ACTIVE agents, so no candidate
    listed here is actually routable yet. Listing them anyway is honest,
    not presumptive — it documents the plan's own reasoning, the same way
    an agent's self-assessed risk level is advisory input to tier
    classification, never a substitute for it (JARVIS-002 §24).
    """

    plan_id: str
    strategy: str
    estimated_steps: int
    approval_required: bool
    approval_tier: Tier
    candidate_agents: tuple[str, ...]
    status: ExecutionPlanStatus

    @staticmethod
    def new(
        strategy: str,
        estimated_steps: int,
        approval_tier: Tier,
        candidate_agents: tuple[str, ...],
    ) -> "ExecutionPlan":
        return ExecutionPlan(
            plan_id=_new_id("plan"),
            strategy=strategy,
            estimated_steps=estimated_steps,
            approval_required=approval_tier >= Tier.TIER_2_CONSEQUENTIAL_REVERSIBLE,
            approval_tier=approval_tier,
            candidate_agents=candidate_agents,
            status=ExecutionPlanStatus.GENERATED,
        )


@dataclass
class Task:
    """
    A single unit of owner-requested work, from creation through
    readiness for routing.

    NOT frozen, unlike Intent/ExecutionPlan: a Task's status and
    execution_plan legitimately change as TaskPlanner progresses it
    through its lifecycle (CREATED -> PLANNING -> READY_FOR_ROUTING).
    This mirrors jarvis.registry.AgentRecord's same mutability choice for
    the same reason (JARVIS-002 §16's lifecycle applied to a different
    kind of record).

    `assigned_agent` is always None in Sprint-1A — this sprint produces
    no routing decision. `audit_reference` points to the Audit Ledger
    entry recording this Task's creation, giving every Task a traceable
    origin per Article IV.
    """

    task_id: str
    created_at: str
    status: TaskStatus
    priority: TaskPriority
    tier: Tier
    intent: Intent
    execution_plan: Optional[ExecutionPlan]
    assigned_agent: Optional[str]
    audit_reference: str
    parent_task: Optional[str]
    child_tasks: list[str]
    metadata: dict[str, Any] = field(default_factory=dict)

    @staticmethod
    def new(
        intent: Intent,
        priority: TaskPriority,
        tier: Tier,
        audit_reference: str,
        parent_task: Optional[str] = None,
    ) -> "Task":
        return Task(
            task_id=_new_id("task"),
            created_at=_utc_now_iso(),
            status=TaskStatus.CREATED,
            priority=priority,
            tier=tier,
            intent=intent,
            execution_plan=None,
            assigned_agent=None,
            audit_reference=audit_reference,
            parent_task=parent_task,
            child_tasks=[],
            metadata={},
        )

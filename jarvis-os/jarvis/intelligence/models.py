"""
jarvis.intelligence.models

Sprint-4 data models. Every field below is populated deterministically —
nothing in this module is ever filled from an AI call, per this sprint's
explicit "No AI model integration" requirement.

`TaskPriority` (goal scheduling urgency) is imported and reused directly
from jarvis.intake.models rather than redefined — same semantic meaning
(urgency, not approval stakes), same rationale Sprint-1A already gave for
keeping Tier and TaskPriority distinct concepts: reusing it here avoids
a second, competing "how urgent is this" enum (Engineering Requirement:
"No duplicate logic").
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
from typing import Any, Optional
from uuid import uuid4

from jarvis.intake.models import TaskPriority

__all__ = [
    "Context",
    "Decision",
    "DecisionType",
    "Goal",
    "GoalCategory",
    "GoalStatus",
    "Plan",
    "PlanComplexity",
    "PlannedTask",
    "Recommendation",
    "RiskAssessment",
    "RiskLevel",
    "SpecialistDomain",
    "StructuredPrompt",
    "TaskPriority",
]


def new_id(prefix: str) -> str:
    return f"{prefix}-{uuid4()}"


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


# --- Part 2: Context --------------------------------------------------------------


@dataclass(frozen=True)
class Context:
    """
    Part 2's single structured Context object, assembled ONLY from what
    ContextBuilder actually found in Memory (Sprint-3) — every field
    below is either the real value found, or an honest empty/None,
    never a guessed default. "Never fabricate context" is enforced by
    this being frozen and constructed in exactly one place
    (ContextBuilder.build()).
    """

    context_id: str
    built_at: str
    working_memory: dict[str, Any]
    conversation_history: tuple[dict[str, Any], ...]
    preferences: dict[str, Any]
    knowledge: dict[str, Any]
    session: Optional[dict[str, Any]]
    current_task: Optional[dict[str, Any]]
    current_workflow: Optional[dict[str, Any]]
    current_approval: Optional[dict[str, Any]]


# --- Part 3: Goal Manager --------------------------------------------------------


class GoalCategory(str, Enum):
    """
    Deterministic, keyword-derived categories a Goal can fall into.

    Deliberately a SEPARATE vocabulary from jarvis.intake.models.IntentType
    — Intent classifies a raw request for Task Intake/routing purposes
    (Sprint-1A's narrow scope: analyze/investigate/status_check/research);
    Goal classifies what the Intelligence Layer should actually reason
    about and plan toward, which needs categories Intake never had reason
    to define (BUILD, for instance — Intake has no "create/build" intent
    type because Sprint-1A never planned or executed anything). This is
    the same kind of intentional separation Sprint-1A already drew
    between TaskPriority and Tier — two concepts that sound similar but
    answer different questions — not an accidental duplication of Intent.
    """

    REVIEW = "review"
    BUILD = "build"
    RESEARCH = "research"
    INVESTIGATE = "investigate"
    STATUS_CHECK = "status_check"
    GENERAL = "general"


class GoalStatus(str, Enum):
    ACTIVE = "active"
    BLOCKED = "blocked"
    COMPLETED = "completed"
    CANCELLED = "cancelled"


@dataclass
class Goal:
    """
    Mutable by design (status, sub_goals, dependencies genuinely change
    over a Goal's life) — same justified exception to "prefer
    immutability" already established for Task, ApprovalRequest, and
    AgentRecord in prior sprints, applied here for the same reason: this
    generically represents a state machine over time.
    """

    goal_id: str
    title: str
    description: str
    category: GoalCategory
    status: GoalStatus
    priority: TaskPriority
    parent_goal: Optional[str]
    sub_goals: list[str]
    dependencies: list[str]
    created_at: str
    updated_at: str
    confidence: float
    confidence_reason: str

    @staticmethod
    def new(
        title: str,
        description: str,
        category: GoalCategory,
        confidence: float,
        confidence_reason: str,
        priority: TaskPriority = TaskPriority.NORMAL,
        parent_goal: Optional[str] = None,
    ) -> "Goal":
        now = utc_now_iso()
        return Goal(
            goal_id=new_id("goal"),
            title=title,
            description=description,
            category=category,
            status=GoalStatus.ACTIVE,
            priority=priority,
            parent_goal=parent_goal,
            sub_goals=[],
            dependencies=[],
            created_at=now,
            updated_at=now,
            confidence=confidence,
            confidence_reason=confidence_reason,
        )


# --- Part 4: Planning Engine ------------------------------------------------------


class PlanComplexity(str, Enum):
    TRIVIAL = "trivial"
    LOW = "low"
    MODERATE = "moderate"
    HIGH = "high"
    VERY_HIGH = "very_high"


class RiskLevel(str, Enum):
    LOW = "low"
    MODERATE = "moderate"
    HIGH = "high"
    CRITICAL = "critical"


@dataclass(frozen=True)
class RiskAssessment:
    level: RiskLevel
    factors: tuple[str, ...]
    reason: str


@dataclass(frozen=True)
class PlannedTask:
    """One step of a Plan. `depends_on` holds other PlannedTask.task_id values within the SAME Plan — never a live jarvis.intake.models.Task, since Part 4 is explicit: 'Never execute. Only plan.'"""

    task_id: str
    title: str
    depends_on: tuple[str, ...]
    order: int


@dataclass(frozen=True)
class Plan:
    plan_id: str
    goal_id: str
    tasks: tuple[PlannedTask, ...]
    execution_order: tuple[str, ...]
    risk_assessment: RiskAssessment
    estimated_complexity: PlanComplexity
    estimated_duration: str
    created_at: str


# --- Part 5: Decision Engine -------------------------------------------------------


class DecisionType(str, Enum):
    PROCEED = "proceed"
    ASK_QUESTION = "ask_question"
    REQUEST_APPROVAL = "request_approval"
    REJECT = "reject"
    ESCALATE = "escalate"


@dataclass(frozen=True)
class Decision:
    decision_id: str
    decision_type: DecisionType
    reason: str
    confidence: float
    risk_level: RiskLevel
    missing_information: tuple[str, ...]
    created_at: str


# --- Part 6: Specialist Coordinator -------------------------------------------------


class SpecialistDomain(str, Enum):
    ENGINEERING = "engineering"
    RESEARCH = "research"
    TRADING = "trading"
    CALENDAR = "calendar"
    GITHUB = "github"
    PROJECTOS = "projectos"
    GENERAL = "general"


# --- Part 7: Prompt Builder ---------------------------------------------------------


@dataclass(frozen=True)
class StructuredPrompt:
    """
    Output of the Prompt Builder — a structured artifact FOR a future AI
    system to consume, never itself sent anywhere. Part 7 is explicit:
    "No AI calls." This dataclass exists so that whichever future sprint
    adds a real model integration has a stable, already-audited input
    shape to build against, rather than inventing prompt assembly at the
    same time it wires up the first real API call.
    """

    prompt_id: str
    goal_summary: str
    context_summary: dict[str, Any]
    memory_references: tuple[str, ...]
    constraints: tuple[str, ...]
    created_at: str


# --- Recommendation: the pipeline's final output (Part 8) --------------------------


@dataclass(frozen=True)
class Recommendation:
    recommendation_id: str
    goal: Goal
    context: Context
    plan: Plan
    decision: Decision
    specialist: SpecialistDomain
    prompt: StructuredPrompt
    summary: str
    created_at: str

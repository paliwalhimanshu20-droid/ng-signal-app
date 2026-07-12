"""
jarvis.ai_coordination.models

Sprint-5 data models. Every field below is either fixed metadata (never
a live credential or endpoint — see ProviderMetadata's docstring) or
produced deterministically. No field, type, or default anywhere in this
module makes an HTTP call, imports an SDK, or holds a secret.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
from typing import Any, Optional
from uuid import uuid4

from jarvis.intelligence.models import RiskLevel, StructuredPrompt, TaskPriority

__all__ = [
    "Capability",
    "ConflictResolution",
    "ConflictResolutionType",
    "ConsensusResult",
    "ConsensusStatus",
    "ConversationRecord",
    "Exchange",
    "ProviderAvailability",
    "ProviderHealth",
    "ProviderMetadata",
    "ProviderRequest",
    "ProviderResponse",
    "AIRecommendation",
    "AISession",
    "AISessionStatus",
    "RiskLevel",
    "TaskPriority",
    "ValidationFlag",
    "ValidationResult",
]


def new_id(prefix: str) -> str:
    return f"{prefix}-{uuid4()}"


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


# --- Part 2: Capability Registry --------------------------------------------------


class Capability(str, Enum):
    """
    Part 2's fixed capability vocabulary. "Future capabilities easily
    extendable" is honored by CapabilityRegistry's data-driven metadata
    (description, notes) being separate from this enum — adding a new
    capability is a one-line enum addition plus a registry entry, never
    a change to any provider-selection or dispatch logic, which only
    ever reasons about Capability values generically.
    """

    ARCHITECTURE = "architecture"
    IMPLEMENTATION = "implementation"
    RESEARCH = "research"
    PLANNING = "planning"
    REVIEW = "review"
    TESTING = "testing"
    DOCUMENTATION = "documentation"
    DEBUGGING = "debugging"
    OPTIMIZATION = "optimization"
    TRANSLATION = "translation"


# --- Part 3: Model Registry ---------------------------------------------------------


class ProviderAvailability(str, Enum):
    AVAILABLE = "available"
    DEGRADED = "degraded"
    UNAVAILABLE = "unavailable"


class ProviderHealth(str, Enum):
    HEALTHY = "healthy"
    DEGRADED = "degraded"
    UNHEALTHY = "unhealthy"


@dataclass(frozen=True)
class ProviderMetadata:
    """
    METADATA ONLY, per Part 3's explicit instruction. There is no field
    here — nor may one ever be added to this dataclass — capable of
    holding a credential, endpoint URL, or SDK client: no `api_key`, no
    `base_url`, no `client`. This is structurally, not just currently,
    an inert description of a provider a future sprint could integrate,
    never something this sprint (or an accidental future misuse of this
    exact class) could use to actually reach one.
    """

    provider_id: str
    provider_name: str
    supported_capabilities: tuple[Capability, ...]
    priority: int  # lower number = preferred; deterministic tie-break input, never randomness
    estimated_cost: float  # abstract relative unit, not a real price
    estimated_latency_ms: int
    availability: ProviderAvailability
    version: str
    health: ProviderHealth


# --- Part 4: Model Selector ------------------------------------------------------


# --- Part 5: AI Session Manager --------------------------------------------------


class AISessionStatus(str, Enum):
    CREATED = "created"
    PROMPT_PREPARED = "prompt_prepared"
    AWAITING_RESPONSE = "awaiting_response"
    RESPONDED = "responded"
    RETRYING = "retrying"
    FAILED = "failed"
    COMPLETED = "completed"


@dataclass
class AISession:
    """
    Mutable by design (status, retry_count, response genuinely change
    over the session's life) — same justified exception already
    established for Task, ApprovalRequest, AgentRecord, and Goal.

    "Future-ready for persistent conversations": `conversation_id`
    groups sessions that belong to the same ongoing exchange, exactly
    the key a future ConversationMemory-style persistence layer (Sprint-3
    pattern) would need — this sprint does not persist sessions (see
    module-level scope note in ai_coordinator.py), but the grouping key
    already exists so that future addition is additive, not a redesign.
    """

    session_id: str
    conversation_id: str
    provider_id: Optional[str]
    prompt: StructuredPrompt
    response: Optional["ProviderResponse"]
    context: dict[str, Any]
    retry_count: int
    status: AISessionStatus
    created_at: str
    updated_at: str


# --- Part 6: Conversation Manager -----------------------------------------------


@dataclass(frozen=True)
class Exchange:
    """One prompt/response pairing within a conversation's history."""

    exchange_id: str
    session_id: str
    prompt_id: str
    response_id: Optional[str]
    timestamp: str


@dataclass
class ConversationRecord:
    conversation_id: str
    parent_request: Optional[str]
    exchanges: list[Exchange]
    created_at: str
    updated_at: str


# --- Part 7: Prompt Dispatcher --------------------------------------------------


@dataclass(frozen=True)
class ProviderRequest:
    """
    A provider-NEUTRAL request — deliberately shaped so it names no
    specific provider's API format. Preparing this is the entirety of
    Part 7's scope: "Do NOT call external APIs" applies to this dataclass
    as much as to the code that builds it — nothing about this class can
    be sent anywhere without a future sprint writing an actual adapter.
    """

    request_id: str
    session_id: str
    capability: Capability
    prompt_summary: str
    context_references: tuple[str, ...]
    constraints: tuple[str, ...]
    prepared_at: str


# --- Part 8: Response Validator --------------------------------------------------


class ValidationFlag(str, Enum):
    INVALID_STRUCTURE = "invalid_structure"
    INCOMPLETE = "incomplete"
    LOW_CONFIDENCE = "low_confidence"
    INCONSISTENT = "inconsistent"


@dataclass(frozen=True)
class ProviderResponse:
    """
    The shape a real provider adapter would eventually need to produce —
    this sprint never constructs one from a live call; every instance in
    this codebase (tests, future integration scaffolding) is either
    hand-built or, eventually, produced by a Sprint-6+ adapter this
    sprint does not implement.
    """

    response_id: str
    provider_id: str
    request_id: str
    recommended_action: str
    content: str
    confidence: float
    completeness: float  # 0.0-1.0, self-reported by the (future) provider adapter
    structured_fields: dict[str, Any]
    created_at: str


@dataclass(frozen=True)
class ValidationResult:
    response_id: str
    valid: bool
    flags: tuple[ValidationFlag, ...]
    reason: str


# --- Part 9: Consensus Engine -----------------------------------------------------


class ConsensusStatus(str, Enum):
    AGREEMENT = "agreement"
    CONFLICT = "conflict"
    INSUFFICIENT_RESPONSES = "insufficient_responses"


@dataclass(frozen=True)
class ConsensusResult:
    consensus_id: str
    status: ConsensusStatus
    agreed_action: Optional[str]
    confidence: float
    contributing_provider_ids: tuple[str, ...]
    dissenting_provider_ids: tuple[str, ...]
    reason: str
    created_at: str


# --- Part 10: Conflict Resolver ---------------------------------------------------


class ConflictResolutionType(str, Enum):
    RETRY = "retry"
    ESCALATE = "escalate"
    ASK_OWNER = "ask_owner"
    REJECT = "reject"


@dataclass(frozen=True)
class ConflictResolution:
    resolution_id: str
    resolution_type: ConflictResolutionType
    reason: str
    created_at: str


# --- Recommendation: AICoordinator's final output (Part 11) ------------------------


@dataclass(frozen=True)
class AIRecommendation:
    recommendation_id: str
    capability: Capability
    selected_provider: Optional[ProviderMetadata]
    session_id: str
    provider_request: Optional[ProviderRequest]
    responses: tuple[ProviderResponse, ...]
    validation_results: tuple[ValidationResult, ...]
    consensus_result: Optional[ConsensusResult]
    conflict_resolution: Optional[ConflictResolution]
    status: str
    summary: str
    created_at: str

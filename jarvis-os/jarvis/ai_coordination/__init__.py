"""
jarvis.ai_coordination

Sprint-5: the AI Coordination Layer. Public surface is deliberately
narrow — AICoordinator plus the read-only data types other layers need
to call it and read its output. Every other name in this package is an
internal collaborator of AICoordinator and must never be imported or
constructed outside jarvis.ai_coordination (Part 1: "Single entry
point").

JARVIS never talks directly about ChatGPT, Claude, or Gemini anywhere in
this package — see models.ProviderMetadata's docstring for the
structural (not just conventional) guarantee behind that.
"""

from __future__ import annotations

from jarvis.ai_coordination.ai_coordinator import AICoordinator
from jarvis.ai_coordination.health import AICoordinationHealthReport, run_ai_coordination_health_check
from jarvis.ai_coordination.models import (
    AIRecommendation,
    AISession,
    AISessionStatus,
    Capability,
    ConflictResolution,
    ConflictResolutionType,
    ConsensusResult,
    ConsensusStatus,
    ConversationRecord,
    Exchange,
    ProviderAvailability,
    ProviderHealth,
    ProviderMetadata,
    ProviderRequest,
    ProviderResponse,
    ValidationFlag,
    ValidationResult,
)

__all__ = [
    "AICoordinationHealthReport",
    "AICoordinator",
    "AIRecommendation",
    "AISession",
    "AISessionStatus",
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
    "ValidationFlag",
    "ValidationResult",
    "run_ai_coordination_health_check",
]

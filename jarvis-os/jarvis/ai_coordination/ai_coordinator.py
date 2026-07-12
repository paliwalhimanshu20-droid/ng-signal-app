"""
jarvis.ai_coordination.ai_coordinator

Sprint-5 Part 1 & Part 11 — AI Coordinator and Coordination Pipeline.

AICoordinator is the ONLY entry point for AI coordination — every other
component in this package is an internal collaborator, constructed and
owned here, never imported or constructed outside jarvis.ai_coordination.
Same "sole entry point" rule Sprint-3's MemoryManager and Sprint-4's
IntelligenceEngine already established for their own layers.

Two public operations, matching what this sprint can actually DO without
a real provider integration (which is explicitly out of scope):

  coordinate(prompt, risk, priority, context) -> AIRecommendation
      Runs the pipeline as far as Part 11 allows without a live provider:
      Capability Selection -> Provider Selection -> Session Creation ->
      Prompt Dispatch. Returns a recommendation describing what WOULD be
      sent and to whom, with no response yet — because no external call
      is made, there is nothing to validate or reach consensus over.

  finalize_with_responses(session_id, responses, risk) -> AIRecommendation
      The remainder of the pipeline: Response Validation -> Consensus ->
      (Conflict Resolution if needed) -> Recommendation. Takes
      already-assembled ProviderResponse objects — supplied by tests
      today, and by a future real provider adapter eventually — since
      Part 9 is explicit that the Consensus Engine itself "does NOT call
      providers."
"""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Any, Optional
from uuid import uuid4

from jarvis.audit import AuditLedger
from jarvis.ai_coordination.capability_classifier import classify as classify_capability
from jarvis.ai_coordination.capability_registry import CapabilityRegistry
from jarvis.ai_coordination.conflict_resolver import ConflictResolver
from jarvis.ai_coordination.consensus_engine import ConsensusEngine
from jarvis.ai_coordination.conversation_manager import ConversationManager
from jarvis.ai_coordination.health import AICoordinationHealthReport, run_ai_coordination_health_check
from jarvis.ai_coordination.model_registry import ModelRegistry
from jarvis.ai_coordination.model_selector import ModelSelector
from jarvis.ai_coordination.models import (
    AIRecommendation,
    AISessionStatus,
    ConsensusStatus,
    ProviderResponse,
    RiskLevel,
    TaskPriority,
)
from jarvis.ai_coordination.prompt_dispatcher import PromptDispatcher
from jarvis.ai_coordination.response_validator import ResponseValidator
from jarvis.ai_coordination.session_manager import AISessionManager
from jarvis.intelligence.models import StructuredPrompt

__all__ = ["AICoordinator"]


def _new_id(prefix: str) -> str:
    return f"{prefix}-{uuid4()}"


def _utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


class AICoordinator:
    def __init__(self, audit_ledger: AuditLedger, seed_example_providers: bool = True) -> None:
        self._audit = audit_ledger

        self.capability_registry = CapabilityRegistry()
        self.model_registry = ModelRegistry(seed_examples=seed_example_providers)
        self.model_selector = ModelSelector(model_registry=self.model_registry)
        self.session_manager = AISessionManager(audit_ledger=audit_ledger)
        self.conversation_manager = ConversationManager(audit_ledger=audit_ledger)
        self.prompt_dispatcher = PromptDispatcher(audit_ledger=audit_ledger)
        self.response_validator = ResponseValidator(audit_ledger=audit_ledger)
        self.consensus_engine = ConsensusEngine(audit_ledger=audit_ledger)
        self.conflict_resolver = ConflictResolver(audit_ledger=audit_ledger)

    def coordinate(
        self,
        prompt: StructuredPrompt,
        risk: RiskLevel,
        priority: TaskPriority,
        context: Optional[dict[str, Any]] = None,
        conversation_id: Optional[str] = None,
    ) -> AIRecommendation:
        """Runs the pipeline up through Prompt Dispatch. No external call is made — see module docstring."""
        # Step: Receive Structured Prompt (implicit — it's the argument itself)

        # Step: Capability Selection
        capability, cap_confidence, cap_reason = classify_capability(prompt.goal_summary)
        self._audit.record(
            event_type="ai_coordination.capability_selected",
            message=cap_reason,
            details={"capability": capability.value, "confidence": cap_confidence},
        )

        # Step: Provider Selection
        provider = self.model_selector.select(capability=capability, risk=risk, priority=priority, context=context)
        self._audit.record(
            event_type="ai_coordination.provider_selected",
            message=(
                f"Selected provider '{provider.provider_id}'." if provider is not None
                else f"No provider available for capability '{capability.value}' at risk '{risk.value}'."
            ),
            details={"capability": capability.value, "provider_id": provider.provider_id if provider else None},
        )

        # Step: Session Creation
        conversation = self.conversation_manager.start(conversation_id or _new_id("conversation"))
        session = self.session_manager.create_session(
            prompt=prompt, conversation_id=conversation.conversation_id, context=context
        )
        if provider is not None:
            self.session_manager.assign_provider(session.session_id, provider.provider_id)

        if provider is None:
            self.session_manager.fail(session.session_id, "No eligible provider found for this capability/risk combination.")
            recommendation = self._build_recommendation(
                capability=capability,
                provider=None,
                session_id=session.session_id,
                provider_request=None,
                responses=(),
                validation_results=(),
                consensus_result=None,
                conflict_resolution=None,
                status="no_provider_available",
                summary=f"No provider is currently available for capability '{capability.value}' at risk '{risk.value}'.",
            )
            return recommendation

        # Step: Prompt Dispatch (provider-neutral request only — no external call)
        provider_request = self.prompt_dispatcher.prepare(prompt, session.session_id, capability)
        self.conversation_manager.add_exchange(
            conversation.conversation_id, session.session_id, provider_request.request_id
        )
        self.session_manager.set_status(session.session_id, AISessionStatus.PROMPT_PREPARED)

        recommendation = self._build_recommendation(
            capability=capability,
            provider=provider,
            session_id=session.session_id,
            provider_request=provider_request,
            responses=(),
            validation_results=(),
            consensus_result=None,
            conflict_resolution=None,
            status="prepared_awaiting_provider_response",
            summary=(
                f"Capability '{capability.value}' -> provider '{provider.provider_name}' selected. "
                f"Provider-neutral request prepared; no external call made."
            ),
        )
        return recommendation

    def finalize_with_responses(
        self,
        session_id: str,
        responses: tuple[ProviderResponse, ...],
        risk: RiskLevel,
    ) -> AIRecommendation:
        """Runs Response Validation -> Consensus -> (Conflict Resolution). `responses` must already exist — this method never calls a provider (Part 9)."""
        session = self.session_manager.get(session_id)
        capability = session.prompt and self._capability_for_session(session)

        validation_results = tuple(self.response_validator.validate(r) for r in responses)
        valid_responses = tuple(
            r for r, v in zip(responses, validation_results) if v.valid
        )

        consensus_result = self.consensus_engine.build_consensus(valid_responses)

        conflict_resolution = None
        if consensus_result.status is ConsensusStatus.CONFLICT:
            conflict_resolution = self.conflict_resolver.resolve(consensus_result, risk=risk, retry_count=session.retry_count)

        if consensus_result.status is ConsensusStatus.AGREEMENT:
            self.session_manager.set_status(session_id, AISessionStatus.COMPLETED)
            status = "consensus_reached"
        elif consensus_result.status is ConsensusStatus.CONFLICT:
            status = "conflict_detected"
        else:
            status = "insufficient_responses"

        summary = self._summarize_final(capability, consensus_result, conflict_resolution)
        recommendation = self._build_recommendation(
            capability=capability,
            provider=self.model_registry.get(session.provider_id) if session.provider_id else None,
            session_id=session_id,
            provider_request=None,
            responses=responses,
            validation_results=validation_results,
            consensus_result=consensus_result,
            conflict_resolution=conflict_resolution,
            status=status,
            summary=summary,
        )
        return recommendation

    def _capability_for_session(self, session) -> Any:
        capability, _, _ = classify_capability(session.prompt.goal_summary)
        return capability

    def _build_recommendation(
        self,
        capability,
        provider,
        session_id,
        provider_request,
        responses,
        validation_results,
        consensus_result,
        conflict_resolution,
        status,
        summary,
    ) -> AIRecommendation:
        recommendation = AIRecommendation(
            recommendation_id=_new_id("airec"),
            capability=capability,
            selected_provider=provider,
            session_id=session_id,
            provider_request=provider_request,
            responses=tuple(responses),
            validation_results=tuple(validation_results),
            consensus_result=consensus_result,
            conflict_resolution=conflict_resolution,
            status=status,
            summary=summary,
            created_at=_utc_now_iso(),
        )
        self._audit.record(
            event_type="ai_coordination.recommendation_returned",
            message=summary,
            details={"recommendation_id": recommendation.recommendation_id, "status": status},
        )
        return recommendation

    @staticmethod
    def _summarize_final(capability, consensus_result, conflict_resolution) -> str:
        base = f"Capability '{capability.value if capability else 'unknown'}': consensus status {consensus_result.status.value}."
        if conflict_resolution is not None:
            return f"{base} Conflict resolution: {conflict_resolution.resolution_type.value}."
        if consensus_result.status is ConsensusStatus.AGREEMENT:
            return f"{base} Agreed action: {consensus_result.agreed_action}."
        return base

    def health_check(self) -> AICoordinationHealthReport:
        return run_ai_coordination_health_check(self)

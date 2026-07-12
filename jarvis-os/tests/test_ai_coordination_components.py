"""
Unit tests for Sprint-5's individual AI Coordination Layer components —
Part 14 coverage. Full pipeline/acceptance scenarios are covered
separately in test_ai_coordination_acceptance.py.
"""

from __future__ import annotations

import pytest

from jarvis.audit import AuditLedger
from jarvis.ai_coordination.capability_classifier import classify as classify_capability
from jarvis.ai_coordination.capability_registry import CapabilityRegistry
from jarvis.ai_coordination.conflict_resolver import ConflictResolver
from jarvis.ai_coordination.consensus_engine import ConsensusEngine
from jarvis.ai_coordination.conversation_manager import ConversationError, ConversationManager
from jarvis.ai_coordination.model_registry import ModelRegistry, ModelRegistryError
from jarvis.ai_coordination.model_selector import ModelSelector
from jarvis.ai_coordination.models import (
    Capability,
    ConflictResolutionType,
    ConsensusStatus,
    ProviderAvailability,
    ProviderHealth,
    ProviderMetadata,
    ProviderResponse,
)
from jarvis.ai_coordination.response_validator import ResponseValidator
from jarvis.ai_coordination.session_manager import AISessionError, AISessionManager
from jarvis.intelligence.models import RiskLevel, StructuredPrompt, TaskPriority


@pytest.fixture()
def audit_ledger(tmp_path):
    ledger = AuditLedger(storage_path=tmp_path / "audit.jsonl")
    ledger.connect()
    return ledger


def _prompt(goal_summary="Do something") -> StructuredPrompt:
    return StructuredPrompt(
        prompt_id="p-1",
        goal_summary=goal_summary,
        context_summary={},
        memory_references=(),
        constraints=(),
        created_at="now",
    )


def _response(response_id, provider_id, action, confidence, completeness=0.9, request_id="req-1") -> ProviderResponse:
    return ProviderResponse(
        response_id=response_id,
        provider_id=provider_id,
        request_id=request_id,
        recommended_action=action,
        content="content",
        confidence=confidence,
        completeness=completeness,
        structured_fields={},
        created_at="now",
    )


# --- Capability Registry -----------------------------------------------------------


def test_capability_registry_covers_every_capability():
    registry = CapabilityRegistry()
    for capability in Capability:
        assert registry.is_supported(capability)
        assert registry.describe(capability)
    assert len(registry.list_all()) == len(Capability)
    assert registry.is_healthy()


@pytest.mark.parametrize(
    "text,expected",
    [
        ("Design Android Voice Interface", Capability.ARCHITECTURE),
        ("Implement Memory Optimization", Capability.IMPLEMENTATION),
        ("Research SQLite Alternatives", Capability.RESEARCH),
        ("Debug why the scanner fails", Capability.DEBUGGING),
        ("Write documentation for the API", Capability.DOCUMENTATION),
    ],
)
def test_capability_classification(text, expected):
    capability, confidence, reason = classify_capability(text)
    assert capability is expected
    assert 0.0 <= confidence <= 1.0
    assert reason


# --- Model Registry --------------------------------------------------------------


def test_model_registry_seeds_example_providers():
    registry = ModelRegistry()
    providers = registry.list_all()
    assert len(providers) == 4
    names = {p.provider_name for p in providers}
    assert names == {"OpenAI", "Anthropic", "Google", "Future Provider"}


def test_model_registry_metadata_has_no_credential_fields():
    """Structural guarantee: ProviderMetadata cannot hold an api_key, base_url, or client — see its docstring."""
    from dataclasses import fields

    field_names = {f.name for f in fields(ProviderMetadata)}
    for forbidden in ("api_key", "base_url", "client", "endpoint", "token", "secret"):
        assert forbidden not in field_names


def test_model_registry_register_and_get():
    registry = ModelRegistry(seed_examples=False)
    provider = ProviderMetadata(
        provider_id="p-1",
        provider_name="Test",
        supported_capabilities=(Capability.RESEARCH,),
        priority=1,
        estimated_cost=1.0,
        estimated_latency_ms=100,
        availability=ProviderAvailability.AVAILABLE,
        version="1.0",
        health=ProviderHealth.HEALTHY,
    )
    registry.register(provider)
    assert registry.get("p-1") is provider
    assert len(registry) == 1


def test_model_registry_duplicate_registration_raises():
    registry = ModelRegistry(seed_examples=False)
    provider = ProviderMetadata(
        provider_id="p-1", provider_name="Test", supported_capabilities=(Capability.RESEARCH,),
        priority=1, estimated_cost=1.0, estimated_latency_ms=100,
        availability=ProviderAvailability.AVAILABLE, version="1.0", health=ProviderHealth.HEALTHY,
    )
    registry.register(provider)
    with pytest.raises(ModelRegistryError):
        registry.register(provider)


def test_model_registry_unknown_provider_raises():
    registry = ModelRegistry(seed_examples=False)
    with pytest.raises(ModelRegistryError):
        registry.get("does-not-exist")


def test_model_registry_find_by_capability():
    registry = ModelRegistry()
    architects = registry.find_by_capability(Capability.ARCHITECTURE)
    assert all(Capability.ARCHITECTURE in p.supported_capabilities for p in architects)
    assert len(architects) >= 1


# --- Model Selector --------------------------------------------------------------


def test_model_selector_is_deterministic():
    registry = ModelRegistry()
    selector = ModelSelector(registry)
    results = {
        selector.select(Capability.ARCHITECTURE, RiskLevel.LOW, TaskPriority.NORMAL).provider_id
        for _ in range(10)
    }
    assert len(results) == 1  # always the same answer


def test_model_selector_excludes_unavailable_providers():
    registry = ModelRegistry()
    selector = ModelSelector(registry)
    # "Future Provider" supports every capability but is UNAVAILABLE — must never be selected.
    for capability in Capability:
        chosen = selector.select(capability, RiskLevel.LOW, TaskPriority.NORMAL)
        if chosen is not None:
            assert chosen.provider_id != "provider-future-example"


def test_model_selector_returns_none_when_no_provider_supports_capability():
    registry = ModelRegistry(seed_examples=False)
    selector = ModelSelector(registry)
    assert selector.select(Capability.ARCHITECTURE, RiskLevel.LOW, TaskPriority.NORMAL) is None


def test_model_selector_high_risk_excludes_degraded_providers():
    registry = ModelRegistry(seed_examples=False)
    degraded = ProviderMetadata(
        provider_id="p-degraded", provider_name="Degraded", supported_capabilities=(Capability.RESEARCH,),
        priority=1, estimated_cost=1.0, estimated_latency_ms=100,
        availability=ProviderAvailability.DEGRADED, version="1.0", health=ProviderHealth.DEGRADED,
    )
    registry.register(degraded)
    selector = ModelSelector(registry)

    assert selector.select(Capability.RESEARCH, RiskLevel.LOW, TaskPriority.NORMAL) is not None
    assert selector.select(Capability.RESEARCH, RiskLevel.HIGH, TaskPriority.NORMAL) is None


def test_model_selector_priority_affects_tie_break():
    registry = ModelRegistry(seed_examples=False)
    fast_expensive = ProviderMetadata(
        provider_id="p-fast", provider_name="Fast", supported_capabilities=(Capability.RESEARCH,),
        priority=1, estimated_cost=10.0, estimated_latency_ms=100,
        availability=ProviderAvailability.AVAILABLE, version="1.0", health=ProviderHealth.HEALTHY,
    )
    slow_cheap = ProviderMetadata(
        provider_id="p-cheap", provider_name="Cheap", supported_capabilities=(Capability.RESEARCH,),
        priority=1, estimated_cost=1.0, estimated_latency_ms=1000,
        availability=ProviderAvailability.AVAILABLE, version="1.0", health=ProviderHealth.HEALTHY,
    )
    registry.register(fast_expensive)
    registry.register(slow_cheap)
    selector = ModelSelector(registry)

    assert selector.select(Capability.RESEARCH, RiskLevel.LOW, TaskPriority.HIGH).provider_id == "p-fast"
    assert selector.select(Capability.RESEARCH, RiskLevel.LOW, TaskPriority.NORMAL).provider_id == "p-cheap"


# --- AI Session Manager --------------------------------------------------------------


def test_session_manager_create_and_get(audit_ledger):
    manager = AISessionManager(audit_ledger)
    session = manager.create_session(_prompt())
    assert manager.get(session.session_id) is session
    assert session.retry_count == 0


def test_session_manager_unknown_session_raises(audit_ledger):
    manager = AISessionManager(audit_ledger)
    with pytest.raises(AISessionError):
        manager.get("does-not-exist")


def test_session_manager_retry_and_fail(audit_ledger):
    manager = AISessionManager(audit_ledger)
    session = manager.create_session(_prompt())
    manager.increment_retry(session.session_id)
    assert manager.get(session.session_id).retry_count == 1
    manager.fail(session.session_id, "test failure")
    assert manager.get(session.session_id).status.value == "failed"


def test_session_manager_audits_creation(audit_ledger):
    manager = AISessionManager(audit_ledger)
    manager.create_session(_prompt())
    event_types = [e.event_type for e in audit_ledger.read_all()]
    assert "ai_coordination.session_created" in event_types


# --- Conversation Manager --------------------------------------------------------------


def test_conversation_manager_start_and_add_exchange(audit_ledger):
    manager = ConversationManager(audit_ledger)
    manager.start("conv-1")
    manager.add_exchange("conv-1", "session-1", "prompt-1")
    history = manager.history("conv-1")
    assert len(history) == 1
    assert history[0].prompt_id == "prompt-1"


def test_conversation_manager_unknown_conversation_raises(audit_ledger):
    manager = ConversationManager(audit_ledger)
    with pytest.raises(ConversationError):
        manager.history("does-not-exist")


def test_conversation_manager_follow_up_links_parent(audit_ledger):
    manager = ConversationManager(audit_ledger)
    manager.start("conv-1")
    followup = manager.link_follow_up("conv-1", "conv-2")
    assert followup.parent_request == "conv-1"


def test_conversation_manager_start_is_idempotent(audit_ledger):
    manager = ConversationManager(audit_ledger)
    first = manager.start("conv-1")
    manager.add_exchange("conv-1", "s-1", "p-1")
    second = manager.start("conv-1")
    assert second is first
    assert len(manager.history("conv-1")) == 1  # not reset by the second start()


# --- Response Validator --------------------------------------------------------------


def test_response_validator_valid_response(audit_ledger):
    validator = ResponseValidator(audit_ledger)
    response = _response("r-1", "provider-1", "proceed", confidence=0.8, completeness=0.9)
    result = validator.validate(response)
    assert result.valid
    assert result.flags == ()


def test_response_validator_flags_low_confidence(audit_ledger):
    validator = ResponseValidator(audit_ledger)
    response = _response("r-1", "provider-1", "proceed", confidence=0.1, completeness=0.9)
    result = validator.validate(response)
    assert "low_confidence" in [f.value for f in result.flags]


def test_response_validator_flags_incomplete(audit_ledger):
    validator = ResponseValidator(audit_ledger)
    response = _response("r-1", "provider-1", "proceed", confidence=0.8, completeness=0.2)
    result = validator.validate(response)
    assert "incomplete" in [f.value for f in result.flags]


def test_response_validator_flags_invalid_structure_for_missing_fields(audit_ledger):
    validator = ResponseValidator(audit_ledger)
    response = _response("r-1", "provider-1", "", confidence=0.8, completeness=0.9)
    result = validator.validate(response)
    assert not result.valid
    assert "invalid_structure" in [f.value for f in result.flags]


def test_response_validator_flags_inconsistency(audit_ledger):
    validator = ResponseValidator(audit_ledger)
    response = _response("r-1", "provider-1", "proceed", confidence=0.95, completeness=0.1)
    result = validator.validate(response)
    assert "inconsistent" in [f.value for f in result.flags]


# --- Consensus Engine --------------------------------------------------------------


def test_consensus_engine_agreement(audit_ledger):
    engine = ConsensusEngine(audit_ledger)
    responses = [
        _response("r-1", "p-1", "proceed", 0.9),
        _response("r-2", "p-2", "proceed", 0.7),
    ]
    result = engine.build_consensus(responses)
    assert result.status is ConsensusStatus.AGREEMENT
    assert result.agreed_action == "proceed"
    assert result.confidence == 0.7  # min of contributing confidences


def test_consensus_engine_conflict(audit_ledger):
    engine = ConsensusEngine(audit_ledger)
    responses = [
        _response("r-1", "p-1", "proceed", 0.9),
        _response("r-2", "p-2", "reject", 0.8),
    ]
    result = engine.build_consensus(responses)
    assert result.status is ConsensusStatus.CONFLICT
    assert result.agreed_action is None
    assert set(result.contributing_provider_ids) | set(result.dissenting_provider_ids) == {"p-1", "p-2"}


def test_consensus_engine_insufficient_responses(audit_ledger):
    engine = ConsensusEngine(audit_ledger)
    result = engine.build_consensus([_response("r-1", "p-1", "proceed", 0.9)])
    assert result.status is ConsensusStatus.INSUFFICIENT_RESPONSES


def test_consensus_engine_never_calls_a_provider():
    import jarvis.ai_coordination.consensus_engine as module

    with open(module.__file__) as handle:
        import_lines = [l.lower() for l in handle if l.strip().startswith(("import ", "from "))]
    for forbidden in ("requests", "http", "openai", "anthropic"):
        assert not any(forbidden in line for line in import_lines)


# --- Conflict Resolver --------------------------------------------------------------


def test_conflict_resolver_high_risk_asks_owner(audit_ledger):
    consensus_engine = ConsensusEngine(audit_ledger)
    consensus = consensus_engine.build_consensus(
        [_response("r-1", "p-1", "proceed", 0.9), _response("r-2", "p-2", "reject", 0.8)]
    )
    resolver = ConflictResolver(audit_ledger)
    resolution = resolver.resolve(consensus, risk=RiskLevel.HIGH, retry_count=0)
    assert resolution.resolution_type is ConflictResolutionType.ASK_OWNER


def test_conflict_resolver_low_confidence_conflict_rejects(audit_ledger):
    consensus_engine = ConsensusEngine(audit_ledger)
    consensus = consensus_engine.build_consensus(
        [_response("r-1", "p-1", "proceed", 0.2), _response("r-2", "p-2", "reject", 0.1)]
    )
    resolver = ConflictResolver(audit_ledger)
    resolution = resolver.resolve(consensus, risk=RiskLevel.LOW, retry_count=0)
    assert resolution.resolution_type is ConflictResolutionType.REJECT


def test_conflict_resolver_retries_before_limit(audit_ledger):
    consensus_engine = ConsensusEngine(audit_ledger)
    consensus = consensus_engine.build_consensus(
        [_response("r-1", "p-1", "proceed", 0.8), _response("r-2", "p-2", "reject", 0.75)]
    )
    resolver = ConflictResolver(audit_ledger)
    resolution = resolver.resolve(consensus, risk=RiskLevel.LOW, retry_count=0)
    assert resolution.resolution_type is ConflictResolutionType.RETRY


def test_conflict_resolver_escalates_after_retry_limit(audit_ledger):
    consensus_engine = ConsensusEngine(audit_ledger)
    consensus = consensus_engine.build_consensus(
        [_response("r-1", "p-1", "proceed", 0.8), _response("r-2", "p-2", "reject", 0.75)]
    )
    resolver = ConflictResolver(audit_ledger)
    resolution = resolver.resolve(consensus, risk=RiskLevel.LOW, retry_count=5)
    assert resolution.resolution_type is ConflictResolutionType.ESCALATE


def test_conflict_resolver_rejects_non_conflict_input(audit_ledger):
    consensus_engine = ConsensusEngine(audit_ledger)
    consensus = consensus_engine.build_consensus(
        [_response("r-1", "p-1", "proceed", 0.9), _response("r-2", "p-2", "proceed", 0.8)]
    )
    resolver = ConflictResolver(audit_ledger)
    with pytest.raises(ValueError):
        resolver.resolve(consensus, risk=RiskLevel.LOW, retry_count=0)


def test_conflict_resolver_never_auto_executes():
    import jarvis.ai_coordination.conflict_resolver as module

    with open(module.__file__) as handle:
        import_lines = [l.lower() for l in handle if l.strip().startswith(("import ", "from "))]
    for forbidden in ("jarvis.execution", "jarvis.kernel", "jarvis.agents"):
        assert not any(forbidden in line for line in import_lines)

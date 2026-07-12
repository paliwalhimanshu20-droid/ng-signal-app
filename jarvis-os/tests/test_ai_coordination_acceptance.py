"""
Sprint-5 Acceptance Scenarios 1-4, run against a real AICoordinator.
"""

from __future__ import annotations

import pytest

from jarvis.audit import AuditLedger
from jarvis.ai_coordination import AICoordinator, Capability, ConsensusStatus
from jarvis.ai_coordination.models import ProviderResponse
from jarvis.intelligence.models import RiskLevel, StructuredPrompt, TaskPriority


@pytest.fixture()
def coordinator(tmp_path):
    ledger = AuditLedger(storage_path=tmp_path / "audit.jsonl")
    ledger.connect()
    return AICoordinator(audit_ledger=ledger), ledger


def _prompt(goal_summary: str) -> StructuredPrompt:
    return StructuredPrompt(
        prompt_id="p-1",
        goal_summary=goal_summary,
        context_summary={},
        memory_references=(),
        constraints=("Never execute any action directly; produce a recommendation only.",),
        created_at="now",
    )


# --- Acceptance Scenario 1 --------------------------------------------------------


def test_scenario_1_design_android_voice_interface(coordinator):
    coord, _ = coordinator
    recommendation = coord.coordinate(_prompt("Design Android Voice Interface"), risk=RiskLevel.MODERATE, priority=TaskPriority.NORMAL)

    assert recommendation.capability is Capability.ARCHITECTURE
    assert recommendation.selected_provider is not None
    assert recommendation.provider_request is not None
    assert recommendation.responses == ()  # no API call
    assert recommendation.status == "prepared_awaiting_provider_response"


# --- Acceptance Scenario 2 --------------------------------------------------------


def test_scenario_2_implement_memory_optimization(coordinator):
    coord, _ = coordinator
    recommendation = coord.coordinate(_prompt("Implement Memory Optimization"), risk=RiskLevel.MODERATE, priority=TaskPriority.NORMAL)

    assert recommendation.capability is Capability.IMPLEMENTATION
    assert recommendation.selected_provider is not None
    assert recommendation.provider_request is not None
    assert "Never execute" in recommendation.provider_request.constraints[0]


# --- Acceptance Scenario 3 --------------------------------------------------------


def test_scenario_3_research_sqlite_alternatives(coordinator):
    coord, _ = coordinator
    recommendation = coord.coordinate(_prompt("Research SQLite Alternatives"), risk=RiskLevel.LOW, priority=TaskPriority.NORMAL)

    assert recommendation.capability is Capability.RESEARCH
    assert recommendation.selected_provider is not None
    session = coord.session_manager.get(recommendation.session_id)
    assert session.conversation_id  # a real research session/conversation exists


# --- Acceptance Scenario 4 --------------------------------------------------------


def test_scenario_4_compare_two_provider_responses_agreement(coordinator):
    coord, _ = coordinator
    prep = coord.coordinate(_prompt("Implement Memory Optimization"), risk=RiskLevel.LOW, priority=TaskPriority.NORMAL)

    r1 = ProviderResponse(
        response_id="r-1", provider_id="provider-openai-example", request_id=prep.provider_request.request_id,
        recommended_action="proceed", content="Do it this way", confidence=0.85, completeness=0.9,
        structured_fields={}, created_at="now",
    )
    r2 = ProviderResponse(
        response_id="r-2", provider_id="provider-anthropic-example", request_id=prep.provider_request.request_id,
        recommended_action="proceed", content="Agreed, do it this way", confidence=0.8, completeness=0.9,
        structured_fields={}, created_at="now",
    )
    final = coord.finalize_with_responses(prep.session_id, (r1, r2), risk=RiskLevel.LOW)

    assert final.consensus_result.status is ConsensusStatus.AGREEMENT
    assert final.status == "consensus_reached"


def test_scenario_4_compare_two_provider_responses_conflict(coordinator):
    coord, _ = coordinator
    prep = coord.coordinate(_prompt("Implement Memory Optimization"), risk=RiskLevel.LOW, priority=TaskPriority.NORMAL)

    r1 = ProviderResponse(
        response_id="r-1", provider_id="provider-openai-example", request_id=prep.provider_request.request_id,
        recommended_action="proceed", content="Do it this way", confidence=0.85, completeness=0.9,
        structured_fields={}, created_at="now",
    )
    r2 = ProviderResponse(
        response_id="r-2", provider_id="provider-anthropic-example", request_id=prep.provider_request.request_id,
        recommended_action="redesign_first", content="Disagree, redesign first", confidence=0.8, completeness=0.9,
        structured_fields={}, created_at="now",
    )
    final = coord.finalize_with_responses(prep.session_id, (r1, r2), risk=RiskLevel.LOW)

    assert final.consensus_result.status is ConsensusStatus.CONFLICT
    assert final.status == "conflict_detected"
    assert final.conflict_resolution is not None
    # Never automatically executes anything — resolution is advisory only.
    assert final.conflict_resolution.resolution_type.value in ("retry", "escalate", "ask_owner", "reject")


# --- Health ------------------------------------------------------------------------


def test_ai_coordination_health_all_green(coordinator):
    coord, _ = coordinator
    report = coord.health_check()
    assert report.healthy
    assert set(report.checks) == {
        "ai_coordinator",
        "capability_registry",
        "model_registry",
        "model_selector",
        "session_manager",
        "conversation_manager",
        "prompt_dispatcher",
        "response_validator",
        "consensus_engine",
        "conflict_resolver",
    }
    assert all(report.checks.values())


# --- Audit: every pipeline stage produces an event (Part 13) -----------------------


def test_every_pipeline_stage_is_audited(coordinator):
    coord, ledger = coordinator
    prep = coord.coordinate(_prompt("Design Android Voice Interface"), risk=RiskLevel.LOW, priority=TaskPriority.NORMAL)

    r1 = ProviderResponse(
        response_id="r-1", provider_id="provider-openai-example", request_id=prep.provider_request.request_id,
        recommended_action="proceed", content="x", confidence=0.8, completeness=0.9, structured_fields={}, created_at="now",
    )
    r2 = ProviderResponse(
        response_id="r-2", provider_id="provider-anthropic-example", request_id=prep.provider_request.request_id,
        recommended_action="reject", content="y", confidence=0.8, completeness=0.9, structured_fields={}, created_at="now",
    )
    coord.finalize_with_responses(prep.session_id, (r1, r2), risk=RiskLevel.LOW)

    event_types = [e.event_type for e in ledger.read_all()]
    required = (
        "ai_coordination.capability_selected",
        "ai_coordination.provider_selected",
        "ai_coordination.session_created",
        "ai_coordination.prompt_prepared",
        "ai_coordination.response_validated",
        "ai_coordination.consensus_generated",
        "ai_coordination.conflict_detected",
        "ai_coordination.recommendation_returned",
    )
    for event in required:
        assert event in event_types, f"Missing AI coordination audit event: {event}"


# --- No external calls, ever --------------------------------------------------------


def test_ai_coordination_has_no_external_dependency():
    import pathlib

    package_dir = pathlib.Path(__file__).parent.parent / "jarvis" / "ai_coordination"
    for py_file in package_dir.glob("*.py"):
        with open(py_file) as handle:
            import_lines = [line.lower() for line in handle if line.strip().startswith(("import ", "from "))]
        for forbidden in ("requests", "httpx", "urllib", "openai", "anthropic", "google.generativeai", "socket"):
            assert not any(forbidden in line for line in import_lines), f"{py_file} imports forbidden '{forbidden}'"


def test_no_provider_metadata_names_a_real_endpoint(coordinator):
    coord, _ = coordinator
    for provider in coord.model_registry.list_all():
        # ProviderMetadata structurally has no such field at all (see
        # test_model_registry_metadata_has_no_credential_fields), this
        # is an additional behavioral check on the seeded data itself.
        assert not hasattr(provider, "api_key")
        assert not hasattr(provider, "base_url")

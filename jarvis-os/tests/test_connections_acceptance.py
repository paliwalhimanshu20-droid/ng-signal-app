"""
Sprint-6 Acceptance Scenarios 1-5, run against real ConnectionManager +
ConnectionService + real adapter classes (with an injected fake
transport — no live network call, per the adapter modules' own
docstrings).
"""

from __future__ import annotations

import json

import pytest

from jarvis.ai_coordination import AICoordinator
from jarvis.audit import AuditLedger
from jarvis.connections import (
    ConnectionCredentials,
    ConnectionHealthStatus,
    ConnectionManager,
    ConnectionProfile,
    ConnectionService,
    ConnectionServiceError,
    PermissionScope,
    ProfileManager,
    ProviderRegistry,
    run_connection_health_check,
)
from jarvis.connections.ai_dispatch import AIConnectionDispatcher, NoConnectedProviderError
from jarvis.connections.adapters.anthropic_adapter import AnthropicAdapter
from jarvis.connections.adapters.openai_adapter import OpenAIAdapter
from jarvis.connections.connection_manager import ConnectionError_
from jarvis.connections.models import ConnectionStatus
from jarvis.connections.provider_adapter import HTTPResponseSpec, ProviderAdapterError
from jarvis.intelligence import IntelligenceEngine
from jarvis.intelligence.models import RiskLevel, TaskPriority
from jarvis.memory import MemoryManager


def _openai_transport(content="Looks good.", finish_reason="stop"):
    def transport(spec):
        if spec.url.endswith("/models"):
            return HTTPResponseSpec(200, {}, b"{}")
        body = json.dumps({"choices": [{"message": {"content": content}, "finish_reason": finish_reason}]}).encode()
        return HTTPResponseSpec(200, {}, body)

    return transport


def _anthropic_transport(content="Looks good.", stop_reason="end_turn"):
    def transport(spec):
        return HTTPResponseSpec(200, {}, json.dumps({"content": [{"text": content}], "stop_reason": stop_reason}).encode())

    return transport


@pytest.fixture()
def system(tmp_path):
    ledger = AuditLedger(storage_path=tmp_path / "audit.jsonl")
    ledger.connect()
    connection_manager = ConnectionManager(ledger)
    provider_registry = ProviderRegistry(seed_defaults=False)
    provider_registry.register("provider-openai", lambda: OpenAIAdapter(transport=_openai_transport()))
    provider_registry.register("provider-anthropic", lambda: AnthropicAdapter(transport=_anthropic_transport()))
    connection_service = ConnectionService(connection_manager, provider_registry, ledger)
    profile_manager = ProfileManager(connection_manager, ledger)
    return {
        "ledger": ledger,
        "connections": connection_manager,
        "providers": provider_registry,
        "service": connection_service,
        "profiles": profile_manager,
    }


# --- Acceptance Scenario 1: connect ChatGPT, approve, health PASS --------------------


def test_scenario_1_connect_chatgpt_approve_health_pass(system):
    cm, svc = system["connections"], system["service"]
    connection = cm.request_connection(
        "provider-openai", "ChatGPT",
        requested_permissions=frozenset({PermissionScope.READ, PermissionScope.WRITE}),
        maximum_permission=PermissionScope.WRITE,
    )
    assert connection.status is ConnectionStatus.PENDING_APPROVAL

    cm.approve(connection.connection_id, approved_by="owner")
    svc.establish(connection.connection_id, ConnectionCredentials(api_key="sk-test"))

    established = cm.get(connection.connection_id)
    assert established.status is ConnectionStatus.CONNECTED
    assert established.health is ConnectionHealthStatus.HEALTHY

    health = svc.check_health(connection.connection_id)
    assert health is ConnectionHealthStatus.HEALTHY


# --- Acceptance Scenario 2: reject Claude, JARVIS must never use it -----------------


def test_scenario_2_reject_claude_never_used(system):
    cm, svc = system["connections"], system["service"]
    connection = cm.request_connection(
        "provider-anthropic", "Claude",
        requested_permissions=frozenset({PermissionScope.READ}),
        maximum_permission=PermissionScope.READ,
    )
    cm.reject(connection.connection_id, reason="Owner declined")

    assert cm.get(connection.connection_id).status is ConnectionStatus.REJECTED
    # Attempting to establish a rejected connection must fail — there is
    # no code path that lets a rejected connection reach CONNECTED.
    with pytest.raises(ConnectionServiceError):
        svc.establish(connection.connection_id, ConnectionCredentials(api_key="sk-test"))
    # And no live adapter was ever created for it.
    assert cm.list_connected() == ()


# --- Acceptance Scenario 3: disable all connections ----------------------------------


def test_scenario_3_disable_all_connections(system):
    cm = system["connections"]
    names = ["GitHub", "ChatGPT", "Claude", "Calendar", "Spotify"]
    connections = []
    for i, name in enumerate(names):
        c = cm.request_connection(f"provider-{i}", name, frozenset({PermissionScope.READ}), PermissionScope.READ)
        cm.approve(c.connection_id, "owner")
        cm.mark_connected(c.connection_id)
        connections.append(c)

    affected = cm.disable_all(reason="Owner disabled all connections")
    assert len(affected) == 5
    for c in connections:
        assert cm.get(c.connection_id).status is ConnectionStatus.DISCONNECTED


# --- Acceptance Scenario 4: Work Profile ---------------------------------------------


def test_scenario_4_work_profile(system):
    cm, profiles = system["connections"], system["profiles"]
    work_tools = ["GitHub", "ChatGPT", "Claude", "NG Signal Pro", "ProjectOS"]
    personal_tools = ["Spotify"]

    work_connections = []
    for i, name in enumerate(work_tools):
        c = cm.request_connection(f"provider-work-{i}", name, frozenset({PermissionScope.READ}), PermissionScope.READ, profile_tags=("work",))
        cm.approve(c.connection_id, "owner")
        cm.mark_connected(c.connection_id)
        work_connections.append(c)

    personal_connections = []
    for i, name in enumerate(personal_tools):
        c = cm.request_connection(f"provider-personal-{i}", name, frozenset({PermissionScope.READ}), PermissionScope.READ, profile_tags=("personal",))
        cm.approve(c.connection_id, "owner")
        cm.mark_connected(c.connection_id)
        personal_connections.append(c)

    profiles.activate(ConnectionProfile.WORK)

    for c in work_connections:
        assert cm.get(c.connection_id).status is ConnectionStatus.CONNECTED
    for c in personal_connections:
        assert cm.get(c.connection_id).status is ConnectionStatus.SUSPENDED


# --- Acceptance Scenario 5: full pipeline ---------------------------------------------


def test_scenario_5_full_pipeline(system, tmp_path):
    ledger = system["ledger"]
    cm, svc = system["connections"], system["service"]

    memory = MemoryManager(storage_dir=tmp_path / "memory", audit_ledger=ledger)
    memory.connect()
    intelligence = IntelligenceEngine(memory_manager=memory, audit_ledger=ledger)
    ai_coordinator = AICoordinator(audit_ledger=ledger)
    dispatcher = AIConnectionDispatcher(ai_coordinator, cm, svc, ledger)

    for provider_id, name in [("provider-openai", "OpenAI"), ("provider-anthropic", "Anthropic")]:
        connection = cm.request_connection(provider_id, name, frozenset({PermissionScope.READ, PermissionScope.WRITE}), PermissionScope.WRITE)
        cm.approve(connection.connection_id, "owner")
        svc.establish(connection.connection_id, ConnectionCredentials(api_key="sk-test"))

    # Memory -> Intelligence
    recommendation = intelligence.analyze("Review today's NG Signal Pro progress")
    assert recommendation.goal is not None

    # Intelligence -> AI Coordinator
    prepared = ai_coordinator.coordinate(
        recommendation.prompt, risk=recommendation.decision.risk_level, priority=recommendation.goal.priority
    )
    assert prepared.selected_provider is not None

    # AI Coordinator -> Connection Manager -> Provider Adapter -> Response -> Validation -> Consensus
    final = dispatcher.dispatch(prepared, risk=recommendation.decision.risk_level)

    assert len(final.responses) == 1
    assert final.validation_results[0].valid
    assert final.status in ("insufficient_responses", "consensus_reached", "conflict_detected")


def test_scenario_5_blocked_without_connection(system, tmp_path):
    """The full pipeline must refuse to dispatch if the selected provider has no live connection — Owner Sovereignty, not a fallback."""
    ledger = system["ledger"]
    cm, svc = system["connections"], system["service"]

    memory = MemoryManager(storage_dir=tmp_path / "memory", audit_ledger=ledger)
    memory.connect()
    intelligence = IntelligenceEngine(memory_manager=memory, audit_ledger=ledger)
    ai_coordinator = AICoordinator(audit_ledger=ledger)
    dispatcher = AIConnectionDispatcher(ai_coordinator, cm, svc, ledger)

    recommendation = intelligence.analyze("Review today's NG Signal Pro progress")
    prepared = ai_coordinator.coordinate(recommendation.prompt, risk=recommendation.decision.risk_level, priority=recommendation.goal.priority)

    with pytest.raises(NoConnectedProviderError):
        dispatcher.dispatch(prepared, risk=recommendation.decision.risk_level)


# --- Provider Failure / Recovery -----------------------------------------------------


def test_provider_failure_marks_connection_failed_not_connected(system):
    cm, providers = system["connections"], system["providers"]

    def failing_transport(spec):
        return HTTPResponseSpec(500, {}, b"server error")

    providers.register("provider-broken", lambda: OpenAIAdapter(transport=failing_transport))
    svc = ConnectionService(cm, providers, system["ledger"])

    connection = cm.request_connection("provider-broken", "Broken", frozenset({PermissionScope.READ}), PermissionScope.READ)
    cm.approve(connection.connection_id, "owner")

    with pytest.raises(ProviderAdapterError):
        svc.establish(connection.connection_id, ConnectionCredentials(api_key="sk-test"))

    assert cm.get(connection.connection_id).status is ConnectionStatus.FAILED


def test_recovery_after_failure_requires_fresh_approval_cycle(system):
    """A FAILED connection is not silently retried — the Owner must go through request/approve again, consistent with no automatic reconnection."""
    cm = system["connections"]
    connection = cm.request_connection("provider-x", "X", frozenset({PermissionScope.READ}), PermissionScope.READ)
    cm.approve(connection.connection_id, "owner")
    cm.mark_failed(connection.connection_id, reason="simulated failure")

    assert cm.get(connection.connection_id).status is ConnectionStatus.FAILED
    # No method transitions FAILED -> CONNECTED directly.
    with pytest.raises(ConnectionError_):
        cm.mark_connected(connection.connection_id)


def test_send_prompt_fails_cleanly_when_provider_errors_mid_dispatch(system):
    cm, providers = system["connections"], system["providers"]

    def failing_transport(spec):
        if spec.url.endswith("/models"):
            return HTTPResponseSpec(200, {}, b"{}")
        return HTTPResponseSpec(503, {}, b"unavailable")

    providers.register("provider-flaky", lambda: OpenAIAdapter(transport=failing_transport))
    svc = ConnectionService(cm, providers, system["ledger"])

    connection = cm.request_connection("provider-flaky", "Flaky", frozenset({PermissionScope.READ, PermissionScope.WRITE}), PermissionScope.WRITE)
    cm.approve(connection.connection_id, "owner")
    svc.establish(connection.connection_id, ConnectionCredentials(api_key="sk-test"))

    from jarvis.ai_coordination.models import ProviderRequest

    request = ProviderRequest(
        request_id="req-1", session_id="s-1", capability=None, prompt_summary="x",
        context_references=(), constraints=(), prepared_at="now",
    )
    with pytest.raises(ConnectionServiceError):
        svc.send_prompt(connection.connection_id, request)
    assert cm.get(connection.connection_id).health is ConnectionHealthStatus.UNHEALTHY


# --- Health ----------------------------------------------------------------------------


def test_connection_system_health_all_green(system):
    report = run_connection_health_check(system["connections"], system["providers"], system["profiles"])
    assert report.healthy
    assert set(report.checks) == {
        "connection_manager",
        "provider_registry",
        "openai_adapter",
        "anthropic_adapter",
        "profiles",
        "connection_health",
    }


def test_connection_health_detects_unhealthy_connected_connection(system):
    cm = system["connections"]
    connection = cm.request_connection("provider-x", "X", frozenset({PermissionScope.READ}), PermissionScope.READ)
    cm.approve(connection.connection_id, "owner")
    cm.mark_connected(connection.connection_id)
    cm.record_health(connection.connection_id, ConnectionHealthStatus.UNHEALTHY)

    report = run_connection_health_check(system["connections"], system["providers"], system["profiles"])
    assert not report.checks["connection_health"]
    assert not report.healthy


# --- Audit: every pipeline stage produces an event ------------------------------------


def test_scenario_5_pipeline_is_fully_audited(system, tmp_path):
    ledger = system["ledger"]
    cm, svc = system["connections"], system["service"]

    memory = MemoryManager(storage_dir=tmp_path / "memory", audit_ledger=ledger)
    memory.connect()
    intelligence = IntelligenceEngine(memory_manager=memory, audit_ledger=ledger)
    ai_coordinator = AICoordinator(audit_ledger=ledger)
    dispatcher = AIConnectionDispatcher(ai_coordinator, cm, svc, ledger)

    for provider_id, name in [("provider-openai", "OpenAI"), ("provider-anthropic", "Anthropic")]:
        connection = cm.request_connection(provider_id, name, frozenset({PermissionScope.READ, PermissionScope.WRITE}), PermissionScope.WRITE)
        cm.approve(connection.connection_id, "owner")
        svc.establish(connection.connection_id, ConnectionCredentials(api_key="sk-test"))

    recommendation = intelligence.analyze("Review today's NG Signal Pro progress")
    prepared = ai_coordinator.coordinate(recommendation.prompt, risk=recommendation.decision.risk_level, priority=recommendation.goal.priority)
    dispatcher.dispatch(prepared, risk=recommendation.decision.risk_level)

    event_types = [e.event_type for e in ledger.read_all()]
    required = (
        "connection.requested",
        "connection.approved",
        "connection.connected",
        "connection.health_checked",
        "ai_coordination.capability_selected",
        "ai_coordination.provider_selected",
        "ai_coordination.response_validated",
        "ai_coordination.consensus_generated",
    )
    for event in required:
        assert event in event_types, f"Missing audit event: {event}"


# --- No credentials ever appear in the audit trail -------------------------------------


def test_api_key_never_appears_in_audit_ledger(system):
    cm, svc = system["connections"], system["service"]
    secret = "sk-THIS-IS-A-SECRET-VALUE-12345"

    connection = cm.request_connection("provider-openai", "OpenAI", frozenset({PermissionScope.READ}), PermissionScope.READ)
    cm.approve(connection.connection_id, "owner")
    svc.establish(connection.connection_id, ConnectionCredentials(api_key=secret))

    for entry in system["ledger"].read_all():
        assert secret not in entry.to_json()

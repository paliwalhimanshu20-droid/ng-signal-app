"""
Tests for jarvis.routing.router.TaskRouter: successful route, unknown
capability, multiple candidates, unhealthy agent.
"""

from __future__ import annotations

import pytest

from jarvis.agents.engineering_agent import EngineeringAgent
from jarvis.agents.models import AgentHealthStatus
from jarvis.audit import AuditLedger
from jarvis.intake import IntentProcessor, TaskPlanner
from jarvis.registry import AgentLifecycleState, AgentRecord, AgentRegistry
from jarvis.routing import RoutingStatus, TaskRouter


class _UnhealthyEngineeringAgent(EngineeringAgent):
    """Test-only subclass: declares the same capabilities but always reports unhealthy."""

    def health(self) -> AgentHealthStatus:
        return AgentHealthStatus(healthy=False, detail="forced unhealthy for testing")


def _activate(registry: AgentRegistry, agent_id: str) -> None:
    registry.transition(agent_id, AgentLifecycleState.REVIEWED)
    registry.transition(agent_id, AgentLifecycleState.PROVISIONED)
    registry.transition(agent_id, AgentLifecycleState.ACTIVE)


def _register_active(registry: AgentRegistry, instance) -> None:
    registry.register(
        AgentRecord(
            agent_id=instance.agent_id,
            domain=instance.domain,
            parent_domain=None,
            capabilities=instance.capabilities(),
            instance=instance,
        )
    )
    _activate(registry, instance.agent_id)


@pytest.fixture()
def ledger(tmp_path):
    audit_ledger = AuditLedger(storage_path=tmp_path / "ledger.jsonl")
    audit_ledger.connect()
    return audit_ledger


@pytest.fixture()
def ready_task(ledger):
    processor = IntentProcessor(audit_ledger=ledger)
    planner = TaskPlanner(audit_ledger=ledger)
    return planner.plan(processor.process("Analyze GitHub repository"))


def test_successful_route(ledger, ready_task):
    registry = AgentRegistry()
    _register_active(registry, EngineeringAgent())
    router = TaskRouter(registry=registry, audit_ledger=ledger)

    decision = router.route(ready_task)

    assert decision.status is RoutingStatus.ROUTED
    assert decision.selected_agent_id == "engineering-agent-001"


def test_unknown_capability_returns_structured_failure(ledger):
    registry = AgentRegistry()
    _register_active(registry, EngineeringAgent())
    router = TaskRouter(registry=registry, audit_ledger=ledger)

    processor = IntentProcessor(audit_ledger=ledger)
    planner = TaskPlanner(audit_ledger=ledger)
    trading_task = planner.plan(processor.process("research recent market signals"))

    decision = router.route(trading_task)

    assert decision.status is RoutingStatus.NO_CAPABLE_AGENT
    assert decision.selected_agent_id is None
    assert "engineering-agent-001" in decision.candidate_agent_ids
    assert decision.reason


def test_no_agents_registered_at_all_returns_structured_failure(ledger, ready_task):
    registry = AgentRegistry()
    router = TaskRouter(registry=registry, audit_ledger=ledger)

    decision = router.route(ready_task)

    assert decision.status is RoutingStatus.NO_CAPABLE_AGENT
    assert decision.candidate_agent_ids == ()
    assert "No agents are registered" in decision.reason


def test_multiple_candidates_selects_deterministically(ledger, ready_task):
    registry = AgentRegistry()
    _register_active(registry, EngineeringAgent(agent_id="engineering-agent-002"))
    _register_active(registry, EngineeringAgent(agent_id="engineering-agent-001"))
    router = TaskRouter(registry=registry, audit_ledger=ledger)

    decision = router.route(ready_task)

    assert decision.status is RoutingStatus.ROUTED
    assert decision.selected_agent_id == "engineering-agent-001"  # lowest agent_id wins
    assert set(decision.candidate_agent_ids) == {"engineering-agent-001", "engineering-agent-002"}


def test_unhealthy_agent_is_rejected(ledger, ready_task):
    registry = AgentRegistry()
    _register_active(registry, _UnhealthyEngineeringAgent())
    router = TaskRouter(registry=registry, audit_ledger=ledger)

    decision = router.route(ready_task)

    assert decision.status is RoutingStatus.NO_CAPABLE_AGENT
    assert "healthy" in decision.reason.lower()


def test_unhealthy_and_healthy_candidates_mixed_picks_healthy_one(ledger, ready_task):
    registry = AgentRegistry()
    _register_active(registry, _UnhealthyEngineeringAgent(agent_id="engineering-agent-unhealthy"))
    _register_active(registry, EngineeringAgent(agent_id="engineering-agent-healthy"))
    router = TaskRouter(registry=registry, audit_ledger=ledger)

    decision = router.route(ready_task)

    assert decision.status is RoutingStatus.ROUTED
    assert decision.selected_agent_id == "engineering-agent-healthy"


def test_routing_writes_audit_trail(ledger, ready_task):
    registry = AgentRegistry()
    _register_active(registry, EngineeringAgent())
    router = TaskRouter(registry=registry, audit_ledger=ledger)

    router.route(ready_task)

    event_types = [e.event_type for e in ledger.read_all()]
    assert "task.candidate_search" in event_types
    assert "agent.selected" in event_types

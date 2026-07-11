"""
Tests for jarvis.permission.engine.PermissionEngine: Tier 0-3 rules,
unknown capability, invalid agent.
"""

from __future__ import annotations

import pytest

from jarvis.agents.engineering_agent import EngineeringAgent
from jarvis.audit import AuditLedger
from jarvis.intake import IntentProcessor, TaskPlanner
from jarvis.orchestrator.task_planner import Tier
from jarvis.permission import PermissionEngine
from jarvis.registry import AgentLifecycleState, AgentRecord, AgentRegistry


def _activate(registry: AgentRegistry, agent_id: str) -> None:
    registry.transition(agent_id, AgentLifecycleState.REVIEWED)
    registry.transition(agent_id, AgentLifecycleState.PROVISIONED)
    registry.transition(agent_id, AgentLifecycleState.ACTIVE)


@pytest.fixture()
def ledger(tmp_path):
    audit_ledger = AuditLedger(storage_path=tmp_path / "ledger.jsonl")
    audit_ledger.connect()
    return audit_ledger


@pytest.fixture()
def registry_with_engineering_agent(ledger):
    registry = AgentRegistry()
    agent = EngineeringAgent()
    registry.register(
        AgentRecord(
            agent_id=agent.agent_id,
            domain=agent.domain,
            parent_domain=None,
            capabilities=agent.capabilities(),
            instance=agent,
        )
    )
    _activate(registry, agent.agent_id)
    return registry


def _plan(ledger, raw_input: str):
    processor = IntentProcessor(audit_ledger=ledger)
    planner = TaskPlanner(audit_ledger=ledger)
    return planner.plan(processor.process(raw_input))


def test_tier0_always_permitted(ledger, registry_with_engineering_agent):
    task = _plan(ledger, "Analyze GitHub repository")
    assert task.tier is Tier.TIER_0_INFORMATIONAL

    engine = PermissionEngine(registry=registry_with_engineering_agent, audit_ledger=ledger)
    decision = engine.evaluate(task, "engineering-agent-001")

    assert decision.allowed is True
    assert decision.required_approval is False


def test_tier1_capability_validation_passes(ledger, registry_with_engineering_agent):
    task = _plan(ledger, "Review and schedule the repository update")
    assert task.tier is Tier.TIER_1_REVERSIBLE_LOW_STAKES

    engine = PermissionEngine(registry=registry_with_engineering_agent, audit_ledger=ledger)
    decision = engine.evaluate(task, "engineering-agent-001")

    assert decision.allowed is True
    assert decision.required_approval is False


def test_tier2_requires_approval(ledger, registry_with_engineering_agent):
    task = _plan(ledger, "Investigate why the commit push to the repository failed")
    assert task.tier is Tier.TIER_2_CONSEQUENTIAL_REVERSIBLE

    engine = PermissionEngine(registry=registry_with_engineering_agent, audit_ledger=ledger)
    decision = engine.evaluate(task, "engineering-agent-001")

    assert decision.allowed is True
    assert decision.required_approval is True
    assert decision.required_tier is Tier.TIER_2_CONSEQUENTIAL_REVERSIBLE


def test_tier3_requires_approval_and_is_flagged_for_confirmation(ledger, registry_with_engineering_agent):
    task = _plan(ledger, "Deploy this to production")
    assert task.tier is Tier.TIER_3_IRREVERSIBLE_OR_HIGH_STAKES

    engine = PermissionEngine(registry=registry_with_engineering_agent, audit_ledger=ledger)
    decision = engine.evaluate(task, "engineering-agent-001")

    assert decision.allowed is True
    assert decision.required_approval is True
    assert decision.required_tier is Tier.TIER_3_IRREVERSIBLE_OR_HIGH_STAKES


def test_unknown_capability_is_denied(ledger, registry_with_engineering_agent):
    task = _plan(ledger, "research recent market signals")  # candidate_agents = ("research", "trading")

    engine = PermissionEngine(registry=registry_with_engineering_agent, audit_ledger=ledger)
    decision = engine.evaluate(task, "engineering-agent-001")

    assert decision.allowed is False
    assert "not recognized" in decision.reason


def test_invalid_agent_is_denied(ledger, registry_with_engineering_agent):
    task = _plan(ledger, "Analyze GitHub repository")

    engine = PermissionEngine(registry=registry_with_engineering_agent, audit_ledger=ledger)
    decision = engine.evaluate(task, "nonexistent-agent-999")

    assert decision.allowed is False
    assert "not registered" in decision.reason


def test_unavailable_agent_is_denied(ledger):
    registry = AgentRegistry()
    agent = EngineeringAgent()
    registry.register(
        AgentRecord(
            agent_id=agent.agent_id,
            domain=agent.domain,
            parent_domain=None,
            capabilities=agent.capabilities(),
            instance=agent,
        )
    )
    # Deliberately NOT activated — still PROPOSED, so not available.
    task = _plan(ledger, "Analyze GitHub repository")

    engine = PermissionEngine(registry=registry, audit_ledger=ledger)
    decision = engine.evaluate(task, agent.agent_id)

    assert decision.allowed is False
    assert "not ACTIVE" in decision.reason


def test_permission_evaluation_is_fully_audited(ledger, registry_with_engineering_agent):
    task = _plan(ledger, "Analyze GitHub repository")
    engine = PermissionEngine(registry=registry_with_engineering_agent, audit_ledger=ledger)
    engine.evaluate(task, "engineering-agent-001")

    event_types = [e.event_type for e in ledger.read_all()]
    assert "permission.requested" in event_types
    assert "permission.granted" in event_types


def test_denied_permission_is_audited(ledger, registry_with_engineering_agent):
    task = _plan(ledger, "Analyze GitHub repository")
    engine = PermissionEngine(registry=registry_with_engineering_agent, audit_ledger=ledger)
    engine.evaluate(task, "nonexistent-agent-999")

    event_types = [e.event_type for e in ledger.read_all()]
    assert "permission.denied" in event_types

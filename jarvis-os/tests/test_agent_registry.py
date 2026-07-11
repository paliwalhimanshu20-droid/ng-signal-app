"""
Tests for jarvis.registry.agent_registry.

Covers JARVIS-002 §16's five-state lifecycle: legal transitions succeed,
illegal transitions (e.g. skipping straight to ACTIVE, or moving
"backwards") fail closed.
"""

from __future__ import annotations

import pytest

from jarvis.registry import AgentLifecycleState, AgentRecord, AgentRegistry, RegistryError


def _make_record(agent_id: str = "test-agent") -> AgentRecord:
    return AgentRecord(
        agent_id=agent_id,
        domain="testing",
        parent_domain=None,
        capabilities=("read",),
    )


def test_register_and_get():
    registry = AgentRegistry()
    registry.register(_make_record())

    record = registry.get("test-agent")
    assert record.lifecycle_state is AgentLifecycleState.PROPOSED
    assert len(registry) == 1


def test_duplicate_registration_raises():
    registry = AgentRegistry()
    registry.register(_make_record())

    with pytest.raises(RegistryError):
        registry.register(_make_record())


def test_get_unknown_agent_raises():
    registry = AgentRegistry()
    with pytest.raises(RegistryError):
        registry.get("nonexistent")


def test_legal_lifecycle_progression():
    registry = AgentRegistry()
    registry.register(_make_record())

    registry.transition("test-agent", AgentLifecycleState.REVIEWED)
    registry.transition("test-agent", AgentLifecycleState.PROVISIONED)
    registry.transition("test-agent", AgentLifecycleState.ACTIVE)

    record = registry.get("test-agent")
    assert record.lifecycle_state is AgentLifecycleState.ACTIVE
    assert record in registry.active_agents()


def test_illegal_transition_skipping_states_raises():
    registry = AgentRegistry()
    registry.register(_make_record())

    with pytest.raises(RegistryError):
        registry.transition("test-agent", AgentLifecycleState.ACTIVE)


def test_illegal_transition_from_deprecated_raises():
    registry = AgentRegistry()
    registry.register(_make_record())
    registry.transition("test-agent", AgentLifecycleState.REVIEWED)
    registry.transition("test-agent", AgentLifecycleState.PROVISIONED)
    registry.transition("test-agent", AgentLifecycleState.ACTIVE)
    registry.transition("test-agent", AgentLifecycleState.DEPRECATED)

    with pytest.raises(RegistryError):
        registry.transition("test-agent", AgentLifecycleState.ACTIVE)


def test_active_agents_excludes_non_active():
    registry = AgentRegistry()
    registry.register(_make_record("proposed-agent"))
    registry.register(_make_record("active-agent"))
    registry.transition("active-agent", AgentLifecycleState.REVIEWED)
    registry.transition("active-agent", AgentLifecycleState.PROVISIONED)
    registry.transition("active-agent", AgentLifecycleState.ACTIVE)

    active_ids = {record.agent_id for record in registry.active_agents()}
    assert active_ids == {"active-agent"}

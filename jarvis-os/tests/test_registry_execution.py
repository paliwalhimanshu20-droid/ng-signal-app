"""
Tests for the Sprint-1B additions to jarvis.registry.AgentRegistry:
unregister, discover_agents, lookup_by_capability, health_status,
is_available. Every Sprint-0 registry test continues to pass unmodified
in tests/test_agent_registry.py — this file covers only the new surface.
"""

from __future__ import annotations

import pytest

from jarvis.agents.models import AgentHealthStatus
from jarvis.registry import AgentLifecycleState, AgentRecord, AgentRegistry, RegistryError


class _StubAgent:
    """Minimal stand-in exposing only what the registry's health_status() needs."""

    def __init__(self, healthy: bool = True):
        self._healthy = healthy

    def health(self) -> AgentHealthStatus:
        return AgentHealthStatus(healthy=self._healthy, detail="stub")


def _make_active_record(agent_id: str, capabilities=("engineering",), instance=None) -> AgentRecord:
    record = AgentRecord(
        agent_id=agent_id,
        domain="engineering",
        parent_domain=None,
        capabilities=capabilities,
        instance=instance,
    )
    return record


def _activate(registry: AgentRegistry, agent_id: str) -> None:
    registry.transition(agent_id, AgentLifecycleState.REVIEWED)
    registry.transition(agent_id, AgentLifecycleState.PROVISIONED)
    registry.transition(agent_id, AgentLifecycleState.ACTIVE)


def test_unregister_removes_agent():
    registry = AgentRegistry()
    registry.register(_make_active_record("a1"))
    registry.unregister("a1")

    with pytest.raises(RegistryError):
        registry.get("a1")


def test_unregister_unknown_agent_raises():
    registry = AgentRegistry()
    with pytest.raises(RegistryError):
        registry.unregister("nonexistent")


def test_hot_swap_via_unregister_then_reregister():
    registry = AgentRegistry()
    registry.register(_make_active_record("a1", instance=_StubAgent(healthy=True)))
    _activate(registry, "a1")

    registry.unregister("a1")
    registry.register(_make_active_record("a1", instance=_StubAgent(healthy=False)))
    _activate(registry, "a1")

    assert registry.get("a1").instance._healthy is False


def test_discover_agents_returns_only_active():
    registry = AgentRegistry()
    registry.register(_make_active_record("proposed-only"))
    registry.register(_make_active_record("fully-active"))
    _activate(registry, "fully-active")

    discovered_ids = {r.agent_id for r in registry.discover_agents()}
    assert discovered_ids == {"fully-active"}


def test_lookup_by_capability_filters_correctly():
    registry = AgentRegistry()
    registry.register(_make_active_record("engineer", capabilities=("engineering", "code-review")))
    registry.register(_make_active_record("researcher", capabilities=("research",)))
    _activate(registry, "engineer")
    _activate(registry, "researcher")

    results = registry.lookup_by_capability("code-review")
    assert {r.agent_id for r in results} == {"engineer"}

    results = registry.lookup_by_capability("nonexistent-capability")
    assert results == ()


def test_health_status_with_no_instance_is_unhealthy():
    registry = AgentRegistry()
    registry.register(_make_active_record("no-instance", instance=None))
    _activate(registry, "no-instance")

    status = registry.health_status("no-instance")
    assert status.healthy is False


def test_health_status_delegates_to_instance():
    registry = AgentRegistry()
    registry.register(_make_active_record("healthy-agent", instance=_StubAgent(healthy=True)))
    registry.register(_make_active_record("unhealthy-agent", instance=_StubAgent(healthy=False)))
    _activate(registry, "healthy-agent")
    _activate(registry, "unhealthy-agent")

    assert registry.health_status("healthy-agent").healthy is True
    assert registry.health_status("unhealthy-agent").healthy is False


def test_is_available_requires_active_and_healthy():
    registry = AgentRegistry()
    registry.register(_make_active_record("not-yet-active", instance=_StubAgent(healthy=True)))
    registry.register(_make_active_record("active-unhealthy", instance=_StubAgent(healthy=False)))
    registry.register(_make_active_record("active-healthy", instance=_StubAgent(healthy=True)))
    _activate(registry, "active-unhealthy")
    _activate(registry, "active-healthy")

    assert registry.is_available("not-yet-active") is False  # still PROPOSED
    assert registry.is_available("active-unhealthy") is False
    assert registry.is_available("active-healthy") is True

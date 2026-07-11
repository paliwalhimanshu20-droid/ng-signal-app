"""
jarvis.orchestrator.agent_router

Design reference: JARVIS-001 §13 (Agent Routing), JARVIS-003 §13 (the
resolved domain hierarchy: Engineering as root with GitHub/Deployment/
Database as children; Research and Trading as independent roots).

Per §13, routing resolves to the most specific registered agent for a
task's domain, escalating to the nearest parent if no agent is registered
at that specificity — never guessing sideways across unrelated domains.
Sprint-0 implements this resolution logic for real against the Agent
Registry; it has nothing to route yet, since no concrete agents are
registered by this sprint, but the resolution algorithm itself is fully
testable today.
"""

from __future__ import annotations

from jarvis.registry import AgentRecord, AgentRegistry


class NoRoutableAgentError(Exception):
    """Raised when no active agent can be resolved for a requested domain."""


class AgentRouter:
    """
    Resolves a requested domain to a specific, ACTIVE registered agent.

    Sprint-0 scope: implements domain-tree resolution against whatever is
    currently in the Agent Registry. Since Sprint-0 registers no concrete
    agents, every call will raise NoRoutableAgentError today — this is the
    correct, honest behavior (fail closed, per JARVIS-001 §3 Principle 4)
    rather than fabricating a routing target that doesn't exist.
    """

    def __init__(self, registry: AgentRegistry) -> None:
        self._registry = registry

    def route(self, domain: str) -> AgentRecord:
        """
        Resolve `domain` to the most specific active agent.

        Resolution order: exact domain match among ACTIVE agents first;
        if none, walk up JARVIS-003 §13's domain tree is a future-sprint
        concern once a real domain hierarchy exists in the Registry beyond
        the flat `parent_domain` field Sprint-0 stores. For now, only
        exact-match resolution is implemented, deliberately kept simple
        until there's a real tree to walk.
        """
        for record in self._registry.active_agents():
            if record.domain == domain:
                return record

        raise NoRoutableAgentError(
            f"No active agent registered for domain '{domain}'. "
            "This is expected in Sprint-0 — no concrete agents are "
            "registered yet."
        )

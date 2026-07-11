"""
jarvis.routing

Sprint-1B: routes a READY_FOR_ROUTING Task to a capable, healthy,
registered agent.

This is a NEW, separate package — not a modification of Sprint-0's
jarvis.orchestrator.agent_router.AgentRouter, which remains exactly as
Sprint-0 left it (a structural skeleton, still wired into
Orchestrator.health_check(), still resolving bare domain strings rather
than real Tasks). That skeleton's own docstring already documents this
choice: real routing logic was always going to arrive as a distinct
sprint's work. jarvis.routing.TaskRouter is that work, operating on the
real Task/BaseAgent types Sprint-1A/1B introduced, composed alongside
the Sprint-0 Orchestrator at the main.py level rather than replacing it.

Design reference: JARVIS-001 §13 (Agent Routing), JARVIS-002 §17 (Agent
Registry), this sprint's explicit requirement: "Never fabricate an
available agent. If no capable agent exists: Return structured failure."
"""

from jarvis.routing.models import RoutingDecision, RoutingStatus
from jarvis.routing.router import TaskRouter

__all__ = ["RoutingDecision", "RoutingStatus", "TaskRouter"]

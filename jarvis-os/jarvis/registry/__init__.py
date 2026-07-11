"""
jarvis.registry

The Agent Registry — single source of truth for every agent's lifecycle
state, domain position, capability ceiling, trust tier, and version
compatibility range.

Design reference: JARVIS-002 §17 (Agent Registry), JARVIS-002 §16 (Agent
Lifecycle), JARVIS-001 §13 (Agent Routing / domain tree), JARVIS-003 §13
(the resolved domain hierarchy: Engineering as root with GitHub/
Deployment/Database as children; Research and Trading as independent
roots).

Sprint-0 scope: this module implements registration, lookup, and the
five-state lifecycle (proposed / reviewed / provisioned / active /
deprecated) as real, working code. It does NOT implement routing
decisions, capability enforcement, or trust-tier computation — those
require the Orchestrator, Permission Engine, and Trust Model
respectively, none of which are in scope for Sprint-0. What Sprint-0
guarantees is that the Registry is queryable and its lifecycle
transitions are correct and audited, which is the foundation everything
else will be built on.
"""

from jarvis.registry.agent_registry import AgentRegistry, RegistryError
from jarvis.registry.models import AgentLifecycleState, AgentRecord

__all__ = [
    "AgentLifecycleState",
    "AgentRecord",
    "AgentRegistry",
    "RegistryError",
]

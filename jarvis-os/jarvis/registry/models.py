"""
jarvis.registry.models

Data models for the Agent Registry.

Design reference: JARVIS-002 §15 (Base Agent Specification), §16 (Agent
Lifecycle), §17 (Agent Registry), §20 (Trust Model); JARVIS-001 §13
(domain tree routing).
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum


class AgentLifecycleState(str, Enum):
    """
    The five-state Agent Lifecycle, per JARVIS-002 §16.

    State transitions are one-directional except where explicitly noted:
    PROPOSED -> REVIEWED -> PROVISIONED -> ACTIVE -> DEPRECATED.

    Sprint-0 implements the states and legal transitions between them; it
    does not implement the governance workflow (Tier 2 review, probation
    monitoring) that should gate real transitions in later sprints — see
    AgentRegistry.transition() for exactly what is and isn't enforced yet.
    """

    PROPOSED = "proposed"
    REVIEWED = "reviewed"
    PROVISIONED = "provisioned"
    ACTIVE = "active"
    DEPRECATED = "deprecated"


# Legal forward transitions, per JARVIS-002 §16. Deliberately a plain dict
# rather than a more elaborate state-machine library — Sprint-0 has no
# need for anything more sophisticated, and JARVIS-001 §3's "boring
# technology" principle argues against introducing one prematurely.
_LEGAL_TRANSITIONS: dict[AgentLifecycleState, tuple[AgentLifecycleState, ...]] = {
    AgentLifecycleState.PROPOSED: (AgentLifecycleState.REVIEWED,),
    AgentLifecycleState.REVIEWED: (AgentLifecycleState.PROVISIONED,),
    AgentLifecycleState.PROVISIONED: (AgentLifecycleState.ACTIVE,),
    AgentLifecycleState.ACTIVE: (AgentLifecycleState.DEPRECATED,),
    AgentLifecycleState.DEPRECATED: (),
}


def is_legal_transition(
    current: AgentLifecycleState, target: AgentLifecycleState
) -> bool:
    """Return whether `current -> target` is a legal lifecycle transition."""
    return target in _LEGAL_TRANSITIONS.get(current, ())


@dataclass
class AgentRecord:
    """
    A single agent's Registry entry.

    Sprint-0 scope: structural fields only, matching JARVIS-002 §15's
    Base Agent Specification and JARVIS-002 §17's Registry contents.
    `capabilities` and `domain` are free-text/simple-structure for now —
    a later sprint should tighten these once the Capability Framework
    (JARVIS-002 §18) and full domain tree (JARVIS-001 §13) are
    implemented beyond Sprint-0's scaffolding.
    """

    agent_id: str
    domain: str
    parent_domain: str | None
    capabilities: tuple[str, ...]
    lifecycle_state: AgentLifecycleState = AgentLifecycleState.PROPOSED
    version: str = "0.0.0"
    trust_tier: str = "provisional"  # provisional | standard | elevated — JARVIS-002 §20
    notes: str = field(default="")

"""
jarvis.routing.models

RoutingDecision: the structured result of a routing attempt — always
returned, never an exception for the ordinary "no capable agent" case,
per this sprint's explicit "return structured failure" requirement.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


class RoutingStatus(str, Enum):
    ROUTED = "routed"
    NO_CAPABLE_AGENT = "no_capable_agent"


@dataclass(frozen=True)
class RoutingDecision:
    """
    Result of TaskRouter.route().

    `candidate_agent_ids` always lists every ACTIVE agent that was
    considered (whether or not selected), so a NO_CAPABLE_AGENT result is
    auditable and debuggable — "no agent could do this" is very different
    information from "no agent exists at all," and this field lets both
    be told apart after the fact.
    """

    task_id: str
    status: RoutingStatus
    selected_agent_id: str | None
    candidate_agent_ids: tuple[str, ...]
    reason: str

"""
jarvis.agents

Base Agent Specification per JARVIS-002 §15.

Sprint-0 scope: this package defines the abstract contract every future
specialist agent (Engineering, GitHub, Research, Trading, ... per
JARVIS-003 Part I) must satisfy to be registrable at all. It contains NO
concrete agents — implementing an actual specialist agent is explicitly
out of scope for the Foundation Bootstrap sprint. What Sprint-0 delivers
is the shape every future agent will be built against.
"""

from jarvis.agents.base import BaseAgent

__all__ = ["BaseAgent"]

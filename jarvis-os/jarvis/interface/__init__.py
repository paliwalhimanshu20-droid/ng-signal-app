"""
jarvis.interface

Sprint-2: the Interface Layer — the first real connection between the
owner and JARVIS. Console only, per this sprint's explicit scope: no
Web UI, no voice, no LLM, no memory.

Every component here talks to the system exclusively through
jarvis.kernel.ExecutiveKernel — nothing in this package imports
jarvis.agents, jarvis.registry, jarvis.routing, jarvis.permission, or
jarvis.approval directly. That boundary is the concrete, checkable form
of this sprint's "never communicate directly with agents" requirement.
"""

from jarvis.interface.console import ConsoleInterface
from jarvis.interface.session import Session, SessionManager, SessionStatus

__all__ = ["ConsoleInterface", "Session", "SessionManager", "SessionStatus"]

"""
jarvis.kernel

Sprint-2: the Executive Kernel — the single point of contact between the
Interface Layer and everything built in Sprint-0 through Sprint-1D
(Intake, Routing, Permission, Approval, Execution).

Design reference: this sprint's explicit mandate — "Never communicate
directly with agents. Always communicate through the Executive Kernel."
The Kernel does not implement any new governance or execution logic; it
composes the already-built, already-tested pipeline from prior sprints
behind one clean, minimal API surface (submit_request, resume,
health_reports) so the Interface Layer never needs to know that a
Registry, a Router, a PermissionEngine, and an ApprovalEngine exist
separately underneath it.
"""

from jarvis.kernel.kernel import ExecutiveKernel

__all__ = ["ExecutiveKernel"]

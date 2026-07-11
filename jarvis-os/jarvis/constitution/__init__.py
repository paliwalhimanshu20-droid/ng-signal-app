"""
jarvis.constitution

Loads and structurally validates the JARVIS Constitution reference this
Core instance is running against.

Per JARVIS-001 §2 and §7, this is the FIRST thing Bootstrap does, and a
failure here halts startup entirely — no other subsystem is permitted to
initialize before the Constitution reference is confirmed present and
structurally intact (all seven Articles accounted for).

This module intentionally does not interpret or enforce the Constitution's
content — it only confirms the reference JARVIS Core is operating under is
real, versioned, and complete. Interpretation and enforcement are the
Orchestration Layer's and, eventually, the Permission/Approval/Audit
Engines' job (JARVIS-001 §1 scopes those internals out of Core).
"""

from jarvis.constitution.loader import (
    Article,
    Constitution,
    ConstitutionValidationError,
    load_constitution,
)

__all__ = [
    "Article",
    "Constitution",
    "ConstitutionValidationError",
    "load_constitution",
]

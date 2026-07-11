"""
jarvis.permission

Sprint-1C: the Permission Engine — the first gate every routed Task must
pass through before execution, per Article V and this sprint's explicit
mandate ("No execution may bypass Permission Engine").

Design reference: JARVIS-001 §11 (Tier definitions), Article II (the
floor beneath sovereignty — capability/agent validity is checked
independently here, never assumed from a prior stage like routing).
"""

from jarvis.permission.engine import PermissionEngine
from jarvis.permission.models import PermissionDecision, PermissionRequest

__all__ = ["PermissionDecision", "PermissionEngine", "PermissionRequest"]

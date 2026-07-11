"""
jarvis.audit

Audit Ledger connectivity for JARVIS Core.

Design reference: JARVIS-001 §16 (Audit Framework Integration), Article IV
(Auditability), JARVIS-001 §7 Step 2 (Bootstrap establishes Audit Ledger
connectivity before anything else that could produce an auditable event).

Sprint-0 scope: this module implements ONLY connectivity and the ability
to write a structural "system event" entry (e.g. "bootstrap started",
"bootstrap completed") — it does NOT implement task-graph audit writes,
approval-decision audit writes, or any of the rich, tiered audit content
JARVIS-001 §16 eventually requires. Those arrive with the Orchestrator's
real request lifecycle and the Approval Engine, both out of scope for
Sprint-0 per this task's explicit instructions.

The one property this module DOES fully implement, because Bootstrap
depends on it: the Ledger's completeness must never depend on
jarvis.logging_ having been configured correctly (JARVIS-001 §20). This
module uses its own storage path and never routes through the logging
subsystem for its actual writes — only for its own diagnostic messages
about itself, which is a permitted, deliberate distinction.
"""

from jarvis.audit.ledger import AuditLedger, AuditLedgerError, LedgerEntry

__all__ = ["AuditLedger", "AuditLedgerError", "LedgerEntry"]

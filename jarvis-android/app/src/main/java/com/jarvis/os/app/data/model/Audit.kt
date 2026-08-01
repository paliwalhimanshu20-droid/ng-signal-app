package com.jarvis.os.app.data.model

import java.time.Instant

/**
 * Sprint 11 Governance "View Audit" deliverable -- one normalized,
 * append-only record of anything CoreEvent already represents.
 * Deliberately NOT a replacement for ApprovalRepository.auditLog
 * (which stays the authoritative, richer record for approval state
 * specifically -- see that file's docstring on the append-only
 * guarantee) -- this is a flattened, cross-domain VIEW built from the
 * same CoreEvent stream every part of the app already emits into, so
 * an Executive Dashboard or Audit screen has one list to read instead
 * of separately merging ApprovalRepository.auditLog,
 * ConnectionRepository.transitions and ToolRepository.executionLog by
 * hand.
 */
data class AuditEntry(
    val entryId: String,
    val timestamp: Instant,
    val category: String,
    val summary: String,
)

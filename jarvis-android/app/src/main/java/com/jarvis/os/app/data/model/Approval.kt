package com.jarvis.os.app.data.model

import java.time.Instant

/** Mirrors the shape of Sprint-1D's ApprovalRequest (Python) plus Sprint-6's connection-approval flow — Part 9 explicitly needs BOTH permission requests (Tier 2/3 task approvals) and connection requests represented in one Approval Center list. */
enum class ApprovalKind { PERMISSION_REQUEST, CONNECTION_REQUEST }

/**
 * Sprint 9 Final: the full 6-state governance machine. WAITING was
 * renamed PENDING to match this sprint's own vocabulary; APPROVED,
 * REJECTED and EXPIRED already existed, CANCELLED and REVOKED are new.
 * See ApprovalRepository.allowedTransitions for the full graph --
 * notably REVOKED -> APPROVED is legal ("Approve Again" after a
 * revoke), which is the one transition that isn't simply "PENDING
 * fans out, then terminal."
 */
enum class ApprovalOutcome { PENDING, APPROVED, REJECTED, CANCELLED, EXPIRED, REVOKED }

/**
 * @param relatedConnectionId When this approval concerns a specific
 * connection request (kind == CONNECTION_REQUEST), the id of that
 * Connection -- lets JarvisCore react automatically (see its
 * docstring's "Connections react automatically" section) and lets the
 * audit trail show which provider an entry was about. Null for
 * PERMISSION_REQUEST approvals, which aren't about any one connection.
 * Also null for the three approvals seeded before this PR existed --
 * see ApprovalRepository's docstring for why those aren't
 * retroactively linked.
 */
data class ApprovalItem(
    val approvalId: String,
    val kind: ApprovalKind,
    val title: String,
    val reason: String,
    val riskLevel: RiskLevel,
    val outcome: ApprovalOutcome,
    val createdAt: Instant,
    val resolvedAt: Instant?,
    val resolvedBy: String?,
    val relatedConnectionId: String? = null,
)

/**
 * Sprint 9 Final: one permanent, append-only record of a single state
 * change. ApprovalRepository never updates or removes an existing
 * record -- every action adds a new one, so an approval's full history
 * (create, approve, revoke, approve-again, ...) is reconstructable by
 * filtering this list by approvalId and sorting by timestamp. See that
 * class's docstring for the append-only guarantee's enforcement.
 *
 * @param previousState Null only for the record created alongside a
 * brand-new approval (there's no "previous" state before PENDING).
 * @param metadata Free-form extra context a specific transition might
 * want to carry (e.g. which permission scope triggered a revoke).
 * Empty for most transitions today -- present in the shape now so a
 * real backend's richer audit payload has somewhere to land later
 * without another schema change.
 */
data class ApprovalAuditRecord(
    val recordId: String,
    val approvalId: String,
    val timestamp: Instant,
    val actor: String,
    val provider: String?,
    val previousState: ApprovalOutcome?,
    val newState: ApprovalOutcome,
    val reason: String?,
    val metadata: Map<String, String> = emptyMap(),
)

/** Mirrors jarvis.intelligence.models.RiskLevel (Sprint-4, Python) exactly. */
enum class RiskLevel { LOW, MODERATE, HIGH, CRITICAL }

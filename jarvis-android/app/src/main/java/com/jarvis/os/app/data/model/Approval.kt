package com.jarvis.os.app.data.model

import java.time.Instant

/** Mirrors the shape of Sprint-1D's ApprovalRequest (Python) plus Sprint-6's connection-approval flow — Part 9 explicitly needs BOTH permission requests (Tier 2/3 task approvals) and connection requests represented in one Approval Center list. */
enum class ApprovalKind { PERMISSION_REQUEST, CONNECTION_REQUEST }

enum class ApprovalOutcome { WAITING, APPROVED, REJECTED, EXPIRED }

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
)

/** Mirrors jarvis.intelligence.models.RiskLevel (Sprint-4, Python) exactly. */
enum class RiskLevel { LOW, MODERATE, HIGH, CRITICAL }

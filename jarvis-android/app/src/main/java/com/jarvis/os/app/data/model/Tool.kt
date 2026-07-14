package com.jarvis.os.app.data.model

import java.time.Instant

// --- Sprint 10: Tool Framework ------------------------------------------------------

enum class ToolHealthStatus { HEALTHY, DEGRADED, UNAVAILABLE }

/**
 * @param riskLevel Drives the approval gate: LOW never requires an
 * approval, MODERATE and above always do -- see ToolRepository.execute.
 * Reuses RiskLevel rather than inventing a tool-specific scale, same
 * reasoning as Project.priority (see Domain.kt).
 */
data class ToolDefinition(
    val toolId: String,
    val name: String,
    val description: String,
    val riskLevel: RiskLevel,
) {
    val requiresApproval: Boolean get() = riskLevel != RiskLevel.LOW
}

/**
 * One permanent, append-only record of a single tool invocation --
 * same "never updated, never removed" guarantee ApprovalAuditRecord
 * and ConnectionRepository's transitions give their own histories, so
 * ToolRepository's execution log can double as part of the audit trail
 * a governance/executive dashboard reads.
 */
data class ToolExecutionRecord(
    val recordId: String,
    val toolId: String,
    val input: String,
    val outputSummary: String,
    val success: Boolean,
    val timestamp: Instant,
    val approvalId: String? = null,
)

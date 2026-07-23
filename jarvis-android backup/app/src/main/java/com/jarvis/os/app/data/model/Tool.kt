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
    /**
     * Sprint 15 "Executive Intelligence Completion" Phase 8: words or
     * short phrases that mean "run me" in a chat message, owned by the
     * tool itself. IntentRouter iterates ToolRepository.discover() and
     * checks THIS field -- it has no hardcoded per-connector table.
     * That's what makes "a new connector should only register
     * capability/intent/permissions/execution, JarvisCore should not
     * require modifications" (this sprint's Phase 8 wording) literally
     * true: a new Tool with its own triggerKeywords is chat-routable
     * the moment it's bound in ToolModule -- zero edits to
     * IntentRouter, JarvisCore, or any enum of intents.
     *
     * Empty (the default) means this tool never auto-runs from chat --
     * e.g. a calculator, where there's no reliable way to extract the
     * exact input from a full sentence and a wrong guess failing would
     * look like a fake failure (see IntentRouter's own docstring). Only
     * give a tool real keywords if it's safe to invoke with the raw
     * message text as its input, the way every *StatusTool in
     * ConnectorTools.kt is.
     */
    val triggerKeywords: Set<String> = emptySet(),
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

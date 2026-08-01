package com.jarvis.os.app.core

import com.jarvis.os.app.data.model.AuditEntry
import java.time.Instant
import java.util.UUID

/**
 * Sprint 11 Governance: the ONLY place a CoreEvent becomes an
 * AuditEntry, mirroring NotificationFactory's shape exactly (see that
 * object's docstring) but with no `null` case -- every CoreEvent is
 * audit-worthy even when it isn't notification-worthy (e.g.
 * ChatMessageSent has no Notification but is still a real audit trail
 * entry of what was said and when).
 */
object AuditFactory {
    fun from(event: CoreEvent): AuditEntry = AuditEntry(
        entryId = UUID.randomUUID().toString(),
        timestamp = Instant.now(),
        category = event.categoryLabel(),
        summary = event.summaryLabel(),
    )

    private fun CoreEvent.categoryLabel(): String = when (this) {
        is CoreEvent.ApprovalRequested, is CoreEvent.ApprovalStatusChanged -> "Approval"
        is CoreEvent.ConnectionStatusChanged -> "Connection"
        is CoreEvent.ChatMessageSent, is CoreEvent.ChatResponseReceived -> "Chat"
        is CoreEvent.TaskStatusChanged -> "Task"
        is CoreEvent.ToolExecuted, is CoreEvent.ToolStarted -> "Tool"
        is CoreEvent.TradingRecommendationIssued -> "Trading"
        is CoreEvent.LocalIntentResolved -> "Local Intent Router"
    }

    private fun CoreEvent.summaryLabel(): String = when (this) {
        is CoreEvent.ApprovalRequested -> "Approval requested: $summary"
        is CoreEvent.ApprovalStatusChanged -> "Approval '$title' -> $newState (by $actor)"
        is CoreEvent.ConnectionStatusChanged -> "$providerName: $previousStatus -> $newStatus"
        is CoreEvent.ChatMessageSent -> "Message sent in session $sessionId"
        is CoreEvent.ChatResponseReceived -> "Response received in session $sessionId"
        is CoreEvent.TaskStatusChanged -> "Task $taskId marked ${if (done) "done" else "not done"}"
        is CoreEvent.ToolExecuted -> "Tool '$toolId' ${if (success) "succeeded" else "failed"}: $summary"
        is CoreEvent.ToolStarted -> "Tool '$toolId' ($toolName) started"
        is CoreEvent.TradingRecommendationIssued -> "Recommendation #$recommendationId issued for instrument $instrumentId"
        is CoreEvent.LocalIntentResolved -> "Answered locally via $domain (no AI provider called): $summary"
    }
}

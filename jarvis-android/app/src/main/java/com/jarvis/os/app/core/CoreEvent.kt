package com.jarvis.os.app.core

import com.jarvis.os.app.data.model.ConnectionStatus

/**
 * Sprint-8: cross-cutting events any part of the app can react to
 * without a direct dependency on whichever feature produced them.
 * Sprint 8.1 activates the ChatMessageSent to ChatResponseReceived
 * pair end to end -- see JarvisCore.sendChatMessage and
 * ChatViewModel's event collection.
 *
 * Sprint 9 (PR1) enriches ConnectionStatusChanged rather than adding
 * one CoreEvent subtype per transition (ConnectionApproved,
 * ConnectionSuspended, etc.). A future NotificationFactory can derive
 * category/message from `newStatus` the same way a `when` over the
 * enum already does in ConnectionsScreen -- adding seven near-identical
 * event classes would be the exact "duplicated state between modules"
 * Sprint 9 Section 5 warns against, since the state already lives on
 * ConnectionStatus. previousStatus and reason are carried so a listener
 * can tell "APPROVED -> CONNECTING" from "CONNECTED -> ERROR" without
 * re-reading ConnectionRepository.
 */
sealed interface CoreEvent {
    data class ApprovalRequested(val approvalId: String, val summary: String) : CoreEvent
    data class ConnectionStatusChanged(
        val connectionId: String,
        val providerName: String,
        val previousStatus: ConnectionStatus,
        val newStatus: ConnectionStatus,
        val reason: String? = null,
    ) : CoreEvent
    data class ChatMessageSent(val sessionId: String, val text: String) : CoreEvent
    data class ChatResponseReceived(val sessionId: String, val messageId: String) : CoreEvent
    data class TaskStatusChanged(val taskId: String, val done: Boolean) : CoreEvent
}

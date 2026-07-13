package com.jarvis.os.app.core

import com.jarvis.os.app.data.model.ConnectionStatus

/**
 * Sprint-8: cross-cutting events any part of the app can react to
 * without a direct dependency on whichever feature produced them.
 * Sprint 8.1 activates the ChatMessageSent to ChatResponseReceived
 * pair end to end -- see JarvisCore.sendChatMessage and
 * ChatViewModel's event collection.
 */
sealed interface CoreEvent {
    data class ApprovalRequested(val approvalId: String, val summary: String) : CoreEvent
    data class ConnectionStatusChanged(val connectionId: String, val status: ConnectionStatus) : CoreEvent
    data class ChatMessageSent(val sessionId: String, val text: String) : CoreEvent
    data class ChatResponseReceived(val sessionId: String, val messageId: String) : CoreEvent
    data class TaskStatusChanged(val taskId: String, val done: Boolean) : CoreEvent
}

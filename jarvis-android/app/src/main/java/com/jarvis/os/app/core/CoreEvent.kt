package com.jarvis.os.app.core

import com.jarvis.os.app.data.model.ConnectionStatus

/**
 * Sprint-8: cross-cutting events any part of the app can react to
 * without a direct dependency on whichever feature produced them — for
 * example, a future notification surface could react to
 * ApprovalRequested without NotificationsViewModel depending on
 * ApprovalRepository directly. This is new in this sprint; nothing
 * previously existing produced or consumed these events, so there is
 * no migration burden, only additive wiring as consumers are added.
 *
 * A sealed interface (not a sealed class with no state, not a plain
 * enum) because every event genuinely carries different data — the
 * same pattern already used for MessageContentKind/DashboardCardId
 * elsewhere in this codebase's preference for exhaustive `when` over
 * loosely-typed event payloads.
 */
sealed interface CoreEvent {
    data class ApprovalRequested(val approvalId: String, val summary: String) : CoreEvent
    data class ConnectionStatusChanged(val connectionId: String, val status: ConnectionStatus) : CoreEvent
    data class ChatMessageReceived(val sessionId: String, val messageId: String) : CoreEvent
    data class TaskStatusChanged(val taskId: String, val done: Boolean) : CoreEvent
}

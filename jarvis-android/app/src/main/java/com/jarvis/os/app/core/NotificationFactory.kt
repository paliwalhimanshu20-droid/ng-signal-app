package com.jarvis.os.app.core

import com.jarvis.os.app.data.model.ConnectionStatus
import com.jarvis.os.app.data.model.Notification
import com.jarvis.os.app.data.model.NotificationCategory
import com.jarvis.os.app.data.model.NotificationPriority
import java.time.Instant
import java.util.UUID

/**
 * Sprint 9 (PR2): the ONLY place a CoreEvent becomes (or doesn't
 * become) a Notification. JarvisCore's event collector (see its init
 * block) calls `from(event)` and inserts whatever comes back into
 * NotificationRepository -- see that interface's docstring for why
 * that collector is the sole call site of `insert`, which makes this
 * the sole place notification *content* is decided. A `when` with no
 * `else` branch means adding a new CoreEvent subtype without deciding
 * what it means here is a compile error, not a silently-dropped event.
 *
 * Honesty note (explicit per this sprint's own instruction not to
 * simulate a capability that doesn't exist): CoreEvent has no
 * AiProviderUnavailable or SystemWarning variant yet, because nothing
 * in this codebase currently detects either condition -- there is no
 * real AI-router health signal and no real system-health signal to
 * publish from. NotificationCategory.AI, .WARNING and .SYSTEM exist
 * and this factory would need one more `is CoreEvent.Whatever ->`
 * branch each to light up, but nothing fabricates that event today.
 * Likewise ApprovalRequested is handled below but nothing currently
 * publishes it (ApprovalRepository's items are seeded at construction,
 * not created through a live "request" action any screen exposes) --
 * see ApprovalRepository's docstring. The category taxonomy and this
 * factory are ready for both the day a real trigger exists.
 */
object NotificationFactory {

    fun from(event: CoreEvent): Notification? = when (event) {
        is CoreEvent.ApprovalRequested -> Notification(
            notificationId = UUID.randomUUID().toString(),
            category = NotificationCategory.APPROVAL,
            priority = NotificationPriority.NORMAL,
            title = "Approval requested",
            message = event.summary,
            timestamp = Instant.now(),
            source = "Approvals",
            relatedEntityId = event.approvalId,
        )
        is CoreEvent.ConnectionStatusChanged -> fromConnectionStatusChanged(event)
        // Sent/Received drive the chat typing indicator (Sprint 8.1) and
        // aren't notification-worthy on their own -- a chat message isn't
        // something that happened while you were looking elsewhere, it's
        // something you're actively looking at right now.
        is CoreEvent.ChatMessageSent -> null
        is CoreEvent.ChatResponseReceived -> null
        is CoreEvent.TaskStatusChanged -> null
    }

    private fun fromConnectionStatusChanged(event: CoreEvent.ConnectionStatusChanged): Notification? {
        val (title, category, priority) = when (event.newStatus) {
            ConnectionStatus.CONNECTED -> Triple("${event.providerName} connected", NotificationCategory.CONNECTION, NotificationPriority.NORMAL)
            ConnectionStatus.SUSPENDED -> Triple("${event.providerName} suspended", NotificationCategory.CONNECTION, NotificationPriority.NORMAL)
            ConnectionStatus.ERROR -> Triple("${event.providerName} connection error", NotificationCategory.ERROR, NotificationPriority.HIGH)
            ConnectionStatus.REJECTED -> Triple("${event.providerName} connection rejected", NotificationCategory.CONNECTION, NotificationPriority.NORMAL)
            ConnectionStatus.DISCONNECTED -> Triple("${event.providerName} disconnected", NotificationCategory.CONNECTION, NotificationPriority.LOW)
            // PENDING_APPROVAL, APPROVED and CONNECTING are expected
            // intermediate hops, not outcomes -- the spec's own trigger
            // list only names "connected / suspended / error", and
            // surfacing every hop of a normal approve-then-connect
            // sequence would be noise a real notification center
            // shouldn't produce.
            ConnectionStatus.PENDING_APPROVAL, ConnectionStatus.APPROVED, ConnectionStatus.CONNECTING -> return null
        }
        return Notification(
            notificationId = UUID.randomUUID().toString(),
            category = category,
            priority = priority,
            title = title,
            message = event.reason ?: "Status changed to ${event.newStatus}.",
            timestamp = Instant.now(),
            source = "Connections",
            relatedEntityId = event.connectionId,
        )
    }
}

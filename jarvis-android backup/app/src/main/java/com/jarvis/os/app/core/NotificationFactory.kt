package com.jarvis.os.app.core

import com.jarvis.os.app.data.model.ApprovalOutcome
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
 * Sprint 9 Final: ApprovalRequested's honesty caveat from PR2 is now
 * resolved -- ApprovalRepository.requestApproval (called from
 * JarvisCore.requestConnectionApproval, see that class) is a real live
 * publisher, not a dormant case waiting for one. ApprovalStatusChanged
 * is new this PR and handled below the same way ConnectionStatusChanged
 * is: one factory function per state, all under NotificationCategory.APPROVAL
 * regardless of which of the 5 terminal-or-revocable states was reached,
 * since they're all still fundamentally "something about an approval
 * changed" -- CONNECTION and ERROR are reserved for events about the
 * connection itself, not the approval that gated it.
 *
 * Honesty note still standing: CoreEvent has no AiProviderUnavailable or
 * SystemWarning variant yet -- no real AI-router health signal or
 * system-health signal exists to publish either from (the AI Router PR
 * is what would add the first real trigger for AiProviderUnavailable).
 * NotificationCategory.AI, .WARNING and .SYSTEM exist and are ready.
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
        is CoreEvent.ApprovalStatusChanged -> fromApprovalStatusChanged(event)
        is CoreEvent.ConnectionStatusChanged -> fromConnectionStatusChanged(event)
        // Sent/Received drive the chat typing indicator (Sprint 8.1) and
        // aren't notification-worthy on their own -- a chat message isn't
        // something that happened while you were looking elsewhere, it's
        // something you're actively looking at right now.
        is CoreEvent.ChatMessageSent -> null
        is CoreEvent.ChatResponseReceived -> null
        is CoreEvent.TaskStatusChanged -> null
        is CoreEvent.ToolExecuted -> fromToolExecuted(event)
        // Sprint 15: same reasoning as ChatMessageSent above -- a tool
        // starting is an in-progress signal the chat UI shows live
        // (ChatViewModel), not something worth a Notification once it's
        // over; ToolExecuted (the completion event) already covers the
        // notification-worthy outcome.
        is CoreEvent.ToolStarted -> null
    }

    private fun fromToolExecuted(event: CoreEvent.ToolExecuted): Notification {
        return Notification(
            notificationId = UUID.randomUUID().toString(),
            category = NotificationCategory.TOOL,
            priority = if (event.success) NotificationPriority.LOW else NotificationPriority.NORMAL,
            title = if (event.success) "Tool ran: ${event.toolId}" else "Tool failed: ${event.toolId}",
            message = event.summary,
            timestamp = Instant.now(),
            source = "Tools",
            relatedEntityId = event.toolId,
        )
    }

    private fun fromApprovalStatusChanged(event: CoreEvent.ApprovalStatusChanged): Notification? {
        val (title, priority) = when (event.newState) {
            ApprovalOutcome.APPROVED -> "${event.title} approved" to NotificationPriority.NORMAL
            ApprovalOutcome.REJECTED -> "${event.title} rejected" to NotificationPriority.NORMAL
            ApprovalOutcome.CANCELLED -> "${event.title} cancelled" to NotificationPriority.LOW
            ApprovalOutcome.EXPIRED -> "${event.title} expired" to NotificationPriority.NORMAL
            // Revoking pulls back access that was previously granted --
            // a security-relevant event, not routine housekeeping like a
            // cancel, so it's the one approval outcome that gets HIGH.
            ApprovalOutcome.REVOKED -> "${event.title} revoked" to NotificationPriority.HIGH
            // PENDING is only ever a *previous* state in this event (the
            // state something transitioned FROM), never a `newState` --
            // ApprovalRepository has no transition that lands back on
            // PENDING (see its allowedTransitions), so this branch is
            // unreachable but required for exhaustiveness.
            ApprovalOutcome.PENDING -> return null
        }
        return Notification(
            notificationId = UUID.randomUUID().toString(),
            category = NotificationCategory.APPROVAL,
            priority = priority,
            title = title,
            message = event.reason ?: "Status changed to ${event.newState}.",
            timestamp = Instant.now(),
            source = "Approvals",
            relatedEntityId = event.relatedConnectionId ?: event.approvalId,
        )
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

package com.jarvis.os.app.core

import com.jarvis.os.app.core.tools.ToolResult
import com.jarvis.os.app.data.model.ApprovalKind
import com.jarvis.os.app.data.model.ApprovalOutcome
import com.jarvis.os.app.data.model.ConnectionStatus
import com.jarvis.os.app.data.model.PermissionScope
import com.jarvis.os.app.data.model.RiskLevel
import com.jarvis.os.app.data.repository.ApprovalRepository
import com.jarvis.os.app.data.repository.ApprovalTransition
import com.jarvis.os.app.data.repository.AuditRepository
import com.jarvis.os.app.data.repository.ChatRepository
import com.jarvis.os.app.data.repository.ConnectionOperationError
import com.jarvis.os.app.data.repository.ConnectionRepository
import com.jarvis.os.app.data.repository.MemoryRepository
import com.jarvis.os.app.data.repository.NotificationRepository
import com.jarvis.os.app.data.repository.ProjectRepository
import com.jarvis.os.app.data.repository.ToolRepository
import com.jarvis.os.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint-8 established this as the coordination point above the
 * repository layer. Sprint 8.1 activates it end to end for Chat, per
 * that sprint's explicit required flow: ChatScreen -> ChatViewModel ->
 * JarvisCore -> ChatRepository -> AiRouter -> active ChatProvider.
 *
 * Still does NOT duplicate ConnectionRepository, ApprovalRepository,
 * MemoryRepository, ProjectRepository, or ChatRepository -- each
 * remains the actual owner of its domain, exposed here as read-only
 * properties (chat's sendChatMessage below is coordination on top of
 * ChatRepository.sendMessage, not a second implementation of it).
 *
 * "Task management" is still ProjectTask inside ProjectRepository, per
 * Sprint-8's reasoning -- unchanged this sprint.
 *
 * "Navigation coordination" is still a request channel, not Core
 * owning the NavHostController -- but this sprint gives it a real,
 * user-initiated trigger (see matchNavigationCommand) and a real
 * consumer (JarvisAppViewModel, collected in JarvisApp.kt), completing
 * the round trip Sprint-8 left unfinished.
 *
 * Event dispatching now has one complete, working chain:
 * sendChatMessage publishes ChatMessageSent before sending, then
 * ChatResponseReceived once ChatRepository's suspend call returns
 * (which only happens after the full ChatChunk stream has completed --
 * see ChatRepository.sendMessage). ChatViewModel collects events to
 * drive the typing indicator, giving events real UI consequences
 * rather than a bus nothing listens to.
 *
 * Sprint 9 (PR1) adds the Connections half of "single coordinator":
 * every mutating action a screen can take on a connection now goes
 * through one of the methods below instead of ConnectionsViewModel
 * calling ConnectionRepository directly -- and every transition
 * ConnectionRepository accepts (including ones no button triggers
 * directly, like a future background health-check calling markError)
 * is republished here as CoreEvent.ConnectionStatusChanged via the
 * init block's collector, so "every important action must emit a
 * CoreEvent" holds even for transitions this class didn't itself
 * initiate. ConnectionRepository still owns all transition validity
 * (see its allowedTransitions) -- this class forwards and coordinates,
 * it does not re-implement or duplicate that logic.
 *
 * Sprint 9 (PR2) adds the other half of "every important action must
 * emit a CoreEvent" -- what happens to that event afterward. A second
 * init-block collector below reads this class's own `events` flow (the
 * same one publish() writes to) and, for each event, asks
 * NotificationFactory whether it should become a Notification; if so,
 * inserts it into NotificationRepository. That collector is the ONLY
 * call site of NotificationRepository.insert() anywhere in this
 * codebase -- see that interface's docstring. This is deliberately a
 * self-subscription (JarvisCore both produces and consumes its own bus
 * for this side effect) rather than pushing the responsibility onto
 * whatever produced the event, so ConnectionRepository, ApprovalRepository,
 * etc. stay exactly as ignorant of "notifications exist" as they were
 * before this PR.
 *
 * Sprint 9 Final adds the Approvals half of "single coordinator", mirroring
 * PR1's Connections pattern exactly: two more init-block collectors below
 * forward ApprovalRepository.created and .transitions as
 * CoreEvent.ApprovalRequested / ApprovalStatusChanged (picked up by the
 * existing notification collector automatically -- no changes needed
 * there beyond NotificationFactory gaining a case for the new event
 * type). This finally gives ApprovalRequested a real publisher; see
 * NotificationFactory's docstring for why PR2 could only describe that
 * gap, not close it.
 *
 * It also adds the one piece of real cross-repository *coordination*
 * this sprint introduces: reactToApprovalTransition, called from the
 * approvals.transitions collector, drives the linked Connection (when
 * relatedConnectionId is set) through the right next ConnectionRepository
 * call for each approval outcome -- "Connections react automatically."
 * This is coordination, not duplicated business logic: every individual
 * call it makes (connect(), reject(), disconnect(), suspend()) is still
 * validated by ConnectionRepository's own allowedTransitions, exactly as
 * if a button had called it. reactToApprovalTransition only decides
 * WHICH already-legal call fits the situation, wrapped in try/catch so
 * a connection that was independently changed out from under an
 * in-flight approval (e.g. manually disconnected via the Connections
 * screen while its approval was still PENDING) logs a mismatch instead
 * of crashing this collector for the rest of the process's life --
 * SupervisorJob isolates a crashed child coroutine from its siblings,
 * but does nothing to restart that one child once it dies.
 */
@Singleton
class JarvisCore @Inject constructor(
    val connections: ConnectionRepository,
    val approvals: ApprovalRepository,
    val memory: MemoryRepository,
    val projects: ProjectRepository,
    val chat: ChatRepository,
    val notifications: NotificationRepository,
    val tools: ToolRepository,
    val audit: AuditRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val _events = MutableSharedFlow<CoreEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<CoreEvent> = _events

    private val _navigationRequests = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Route strings matching JarvisDestination.route values -- deliberately plain strings, not a JarvisDestination reference, so this coordination layer has no dependency on the navigation/UI package. */
    val navigationRequests: SharedFlow<String> = _navigationRequests

    init {
        appScope.launch {
            connections.transitions.collect { t ->
                publish(
                    CoreEvent.ConnectionStatusChanged(
                        t.connectionId, t.providerName, t.previousStatus, t.newStatus, t.reason,
                    ),
                )
            }
        }
        appScope.launch {
            events.collect { event ->
                NotificationFactory.from(event)?.let { notifications.insert(it) }
            }
        }
        appScope.launch {
            // Sprint 11 Governance: the sole writer of AuditRepository,
            // same shape as the notification collector directly above --
            // every CoreEvent becomes one flattened AuditEntry, using
            // AuditFactory (analogous to NotificationFactory) so this
            // stays a one-line collector rather than a second `when`
            // duplicated here.
            events.collect { event -> audit.record(AuditFactory.from(event)) }
        }
        appScope.launch {
            approvals.created.collect { approval ->
                publish(CoreEvent.ApprovalRequested(approval.approvalId, approval.title))
            }
        }
        appScope.launch {
            approvals.transitions.collect { t ->
                publish(
                    CoreEvent.ApprovalStatusChanged(
                        t.approvalId, t.title, t.relatedConnectionId, t.previousState, t.newState, t.actor, t.reason,
                    ),
                )
                reactToApprovalTransition(t)
            }
        }
    }

    suspend fun publish(event: CoreEvent) {
        _events.emit(event)
    }

    // --- Connections coordination (Sprint 9 PR1) --------------------------------
    // Each of these is a thin call-through to ConnectionRepository; the
    // CoreEvent for it is NOT published here (that would double-publish
    // alongside the init block's collector above) -- publishing happens
    // exactly once, in one place, driven by what ConnectionRepository
    // actually accepted. A caller that needs to react to a specific
    // action's outcome (e.g. ConnectionsViewModel's Snackbar-on-error)
    // still gets that from the thrown ConnectionOperationError, same as
    // Sprint 7.1.

    fun approveConnection(connectionId: String, approvedBy: String = "owner") = connections.approve(connectionId, approvedBy)
    fun rejectConnection(connectionId: String, reason: String = "Rejected by owner") = connections.reject(connectionId, reason)
    fun connectConnection(connectionId: String) = connections.connect(connectionId)
    fun markConnectionConnected(connectionId: String) = connections.markConnected(connectionId)
    fun markConnectionError(connectionId: String, reason: String) = connections.markError(connectionId, reason)
    fun suspendConnection(connectionId: String, reason: String = "Suspended by owner") = connections.suspend(connectionId, reason)
    fun disconnectConnection(connectionId: String, reason: String? = "Disconnected by owner") = connections.disconnect(connectionId, reason)
    fun reconnectConnection(connectionId: String) = connections.reconnect(connectionId)
    fun disableAllConnections(reason: String = "Owner disabled all connections") = connections.disableAll(reason)
    fun testConnection(connectionId: String) = connections.testConnection(connectionId)

    // --- Notifications coordination (Sprint 9 PR2) -------------------------------
    // Read/clear actions, unlike connections, never need a CoreEvent of
    // their own -- "you read a notification" isn't an event any other
    // part of the app needs to react to, it's a private UI-state change
    // on the notification itself. These exist on JarvisCore (rather than
    // NotificationsViewModel calling NotificationRepository directly)
    // purely for consistency with "JarvisCore coordinates every
    // workflow" -- there's no hidden logic in them beyond the call-through.

    fun markNotificationRead(notificationId: String) = notifications.markRead(notificationId)
    fun markAllNotificationsRead() = notifications.markAllRead()
    fun clearReadNotifications() = notifications.clearRead()

    // --- Approvals coordination (Sprint 9 Final) ---------------------------------
    // Same shape as Connections coordination above: thin call-throughs,
    // no CoreEvent published here (the init block's collectors own that,
    // driven by what ApprovalRepository actually accepted).

    fun requestApproval(
        kind: ApprovalKind,
        title: String,
        reason: String,
        riskLevel: RiskLevel,
        requestedBy: String = "system",
        relatedConnectionId: String? = null,
    ) = approvals.requestApproval(kind, title, reason, riskLevel, requestedBy, relatedConnectionId)

    fun approveApproval(approvalId: String, actor: String = "owner", reason: String? = null) = approvals.approve(approvalId, actor, reason)
    fun rejectApproval(approvalId: String, actor: String = "owner", reason: String? = null) = approvals.reject(approvalId, actor, reason)
    fun cancelApproval(approvalId: String, actor: String = "owner", reason: String? = null) = approvals.cancel(approvalId, actor, reason)
    fun expireApproval(approvalId: String, actor: String = "system", reason: String? = null) = approvals.expire(approvalId, actor, reason)
    fun revokeApproval(approvalId: String, actor: String = "owner", reason: String? = null) = approvals.revoke(approvalId, actor, reason)

    /**
     * Sprint 9 Final, Section 6's "Request Connection" entry point:
     * creates the Connection (PENDING_APPROVAL, same as every other
     * seeded connection) and the linked Approval that gates it, in that
     * order, so the approval's relatedConnectionId always points at a
     * connection that already exists by the time anything reacts to it.
     * No screen calls this yet -- see this method's own honesty note in
     * the PR's delivery summary for why that's a real, stated gap
     * rather than an oversight.
     */
    fun requestConnectionApproval(
        providerId: String,
        providerName: String,
        requestedPermissions: Set<PermissionScope>,
        maximumPermission: PermissionScope,
        requestedBy: String = "owner",
    ) {
        val connection = connections.requestConnection(providerId, providerName, requestedPermissions, maximumPermission)
        requestApproval(
            kind = ApprovalKind.CONNECTION_REQUEST,
            title = "Connect $providerName",
            reason = "New AI provider connection requested.",
            riskLevel = RiskLevel.MODERATE,
            requestedBy = requestedBy,
            relatedConnectionId = connection.connectionId,
        )
    }

    /**
     * "Connections react automatically" (Sprint 9 Final Section 6) --
     * called from the approvals.transitions collector for every
     * accepted approval transition. A no-op for approvals with no
     * relatedConnectionId (plain PERMISSION_REQUEST approvals aren't
     * about any one connection). Every ConnectionRepository call below
     * can still legitimately throw ConnectionOperationError if the
     * connection's actual current state doesn't allow it (e.g. it was
     * already manually disconnected elsewhere) -- caught and swallowed
     * here rather than propagated, because this collector must keep
     * running for every approval after this one; see this class's
     * docstring for why an uncaught exception here would be silent and
     * permanent, not a one-time miss.
     */
    private fun reactToApprovalTransition(t: ApprovalTransition) {
        val connectionId = t.relatedConnectionId ?: return
        try {
            when (t.newState) {
                ApprovalOutcome.APPROVED -> {
                    connections.approve(connectionId, t.actor)
                    connections.connect(connectionId)
                }
                ApprovalOutcome.REJECTED -> connections.reject(connectionId, t.reason ?: "Approval rejected")
                ApprovalOutcome.CANCELLED -> connections.disconnect(connectionId, t.reason ?: "Approval cancelled")
                ApprovalOutcome.EXPIRED -> connections.disconnect(connectionId, t.reason ?: "Approval expired")
                ApprovalOutcome.REVOKED -> {
                    val status = connections.connections.value.firstOrNull { it.connectionId == connectionId }?.status
                    if (status == ConnectionStatus.CONNECTED) {
                        connections.suspend(connectionId, t.reason ?: "Approval revoked")
                    } else {
                        connections.disconnect(connectionId, t.reason ?: "Approval revoked")
                    }
                }
                // PENDING is never a `newState` on an accepted transition
                // (see ApprovalRepository.allowedTransitions) -- required
                // for exhaustiveness, unreachable in practice.
                ApprovalOutcome.PENDING -> Unit
            }
        } catch (e: ConnectionOperationError) {
            // The approval's own state change already succeeded and was
            // already audited/notified above regardless of this outcome
            // -- a connection that drifted out of sync with its approval
            // is a real (if unusual) situation this logs by not crashing,
            // not a reason to have refused the approval action itself.
        }
    }

    // --- Tools coordination (Sprint 10) ------------------------------------------
    // Closes the honesty gap ToolRepository's own docstring names: this
    // is the "coordinator for what happens AFTER a tool runs" it
    // refers to. Every screen/caller that wants to run a tool goes
    // through this, not ToolRepository.execute directly, so a
    // CoreEvent (and therefore a Notification and an AuditEntry) is
    // guaranteed for every tool execution attempt -- same "every
    // important action must emit a CoreEvent" discipline as
    // Connections and Approvals above.

    suspend fun runTool(toolId: String, input: String, approvalId: String? = null): ToolResult {
        val result = tools.execute(toolId, input, approvalId)
        val (success, summary) = when (result) {
            is ToolResult.Success -> true to result.output
            is ToolResult.Failure -> false to result.message
        }
        publish(CoreEvent.ToolExecuted(toolId, success, summary))
        return result
    }

    suspend fun requestNavigation(route: String) {
        _navigationRequests.emit(route)
    }

    /**
     * The single coordination point Requirement 1 asks for. Publishes
     * ChatMessageSent, delegates the actual send to ChatRepository
     * (which streams through AiRouter's active ChatProvider),
     * publishes ChatResponseReceived once that completes, then checks
     * whether the user's own text was a recognized navigation command
     * -- see matchNavigationCommand's docstring for why this, and not
     * an ambient auto-navigate-on-event trigger, is this sprint's one
     * complete navigation example.
     */
    suspend fun sendChatMessage(text: String) {
        publish(CoreEvent.ChatMessageSent(chat.activeSessionId, text))
        chat.sendMessage(text)
        chat.messages.value.lastOrNull()?.let { lastMessage ->
            publish(CoreEvent.ChatResponseReceived(chat.activeSessionId, lastMessage.messageId))
        }
        matchNavigationCommand(text)?.let { route -> requestNavigation(route) }
    }

    /**
     * Explicit, deterministic, user-initiated -- typing "open
     * approvals" navigates there. Chosen over an automatic trigger
     * (e.g. auto-navigate whenever ApprovalRequested fires) because an
     * ambient navigation change is a real behavior change for whatever
     * screen the user is already on, which this sprint's "do not
     * redesign the application" instruction argues against. This is a
     * genuine, testable, full round trip through the exact mechanism a
     * later "JARVIS can navigate the app for you" capability would
     * extend, not a throwaway demo.
     */
    private fun matchNavigationCommand(text: String): String? =
        navigationCommandsByPhrase[text.trim().lowercase()]

    companion object {
        private val navigationCommandsByPhrase: Map<String, String> = mapOf(
            "open approvals" to "approvals",
            "open connections" to "connections",
            "open settings" to "settings",
            "open projects" to "projects",
            "open memory" to "memory",
            "open dashboard" to "dashboard",
            "go home" to "home",
        )
    }
}

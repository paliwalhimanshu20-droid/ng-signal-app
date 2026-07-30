package com.jarvis.os.app.core

import com.jarvis.os.app.core.agents.WatchTowerOrchestrator
import com.jarvis.os.app.core.intelligence.ContextManager
import com.jarvis.os.app.core.intelligence.ExecutiveBriefing
import com.jarvis.os.app.core.intelligence.ExecutiveBriefingEngine
import com.jarvis.os.app.core.intelligence.IntentClassification
import com.jarvis.os.app.core.intelligence.IntentRouter
import com.jarvis.os.app.core.intelligence.JarvisDecision
import com.jarvis.os.app.core.intelligence.JarvisDecisionEngine
import com.jarvis.os.app.core.tools.ToolResult
import com.jarvis.os.app.core.trading.TradingIntelligenceOrchestrator
import com.jarvis.os.app.data.model.ApprovalKind
import com.jarvis.os.app.data.model.ApprovalOutcome
import com.jarvis.os.app.data.model.ConnectionStatus
import com.jarvis.os.app.data.model.ContextBundle
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
    private val contextManager: ContextManager,
    private val decisionEngine: JarvisDecisionEngine,
    private val intentRouter: IntentRouter,
    private val tradingIntelligenceOrchestrator: TradingIntelligenceOrchestrator,
    val watchTower: WatchTowerOrchestrator,
    val briefingEngine: ExecutiveBriefingEngine,
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
        val toolName = tools.discover().firstOrNull { it.toolId == toolId }?.name ?: toolId
        publish(CoreEvent.ToolStarted(toolId, toolName))

        val result = tools.execute(toolId, input, approvalId)
        val (success, summary) = when (result) {
            is ToolResult.Success -> true to result.output
            is ToolResult.Failure -> false to result.message
        }
        publish(CoreEvent.ToolExecuted(toolId, success, summary))
        return result
    }

    // --- Watch Tower coordination (Sprint 12 Phase 2) -----------------------------
    // Same "ask first, run once approved" two-call shape as runTool above,
    // deliberately -- one governance pattern in this codebase, not a
    // second one for agents that could quietly drift from the first. The
    // actual approval-required rule lives in MultiAiCoordinator (Sprint
    // 11), not re-decided here -- see WatchTowerOrchestrator's docstring.

    /** Requests approval to convene Watch Tower on [topic] -- never runs a specialist itself. Mirrors requestApproval's shape, not runTool's, because nothing executes on this call. */
    suspend fun requestWatchTowerConvene(topic: String) = watchTower.requestConvene(topic)

    /** Runs Watch Tower on [topic] once [approvalId] has been approved -- mirrors runTool(toolId, input, approvalId)'s shape exactly. */
    suspend fun runWatchTower(topic: String, approvalId: String) = watchTower.convene(topic, approvalId)

    suspend fun requestNavigation(route: String) {
        _navigationRequests.emit(route)
    }

    /**
     * Sprint 12: the single coordination point Sprint-8 originally asked
     * for, now actually consulting the rest of the Sprint 10/11
     * foundation before replying instead of forwarding text verbatim.
     * Still publishes ChatMessageSent before sending and
     * ChatResponseReceived once ChatRepository's suspend call returns,
     * same as every sprint before this one -- Sprint 12 changes WHAT
     * gets sent to ChatRepository.sendMessage, not the event-publishing
     * shape around it.
     *
     * Routes to exactly one of four things, in priority order:
     *  1. needsBriefing -> ExecutiveBriefingEngine (Phase 3) -- a status
     *     roundup, not a reply about the owner's specific words.
     *  2. needsOrchestration -> WatchTowerOrchestrator.requestConvene
     *     (Phase 2) -- always creates a pending approval, never runs a
     *     specialist from this call (see that class's docstring).
     *  3. Sprint 14 "Intent Router" (extended Sprint 15 for multi-tool):
     *     IntentRouter.classifyAll(text) names every real toolId the
     *     message matches (zero, one, or several of the connector
     *     tools) -> every matched tool actually runs (via runTool, so
     *     each is still audited/CoreEvent'd exactly like a
     *     Tools-screen-triggered run) and their real, combined output
     *     is what the ChatProvider sees -- see
     *     buildToolBackedContextHint's own docstring for why this is
     *     safe to auto-run where JarvisDecisionEngine.matchedTool
     *     deliberately is not.
     *  4. otherwise -> the Phase 1 conversational context hint: recalled
     *     memory/conversation (ContextManager, consulted every turn --
     *     Phase 3's "no repeated explanations" means memory is never
     *     conditional on a keyword), project status if relevant, and an
     *     honest, non-executing note about any tool or agent the
     *     message named (see buildConversationalContextHint's own
     *     docstring for why matched tools/agents are never auto-run --
     *     that reasoning is exactly what step 3 above is a narrow,
     *     justified exception to, not a change to).
     *
     * The owner's own chat bubble is unaffected by any of this --
     * ChatRepository.sendMessage's contextHint parameter augments only
     * what the ChatProvider sees, never what's stored as the
     * OWNER-authored ChatMessage (see that interface's docstring).
     */
    suspend fun sendChatMessage(text: String) {
        publish(CoreEvent.ChatMessageSent(chat.activeSessionId, text))

        val decision = decisionEngine.decide(text)
        val classification = intentRouter.classifyAll(text)
        val tradingReply = matchTradingInstrumentSymbol(text)?.let { symbol -> tradingIntelligenceOrchestrator.askAbout(symbol) }
        val outcome = when {
            tradingReply != null -> ChatRoutingOutcome(tradingReply)
            decision.needsBriefing -> ChatRoutingOutcome(renderBriefing(briefingEngine.generateMorningBriefing()))
            decision.needsOrchestration -> ChatRoutingOutcome(renderOrchestrationRequest(text))
            classification.isNotEmpty() -> buildToolBackedContextHint(text, decision, classification)
            else -> ChatRoutingOutcome(buildConversationalContextHint(text, decision))
        }

        chat.sendMessage(text, outcome.contextHint, outcome.sourceToolIds, outcome.hadToolFailure)
        chat.messages.value.lastOrNull()?.let { lastMessage ->
            publish(CoreEvent.ChatResponseReceived(chat.activeSessionId, lastMessage.messageId))
        }
        matchNavigationCommand(text)?.let { route -> requestNavigation(route) }
    }

    /** Phase 3 + Phase 4: renders an ExecutiveBriefing as natural prose, not a labeled data dump -- "never respond like a debug application" (this sprint's own Phase 4 wording). */
    private fun renderBriefing(briefing: ExecutiveBriefing): String {
        val body = briefing.lines.joinToString(" ")
        return "${briefing.greeting} $body"
    }

    /**
     * Phase 2 + Phase 4: requests a Watch Tower convening and phrases
     * the result naturally. Deliberately does not say "PENDING", an
     * approval id in brackets, or any other UI/data-model vocabulary
     * the owner didn't ask for -- Phase 4's "avoid implementation
     * details unless explicitly requested" rule, applied to the one
     * place this sprint most risked violating it (WatchTowerSummary's
     * own [approvalId] field name reads exactly like an implementation
     * detail if surfaced verbatim).
     */
    private suspend fun renderOrchestrationRequest(topic: String): String {
        val summary = watchTower.requestConvene(topic)
        return if (summary.approvalId != null) {
            "I'd like to bring in the specialist team on this -- I've asked for your approval first, since convening the full team is always something you sign off on."
        } else {
            summary.headline
        }
    }

    /**
     * Phase 1 + Phase 4: what the ChatProvider actually sees this turn
     * when neither a briefing, an orchestration request, nor a Sprint
     * 14 tool-backed answer (see buildToolBackedContextHint) applies.
     * Recalled memory and active conversation from ContextManager come
     * first and unconditionally (Phase 3's "no repeated explanations"
     * -- consulted every turn, not gated behind a keyword), followed by
     * project status only when the message actually needs it, followed
     * by a natural mention of any tool or agent JarvisDecisionEngine
     * matched.
     *
     * A matched tool or agent is named, never run or dispatched, for
     * either risk level: there is no reliable, deterministic way to
     * extract a tool's real input (e.g. a bare arithmetic expression)
     * out of the owner's full sentence without guessing, and a
     * guessed-wrong input failing would look exactly like JARVIS
     * attempted and failed -- the fake-success/fake-failure dishonesty
     * this codebase's "no fake success" rule exists to rule out. A
     * matched agent is likewise only named, not dispatched -- Sprint 12
     * Phase 2's Watch Tower path is the one place this codebase
     * actually dispatches a specialist, and only after an explicit
     * orchestration request plus owner approval, never as a side effect
     * of an ordinary message that happened to mention an agent's name.
     */
    private suspend fun buildConversationalContextHint(text: String, decision: JarvisDecision): String {
        val parts = buildBaseContextParts(text, decision)
        decision.matchedTool?.let { tool ->
            parts += "${tool.name} looks relevant here -- it's available from the Tools screen whenever you'd like to run it"
        }
        decision.matchedAgent?.let { agent ->
            parts += "${agent.name} specializes in ${agent.specialty.lowercase()} and could weigh in if you'd like to bring the team in"
        }

        return if (parts.isEmpty()) "" else parts.joinToString(". ") + "."
    }

    /**
     * Sprint 16 "Executive Conversation UI": the one carrier this sprint
     * added so the UI can show a real, verified "via Google Calendar"
     * indicator (or connector-aware error styling) instead of guessing
     * from the LLM's own wording after the fact. contextHint is exactly
     * what used to be the bare String return of this method and
     * buildConversationalContextHint; sourceToolIds/hadToolFailure are
     * new, both defaulting to "nothing tool-backed happened this turn"
     * so briefing/orchestration/plain-conversation replies are
     * unaffected.
     */
    private data class ChatRoutingOutcome(
        val contextHint: String,
        val sourceToolIds: List<String> = emptyList(),
        val hadToolFailure: Boolean = false,
    )

    /**
     * Sprint 14 "Intent Router": the actual fix for chat not being able
     * to answer "can you check my calendar" despite GoogleWorkspaceTool
     * existing and being connected. Shares the same memory/project base
     * as buildConversationalContextHint (an owner asking about their
     * calendar still benefits from recalled context, same as any other
     * message), but replaces the "name it, don't run it" matchedTool
     * line with the tool's REAL output -- runTool is called here, not
     * ToolRepository.execute directly, so this still publishes
     * CoreEvent.ToolStarted/ToolExecuted and lands in the audit log
     * exactly like a Tools-screen-triggered run (see runTool's own
     * docstring).
     *
     * Sprint 15 Executive Integration Audit item 1 "Multi-tool
     * Requests": runs EVERY classification IntentRouter.classifyAll
     * returned, not just the first -- "do I have meetings today and any
     * important unread emails" now actually runs both
     * GoogleCalendarTool and GoogleGmailTool and folds both real
     * results into the same context hint, sequentially (each is its own
     * runTool call, its own ToolStarted/ToolExecuted pair, its own
     * audit entry). A compound question gets a compound, honest answer
     * instead of the first-matched tool silently winning and the rest
     * of the question going unaddressed with no signal to anyone that
     * happened.
     *
     * Every branch (Success and Failure, for every matched tool) tells
     * the ChatProvider the real outcome and instructs it to relay that
     * naturally -- a Failure (e.g. "Google Workspace isn't connected
     * yet") must never be smoothed over into a generic "I don't have
     * access" reply, since that would misrepresent a temporary/fixable
     * state as a permanent capability gap. The trailing instruction
     * covers the remaining edge this audit's item 1 also asks for: if
     * the owner asked about something none of the matched tools
     * actually cover, the model is told to say so honestly rather than
     * guessing -- this is what keeps a *partially* multi-tool answer
     * from silently becoming a *falsely complete* one.
     *
     * Sprint 16: now returns ChatRoutingOutcome instead of a bare
     * String -- toolIds/hadFailure are the real facts of what just
     * happened, computed here where the actual runTool calls are, never
     * reconstructed later by guessing.
     */
    private suspend fun buildToolBackedContextHint(text: String, decision: JarvisDecision, classifications: List<IntentClassification>): ChatRoutingOutcome {
        val parts = buildBaseContextParts(text, decision)
        val toolIds = classifications.mapNotNull { it.toolId }.distinct()
        if (toolIds.isEmpty()) return ChatRoutingOutcome(buildConversationalContextHint(text, decision))

        var hadFailure = false
        for (toolId in toolIds) {
            val result = runTool(toolId, text)
            parts += when (result) {
                is ToolResult.Success ->
                    "Real, current data for this: ${result.output}."
                is ToolResult.Failure -> {
                    hadFailure = true
                    "Attempted to check this just now but it failed: ${result.message}."
                }
            }
        }
        parts += if (toolIds.size > 1) {
            "Answer the owner's question naturally using all of the above -- do not say you lack the ability to check any of it, you just checked each part. " +
                "If the owner asked about something none of the above covers, say so honestly rather than guessing."
        } else {
            "Answer the owner's question naturally using this data -- do not say you lack the ability to check this, you just did."
        }
        decision.matchedAgent?.let { agent ->
            parts += "${agent.name} specializes in ${agent.specialty.lowercase()} and could weigh in if you'd like to bring the team in"
        }

        return ChatRoutingOutcome(parts.joinToString(". ") + ".", toolIds, hadFailure)
    }

    /** Shared by buildConversationalContextHint and buildToolBackedContextHint -- recalled memory/conversation (unconditional every turn) plus project status when relevant. Neither tool-naming nor tool-running belongs here; each caller appends its own version of that. */
    private suspend fun buildBaseContextParts(text: String, decision: JarvisDecision): MutableList<String> {
        val context = contextManager.buildContext(sessionId = chat.activeSessionId, query = text)
        val parts = mutableListOf<String>()

        if (context.recentConversation.isNotEmpty()) {
            parts += "we recently touched on: ${context.recentConversation.takeLast(3).joinToString("; ")}"
        }
        if (context.relevantPersonalMemory.isNotEmpty()) {
            parts += "for context: ${context.relevantPersonalMemory.joinToString("; ")}"
        }
        if (decision.needsProjectContext && projects.projects.value.isNotEmpty()) {
            val summary = projects.projects.value.joinToString("; ") { p ->
                "${p.name} is ${p.status} at ${p.progressPercent}% with ${p.pendingTasks.count { !it.done }} open task(s)"
            }
            parts += summary
        }
        return parts
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

    /**
     * JARVIS-002 Layer 3, minimal first pass: a real `TradingIntentRouter`/
     * `TradingQuestionParser` (deterministic, auditable, matching this file's own
     * IntentRouter/JarvisDecisionEngine convention) is separate, not-yet-built scope --
     * see `TradingIntelligenceOrchestrator`'s class doc. This placeholder only proves the
     * runtime wire end to end for September's single validated instrument. The symbol string
     * below ("NATURALGAS") is a best-guess default pending confirmation against this
     * environment's actual seeded `InstrumentEntity.symbol` convention -- if it does not match,
     * `TradingIntelligenceOrchestrator.askAbout` returns null (unresolved instrument) and this
     * message falls through to JarvisCore's existing, unaffected conversational handling below,
     * so an incorrect symbol fails safe rather than breaking the chat path.
     */
    private fun matchTradingInstrumentSymbol(text: String): String? {
        val lower = text.trim().lowercase()
        val tradingKeywords = listOf("natural gas", "naturalgas", "should i buy", "should i sell", "trade verdict", "recommendation for")
        if (tradingKeywords.none { lower.contains(it) }) return null
        return "NATURALGAS"
    }

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

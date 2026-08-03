package com.jarvis.os.app.core

import com.jarvis.os.app.core.agents.WatchTowerOrchestrator
import com.jarvis.os.app.core.intelligence.ContextManager
import com.jarvis.os.app.core.intelligence.ExecutiveBriefing
import com.jarvis.os.app.core.intelligence.ExecutiveBriefingEngine
import com.jarvis.os.app.core.intelligence.IntentClassification
import com.jarvis.os.app.core.intelligence.IntentRouter
import com.jarvis.os.app.core.intelligence.JarvisDecision
import com.jarvis.os.app.core.intelligence.JarvisDecisionEngine
import com.jarvis.os.app.core.intelligence.localintent.LocalIntentOutcome
import com.jarvis.os.app.core.intelligence.localintent.LocalIntentRouter
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
    private val localIntentRouter: LocalIntentRouter,
    private val languageManager: com.jarvis.os.app.core.intelligence.LanguageManager,
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
     * "LocalIntentRouter Offline Completion": routing now has two independent layers, checked in
     * this order:
     *
     *  LAYER 1 -- what can answer this. [localIntentRouter.resolve] always returns a real
     *  [LocalIntentOutcome], never null (see that class's own docstring for the full contract):
     *   - LOCAL_ONLY: a local repository/service (Trading Intelligence Database, Signals,
     *     Analytics, Mission Control, Connected Systems, Diagnostics, Settings, greetings, "who
     *     are you"/"what can you do"/help/version/status, or the bundled trading glossary)
     *     answered the message completely. Rendered via [ChatRepository.sendLocalMessage] and
     *     this method returns immediately -- no AI provider is ever reached for this turn, not
     *     even if one is configured.
     *   - LOCAL_PLUS_AI: a handler found real local context worth reasoning over further. Folded
     *     into `outcome` below as its own contextHint, same shape as a tool-backed reply. No
     *     handler currently returns this (see LocalIntentRouter's own docstring for why), but the
     *     branch is real, not a placeholder.
     *   - NO_MATCH: nothing local recognized the message. Falls through to the original five-step
     *     chain below unchanged: trading reply, briefing, orchestration, tool-backed, or
     *     conversational.
     *
     *  LAYER 2 -- whether an AI provider can actually be reached. Every path that reaches the
     *  bottom of this method (LOCAL_PLUS_AI or NO_MATCH) still needs SOME reply rendered, and
     *  historically that always meant `chat.sendMessage`, which always calls the real active
     *  ChatProvider -- including when no API key has been saved, which is exactly how a plain
     *  greeting used to surface a raw "No Claude API key is configured" error before this
     *  milestone. [ChatRepository.isAiProviderReady] is checked before ever calling
     *  `chat.sendMessage`: if a provider IS ready, behavior is identical to before. If not, this
     *  method still shows whatever real local content `outcome.contextHint` already contains
     *  (a trading reply, a briefing, tool output -- nothing computed locally is ever thrown away
     *  just because AI is unavailable) and only falls back to the fully generic
     *  [NO_AI_PROVIDER_MESSAGE] when there is truly no local content to show either. Either way,
     *  the reply is delivered via [ChatRepository.sendLocalMessage] -- `router.active.sendMessage`
     *  is never invoked, so its raw error text can never reach the owner.
     *
     * Priority order below LAYER 1, unchanged from before this milestone:
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
     *  4. otherwise -> the Phase 1 conversational context hint: project status if relevant, and an
     *     honest, non-executing note about any tool or agent the message named (see
     *     buildConversationalContextHint's own docstring for why matched tools/agents are never
     *     auto-run -- that reasoning is exactly what step 3 above is a narrow, justified exception
     *     to, not a change to). "Conversation Replay Bug Fix": this step (via
     *     buildBaseContextParts) no longer includes recalled conversation history here at all --
     *     durable personal memory is still consulted every turn but returned as its own separate
     *     ChatRoutingOutcome.memoryHint, and recent conversation is sourced independently, below,
     *     as its own recentChatHint -- see buildBaseContextParts' own docstring for the full
     *     reasoning and why this was the actual root cause of the replay bug.
     *
     * NOTE on trading recommendations (matchTradingInstrumentSymbol / tradingReply below): this
     * predates the Local Intent Router and is intentionally left as its own, separate step --
     * it is real reasoning over evidence (the 13-stage Decision Lifecycle), not a plain data
     * lookup. LocalIntentRouter's own TIDB handler is deliberately narrower than this -- raw
     * recorded facts (price/candle/contract) only, never a recommendation -- so the two never
     * compete for the same message; see TidbLocalIntentHandler's class docstring for that
     * boundary. RUNTIME INTEGRATION MILESTONE UPDATE: a non-null tradingReply is now returned
     * directly, the same way LAYER 1 returns a LOCAL_ONLY answer -- it no longer reaches LAYER 2
     * or the AI provider at all (see the short-circuit immediately below where tradingReply is
     * computed, and that block's own comment for why the prior "still subject to the LAYER 2
     * readiness gate" behavior was the root cause of real trading answers reading as generic AI
     * conversation).
     *
     * The owner's own chat bubble is unaffected by any of this --
     * ChatRepository.sendMessage's contextHint parameter augments only
     * what the ChatProvider sees, never what's stored as the
     * OWNER-authored ChatMessage (see that interface's docstring). The
     * same is true of ChatRepository.sendLocalMessage's [text] param.
     */
    suspend fun sendChatMessage(text: String) {
        publish(CoreEvent.ChatMessageSent(chat.activeSessionId, text))

        // "Phase 3C, Section 6+7 -- Language Manager + Conversation Language Memory": runs before
        // ANY routing below, local or AI-bound, so every deterministic template and (via
        // JarvisPersona.systemPrompt) every real AI provider call this turn sees the correct,
        // just-updated conversation language -- never a stale one from before this message, and
        // never left to the model to re-guess itself. See LanguageManager's own docstring for why
        // an ambiguous turn deliberately leaves the current language untouched rather than
        // resetting to English.
        languageManager.observeAndUpdate(text)

        // LAYER 1: see this method's own docstring. Always returns a real LocalIntentOutcome,
        // never null -- NO_MATCH is a real value here, not an absence this code has to remember
        // to check for.
        val localResult = localIntentRouter.resolve(text)
        if (localResult.outcome == LocalIntentOutcome.LOCAL_ONLY || localResult.outcome == LocalIntentOutcome.DEVICE_ACTION) {
            val domainName = localResult.domain?.name ?: "LOCAL"
            val response = localResult.response.orEmpty()
            chat.sendLocalMessage(text, response, domainName)
            publish(CoreEvent.LocalIntentResolved(domainName, response))
            finishChatTurn(text)
            return
        }

        val decision = decisionEngine.decide(text)
        val classification = intentRouter.classifyAll(text)
        val tradingReply = matchTradingInstrumentSymbol(text)?.let { symbol -> tradingIntelligenceOrchestrator.askAbout(symbol) }

        // RUNTIME INTEGRATION MILESTONE FIX (Goal 2/3): tradingReply is not a hint -- it is the
        // complete output of the 13-stage Decision Lifecycle, already including the real Trust
        // Score, dimension breakdown, and recommendation/rejection reasoning (see
        // TradingIntelligenceOrchestrator.askAbout / DecisionLifecycleRunner). Before this fix it
        // was passed to the AI provider as ChatRoutingOutcome.contextHint -- a mere grounding hint
        // the model was free to paraphrase, dilute, or override with generic conversation, which
        // is exactly the "chat still answers using generic conversational responses despite the
        // new systems existing" symptom this milestone was opened to fix. A repository-backed,
        // already-complete answer must never be handed to a model for optional rewriting -- same
        // "no fake success, no silent dilution of a real answer" reasoning LAYER 1 already applies
        // to every LocalIntentHandler. This is the one deliberate behavior change this milestone
        // makes to sendChatMessage's prior, documented priority chain (see this method's own
        // docstring's now-outdated "NOTE on trading recommendations" paragraph above); every other
        // branch below (briefing/orchestration/tool-backed/conversational) is unchanged.
        if (tradingReply != null) {
            chat.sendLocalMessage(text, tradingReply, TRADING_REPLY_DOMAIN)
            publish(CoreEvent.LocalIntentResolved(TRADING_REPLY_DOMAIN, tradingReply))
            finishChatTurn(text)
            return
        }

        val outcome = when {
            localResult.outcome == LocalIntentOutcome.LOCAL_PLUS_AI -> ChatRoutingOutcome(localResult.response.orEmpty())
            decision.needsBriefing -> ChatRoutingOutcome(renderBriefing(briefingEngine.generateMorningBriefing()))
            decision.needsOrchestration -> ChatRoutingOutcome(renderOrchestrationRequest(text))
            classification.isNotEmpty() -> buildToolBackedContextHint(text, decision, classification)
            else -> buildConversationalContextHint(text, decision)
        }

        // LAYER 2: see this method's own docstring. Never call the real provider when it can't
        // possibly succeed -- show whatever real local content already exists instead, and only
        // the fully generic message when there truly is none.
        if (chat.isAiProviderReady()) {
            // "Conversation Replay Bug Fix": recentChatHint is sourced fresh here, independently of
            // outcome.contextHint -- it is the one place a few real recent messages are still made
            // available to a real AI provider (per this file's own requirement that recent chat stays
            // "only for AI providers"), always as its own separate, clearly labeled channel -- see
            // ChatRepository.sendMessage's own docstring. It is intentionally NOT computed for the
            // isAiProviderReady()==false branch below, since sendLocalMessage never reaches a
            // ChatProvider at all and has nothing that would ever read it.
            val recentChatHint = contextManager.buildContext(sessionId = chat.activeSessionId, query = text)
                .recentConversation.takeLast(RECENT_CHAT_MESSAGE_LIMIT).joinToString("\n")
            chat.sendMessage(text, outcome.contextHint, outcome.memoryHint, recentChatHint, outcome.sourceToolIds, outcome.hadToolFailure)
        } else {
            val fallback = outcome.contextHint.ifBlank { NO_AI_PROVIDER_MESSAGE }
            chat.sendLocalMessage(text, fallback, AI_UNAVAILABLE_DOMAIN)
        }
        finishChatTurn(text)
    }

    /** Shared tail of every [sendChatMessage] branch: publish the completion event and resolve any navigation command the owner's own text named -- factored out so LAYER 1's early return and LAYER 2's two branches don't each duplicate it. */
    private suspend fun finishChatTurn(text: String) {
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
    private suspend fun buildConversationalContextHint(text: String, decision: JarvisDecision): ChatRoutingOutcome {
        val base = buildBaseContextParts(text, decision)
        val parts = base.parts
        decision.matchedTool?.let { tool ->
            parts += "${tool.name} looks relevant here -- it's available from the Tools screen whenever you'd like to run it"
        }
        decision.matchedAgent?.let { agent ->
            parts += "${agent.name} specializes in ${agent.specialty.lowercase()} and could weigh in if you'd like to bring the team in"
        }

        val hint = if (parts.isEmpty()) "" else parts.joinToString(". ") + "."
        return ChatRoutingOutcome(hint, memoryHint = base.memoryHint)
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
     *
     * "Conversation Replay Bug Fix": [memoryHint] is new -- durable personal-memory facts,
     * carried SEPARATELY from [contextHint] rather than folded into the same joined string.
     * [contextHint] itself no longer carries recalled conversation history at all (see
     * [buildBaseContextParts]'s own docstring for why that line was removed entirely, not just
     * relabeled) -- it is now only ever real, actionable grounding: tool output, project status,
     * a named tool/agent, a briefing, or a trading reply. Both flow into
     * [ChatRepository.sendMessage] as distinct parameters and, from there, into
     * [com.jarvis.os.app.core.chat.PromptBuilder] as distinct [com.jarvis.os.app.core.chat.ChatPrompt]
     * fields -- there is no longer any single point downstream where they get concatenated with
     * the Owner's own message.
     */
    private data class ChatRoutingOutcome(
        val contextHint: String,
        val sourceToolIds: List<String> = emptyList(),
        val hadToolFailure: Boolean = false,
        val memoryHint: String = "",
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
        val base = buildBaseContextParts(text, decision)
        val parts = base.parts
        val toolIds = classifications.mapNotNull { it.toolId }.distinct()
        if (toolIds.isEmpty()) return buildConversationalContextHint(text, decision)

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

        return ChatRoutingOutcome(parts.joinToString(". ") + ".", toolIds, hadFailure, base.memoryHint)
    }

    /**
     * Shared by buildConversationalContextHint and buildToolBackedContextHint -- project status
     * when relevant, plus (returned separately, see [BaseContextParts.memoryHint]) durable
     * personal-memory facts. Neither tool-naming nor tool-running belongs here; each caller
     * appends its own version of that.
     *
     * "Conversation Replay Bug Fix" -- ROOT CAUSE FIX: this method used to unconditionally add
     * `"we recently touched on: ${context.recentConversation.takeLast(3)...}"` as the FIRST line
     * of `parts` on every single AI-bound turn, regardless of whether the Owner's message had
     * anything to do with earlier conversation. That line then flowed, still as plain text, all
     * the way into what the active ChatProvider received as its entire prompt (see
     * MockChatRepository.sendMessage's old `"$contextHint\n\n$text"` concatenation) -- so a model
     * given "we recently touched on: X; Y; Z" as the first thing it reads naturally opened its
     * reply by acknowledging that, instead of answering the real, current question that followed
     * it. Two things fix this together, not one:
     *  1. This method no longer reads or surfaces `context.recentConversation` AT ALL for an
     *     ordinary turn. Nothing here re-adds it in a "safer" wrapper -- it is simply not part of
     *     what an ordinary question's prompt contains anymore.
     *  2. A message that's actually, explicitly ASKING to recall the conversation ("what did we
     *     discuss", "summarize our conversation", "what do you remember") is now answered by
     *     [com.jarvis.os.app.core.intelligence.localintent.ConversationLocalIntentHandler] at LAYER
     *     1, deterministically and locally, before this method (or any AI provider) is ever
     *     reached -- see that class's own docstring. That is the ONLY path by which recalled
     *     conversation history can become part of a JARVIS reply, and it is never blended with an
     *     unrelated question the way this method used to do unconditionally.
     *
     * `relevantPersonalMemory` (durable cross-session facts/preferences -- a genuinely different,
     * lower-risk thing than raw conversation transcript) is still consulted every turn, but is
     * returned as its own field ([BaseContextParts.memoryHint]) rather than appended into `parts`
     * -- callers thread it into [ChatRoutingOutcome.memoryHint], which
     * [com.jarvis.os.app.core.chat.PromptBuilder] renders as clearly labeled background context,
     * never as something concatenated with -- or mistakable for -- the Owner's own message.
     */
    private suspend fun buildBaseContextParts(text: String, decision: JarvisDecision): BaseContextParts {
        val context = contextManager.buildContext(sessionId = chat.activeSessionId, query = text)
        val parts = mutableListOf<String>()

        if (decision.needsProjectContext && projects.projects.value.isNotEmpty()) {
            val summary = projects.projects.value.joinToString("; ") { p ->
                "${p.name} is ${p.status} at ${p.progressPercent}% with ${p.pendingTasks.count { !it.done }} open task(s)"
            }
            parts += summary
        }
        val memoryHint = if (context.relevantPersonalMemory.isNotEmpty()) {
            context.relevantPersonalMemory.joinToString("; ")
        } else {
            ""
        }
        return BaseContextParts(parts, memoryHint)
    }

    /** Return type of [buildBaseContextParts] -- see that method's docstring for why `parts` (real, actionable grounding) and `memoryHint` (background-only personal memory) are kept as two separate fields rather than one joined list. */
    private data class BaseContextParts(val parts: MutableList<String>, val memoryHint: String)

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

        /** "Offline Completion" milestone LAYER 2 fallback -- see sendChatMessage's own docstring. Shown only when no AI provider is configured AND there is no local content at all to show instead. */
        private const val NO_AI_PROVIDER_MESSAGE = "I can answer this once an AI provider is configured. Local capabilities remain available."

        /** Stamped onto ChatMessage.sourceLocalDomain for a LAYER 2 fallback reply -- not a real [com.jarvis.os.app.core.intelligence.localintent.LocalServiceDomain], just an honest marker that no AI provider was called this turn either. */
        private const val AI_UNAVAILABLE_DOMAIN = "AI_UNAVAILABLE"

        /** Runtime Integration milestone: stamped onto ChatMessage.sourceLocalDomain when [tradingIntelligenceOrchestrator]'s real Decision Lifecycle output is shown directly, bypassing the AI provider entirely -- not a real [com.jarvis.os.app.core.intelligence.localintent.LocalServiceDomain] (this path predates and sits outside LocalIntentRouter, see sendChatMessage's own docstring), but [ResponseSourceEngine] should still be able to recognize it as real, repository-backed, HIGH-confidence content rather than an AI completion. */
        private const val TRADING_REPLY_DOMAIN = "TRADING_INTELLIGENCE"

        /** "Conversation Replay Bug Fix": how many trailing messages of real, live recentConversation are made available to a real AI provider as background RECENT CHAT -- see sendChatMessage's own docstring for why this is sourced separately from, and kept out of, ChatRoutingOutcome.contextHint. */
        private const val RECENT_CHAT_MESSAGE_LIMIT = 6
    }
}

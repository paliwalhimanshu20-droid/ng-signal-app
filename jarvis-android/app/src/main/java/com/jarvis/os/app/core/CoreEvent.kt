package com.jarvis.os.app.core

import com.jarvis.os.app.data.model.ApprovalOutcome
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
 *
 * Sprint 9 Final applies the identical reasoning to approvals:
 * ApprovalStatusChanged replaces the spec's suggested
 * ApprovalApproved/Rejected/Cancelled/Expired/Revoked with one event
 * carrying `newState`, for the same reason -- one source of truth for
 * "what state is this in", not six classes that could in principle
 * disagree with ApprovalOutcome. ApprovalRequested (below) stays
 * separate because creation isn't a state *transition* (there's no
 * previous state), the same way connection creation isn't represented
 * as a ConnectionStatusChanged either.
 */
sealed interface CoreEvent {
    data class ApprovalRequested(val approvalId: String, val summary: String) : CoreEvent
    data class ApprovalStatusChanged(
        val approvalId: String,
        val title: String,
        val relatedConnectionId: String?,
        val previousState: ApprovalOutcome,
        val newState: ApprovalOutcome,
        val actor: String,
        val reason: String? = null,
    ) : CoreEvent
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

    /** Sprint 10: published by JarvisCore.runTool after every tool execution attempt (success or failure) -- see that method's docstring for why tool execution goes through Core rather than callers hitting ToolRepository directly. */
    data class ToolExecuted(val toolId: String, val success: Boolean, val summary: String) : CoreEvent

    /** Sprint 15 Executive Integration Audit, item 2 "Tool Execution Feedback": published BEFORE a tool runs, not after -- the one signal that lets a UI show "Checking your calendar..." instead of the owner staring at a blank turn while GoogleCalendarTool's real network call is in flight. See ChatViewModel's collector for the actual UI wiring. */
    data class ToolStarted(val toolId: String, val toolName: String) : CoreEvent

    /**
     * JARVIS-002 Layer 2/3: published after `DecisionLifecycleRunner`'s stage 11 (MONITOR) has
     * recorded the corresponding `TradingTimelineEventEntity`, so any listener (a future UI
     * surface, a future proactive-notification path) can react without depending on
     * `DecisionLifecycleRunner` directly -- the same "listeners never depend on the producer"
     * shape every other CoreEvent above already follows. Not yet published by anything in this
     * first implementation -- the event bus (`_events` + `publish`) is owned by `JarvisCore`
     * itself, and `JarvisCore` is the one injecting `TradingIntelligenceOrchestrator`, so having
     * `TradingIntelligenceOrchestrator` or `DecisionLifecycleRunner` publish directly would
     * require a dependency back onto `JarvisCore`, which is circular. Recommended follow-up:
     * extract the event bus into its own small `@Singleton` (e.g. `CoreEventBus`) that both
     * `JarvisCore` and `core.trading.*` depend on independently -- flagged here as a genuine
     * finding from building this, not deferred by oversight.
     */
    data class TradingRecommendationIssued(val recommendationId: Long, val instrumentId: Long) : CoreEvent
}

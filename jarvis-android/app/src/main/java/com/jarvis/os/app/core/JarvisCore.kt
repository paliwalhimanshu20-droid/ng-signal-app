package com.jarvis.os.app.core

import com.jarvis.os.app.data.repository.ApprovalRepository
import com.jarvis.os.app.data.repository.ChatRepository
import com.jarvis.os.app.data.repository.ConnectionRepository
import com.jarvis.os.app.data.repository.MemoryRepository
import com.jarvis.os.app.data.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
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
 */
@Singleton
class JarvisCore @Inject constructor(
    val connections: ConnectionRepository,
    val approvals: ApprovalRepository,
    val memory: MemoryRepository,
    val projects: ProjectRepository,
    val chat: ChatRepository,
) {
    private val _events = MutableSharedFlow<CoreEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<CoreEvent> = _events

    private val _navigationRequests = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Route strings matching JarvisDestination.route values -- deliberately plain strings, not a JarvisDestination reference, so this coordination layer has no dependency on the navigation/UI package. */
    val navigationRequests: SharedFlow<String> = _navigationRequests

    suspend fun publish(event: CoreEvent) {
        _events.emit(event)
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
            "go home" to "home",
        )
    }
}

package com.jarvis.os.app.core.intelligence

import com.jarvis.os.app.core.memory.ConversationMemory
import com.jarvis.os.app.core.memory.PersonalMemory
import com.jarvis.os.app.data.model.ContextBundle
import com.jarvis.os.app.data.repository.ChatRepository
import com.jarvis.os.app.data.repository.ProjectRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 10 "Context understanding": the one place that assembles a
 * ContextBundle for a chat turn, reading ConversationMemory (Sprint 10
 * memory view, scoped to the active session), PersonalMemory (durable
 * cross-session facts/preferences) and ProjectRepository (what's
 * currently active), rather than each caller doing its own three-way
 * fan-out. Currently no caller wires this into
 * JarvisCore.sendChatMessage / MockChatRepository -- see this class's
 * "known limitation" in the sprint's delivery notes: building the
 * bundle is real and tested, threading it into the actual prompt a
 * ChatProvider receives is deferred exactly like Sprint 8.1 deferred
 * AiRouter's real-provider wiring, for the same reason (no real
 * network-calling provider exists yet to consume a real context
 * window -- MockChatProvider's canned reply has nowhere to put one).
 */
@Singleton
class ContextManager @Inject constructor(
    private val conversationMemory: ConversationMemory,
    private val personalMemory: PersonalMemory,
    private val chat: ChatRepository,
    private val projects: ProjectRepository,
) {
    suspend fun buildContext(sessionId: String, query: String = "", activeProjectId: String? = null): ContextBundle {
        val recentConversation = chat.messages.value
            .filter { it.sessionId == sessionId }
            .takeLast(MAX_RECENT_MESSAGES)
            .map { "${it.author}: ${it.content}" }

        // Sprint 10: live transcript above is what ChatRepository holds for THIS
        // process's lifetime; conversationMemory.recall additionally surfaces
        // CONVERSATION-tier MemoryEntry summaries for this session that predate
        // it (e.g. from a prior app session), appended rather than merged in
        // place so a caller can tell "live message" from "recalled summary" by
        // list position if it needs to.
        val recalledConversationMemory = conversationMemory.recall(sessionId, query)

        val relevantPersonalMemory = personalMemory.recall(query)

        val activeProjectSummary = activeProjectId?.let { id ->
            projects.projects.value.firstOrNull { it.projectId == id }?.let { project ->
                "${project.name} (${project.status}, ${project.progressPercent}%, " +
                    "${project.pendingTasks.count { !it.done }} open tasks)"
            }
        }

        return ContextBundle(sessionId, recentConversation + recalledConversationMemory, relevantPersonalMemory, activeProjectSummary)
    }

    companion object {
        private const val MAX_RECENT_MESSAGES = 10
    }
}

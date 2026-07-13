package com.jarvis.os.app.data.repository

import com.jarvis.os.app.core.chat.AiRouter
import com.jarvis.os.app.core.chat.ChatChunk
import com.jarvis.os.app.core.chat.ChatSessionManager
import com.jarvis.os.app.data.model.ChatMessage
import com.jarvis.os.app.data.model.ChatSession
import com.jarvis.os.app.data.model.MemoryEntry
import com.jarvis.os.app.data.model.MemoryTier
import com.jarvis.os.app.data.model.MessageAuthor
import com.jarvis.os.app.data.model.MessageContentKind
import com.jarvis.os.app.data.model.Project
import com.jarvis.os.app.data.model.ProjectStatus
import com.jarvis.os.app.data.model.ProjectTask
import com.jarvis.os.app.data.model.RiskLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// --- Part 6: Projects -------------------------------------------------------------

interface ProjectRepository {
    val projects: StateFlow<List<Project>>
}

@Singleton
class MockProjectRepository @Inject constructor() : ProjectRepository {
    override val projects: StateFlow<List<Project>> = MutableStateFlow(
        listOf(
            Project(
                "jarvis-os", "JARVIS OS", ProjectStatus.ACTIVE, 62,
                listOf(ProjectTask("t1", "Android app — Sprint-7", false), ProjectTask("t2", "Wake-word design", false)),
                RiskLevel.MODERATE,
            ),
            Project(
                "ng-signal-pro", "NG Signal Pro", ProjectStatus.ACTIVE, 88,
                listOf(ProjectTask("t3", "Research score tuning", false)),
                RiskLevel.LOW,
            ),
            Project(
                "projectos", "ProjectOS", ProjectStatus.BLOCKED, 20,
                listOf(ProjectTask("t4", "Repository write access", false)),
                RiskLevel.HIGH,
            ),
        ),
    ).asStateFlow()
}

// --- Part 7: Memory --------------------------------------------------------------

interface MemoryRepository {
    val entries: StateFlow<List<MemoryEntry>>
    fun search(query: String): List<MemoryEntry>
}

@Singleton
class MockMemoryRepository @Inject constructor() : MemoryRepository {
    private val seeded = listOf(
        MemoryEntry(UUID.randomUUID().toString(), MemoryTier.CONVERSATION, "Discussed Sprint-6 connection dashboard design", Instant.now().minus(1, ChronoUnit.DAYS)),
        MemoryEntry(UUID.randomUUID().toString(), MemoryTier.WORKING, "Current task: Android Sprint-7 build", Instant.now()),
        MemoryEntry(UUID.randomUUID().toString(), MemoryTier.KNOWLEDGE, "Upstox 90-day historical candle limit (discovered Sprint prior)", Instant.now().minus(10, ChronoUnit.DAYS)),
        MemoryEntry(UUID.randomUUID().toString(), MemoryTier.PREFERENCE, "Accent color: Arc Blue", Instant.now().minus(2, ChronoUnit.DAYS)),
    )
    override val entries: StateFlow<List<MemoryEntry>> = MutableStateFlow(seeded).asStateFlow()

    override fun search(query: String): List<MemoryEntry> =
        entries.value.filter { it.summary.contains(query, ignoreCase = true) }
}

// --- Part 5: Chat ------------------------------------------------------------------

interface ChatRepository {
    val messages: StateFlow<List<ChatMessage>>

    /** Sprint 8.1: reads from ChatSessionManager now — see that class's docstring for why session switching has no UI surface yet despite being real underneath. */
    val activeSessionId: String

    suspend fun sendMessage(text: String)
}

/**
 * Sprint-8: no longer hardcodes its own reply text — that behavior
 * lives in MockChatProvider (see core/chat), reached here through
 * AiRouter (Sprint 8.1's rename of Sprint-8's ChatProviderRegistry).
 * This repository's job is exactly what ChatRepository's Sprint-7 job
 * was (own the message list, own sending), not what provider generates
 * a reply or which session is active — those are separate concerns
 * now owned by AiRouter and ChatSessionManager respectively.
 */
@Singleton
class MockChatRepository @Inject constructor(
    private val router: AiRouter,
    private val sessionManager: ChatSessionManager,
) : ChatRepository {

    override val activeSessionId: String get() = sessionManager.activeSessionId.value

    private val _messages = MutableStateFlow(
        listOf(
            ChatMessage(
                UUID.randomUUID().toString(), MessageAuthor.JARVIS, MessageContentKind.TEXT,
                "Ready. No live AI provider call happens yet — see ChatProvider's docstring for how a real one gets wired in.",
                timestamp = Instant.now(),
                sessionId = ChatSession.DEFAULT_SESSION_ID,
            ),
        ),
    )
    override val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    override suspend fun sendMessage(text: String) {
        val sessionId = activeSessionId
        val userMessage = ChatMessage(
            UUID.randomUUID().toString(), MessageAuthor.OWNER, MessageContentKind.TEXT, text,
            timestamp = Instant.now(), sessionId = sessionId,
        )
        _messages.update { it + userMessage }

        // One fixed id reused across every Token/Complete chunk in this
        // turn: the LazyColumn in ChatScreen keys rows by messageId, so
        // reusing it here makes each Token update replace the SAME
        // bubble in place (real streaming), not append a new one.
        val replyMessageId = UUID.randomUUID().toString()
        var replyAdded = false

        router.active.sendMessage(sessionId, text).collect { chunk ->
            when (chunk) {
                is ChatChunk.Token -> {
                    upsertReply(replyMessageId, chunk.text, sessionId, alreadyAdded = replyAdded)
                    replyAdded = true
                }
                is ChatChunk.Complete -> {
                    upsertReply(replyMessageId, chunk.fullText, sessionId, alreadyAdded = replyAdded)
                    replyAdded = true
                }
                is ChatChunk.Error -> {
                    val errorMessage = ChatMessage(
                        UUID.randomUUID().toString(), MessageAuthor.JARVIS, MessageContentKind.TEXT,
                        "Error: ${chunk.message}", timestamp = Instant.now(), sessionId = sessionId,
                    )
                    _messages.update { it + errorMessage }
                }
            }
        }
    }

    private fun upsertReply(messageId: String, text: String, sessionId: String, alreadyAdded: Boolean) {
        val message = ChatMessage(
            messageId, MessageAuthor.JARVIS, MessageContentKind.MARKDOWN, text,
            timestamp = Instant.now(), sessionId = sessionId,
        )
        _messages.update { current ->
            if (alreadyAdded) current.map { if (it.messageId == messageId) message else it }
            else current + message
        }
    }
}

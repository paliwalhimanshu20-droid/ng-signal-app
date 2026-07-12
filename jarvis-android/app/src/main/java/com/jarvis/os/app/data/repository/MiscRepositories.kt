package com.jarvis.os.app.data.repository

import com.jarvis.os.app.data.model.ChatMessage
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

    /** No real AI call happens here — see module docstring. Echoes a structured acknowledgment so the Chat UI (typing indicator, message list, markdown/code rendering) is fully exercisable today. */
    suspend fun sendMessage(text: String)
}

@Singleton
class MockChatRepository @Inject constructor() : ChatRepository {
    private val _messages = MutableStateFlow(
        listOf(
            ChatMessage(
                UUID.randomUUID().toString(), MessageAuthor.JARVIS, MessageContentKind.TEXT,
                "Ready. This chat is a UI shell for Sprint-7 — no live AI Coordinator/provider call happens yet (see ChatRepository's docstring).",
                timestamp = Instant.now(),
            ),
        ),
    )
    override val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    override suspend fun sendMessage(text: String) {
        val userMessage = ChatMessage(UUID.randomUUID().toString(), MessageAuthor.OWNER, MessageContentKind.TEXT, text, timestamp = Instant.now())
        _messages.update { it + userMessage }

        val reply = ChatMessage(
            UUID.randomUUID().toString(), MessageAuthor.JARVIS, MessageContentKind.TEXT,
            "Received: \"$text\". Once Sprint-6's AI Coordination Layer is bridged to this app, this reply will come from a real provider response.",
            timestamp = Instant.now(),
        )
        _messages.update { it + reply }
    }
}

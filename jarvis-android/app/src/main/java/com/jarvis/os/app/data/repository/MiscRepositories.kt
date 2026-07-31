package com.jarvis.os.app.data.repository

import com.jarvis.os.app.core.chat.AiRouter
import com.jarvis.os.app.core.chat.ChatChunk
import com.jarvis.os.app.core.chat.ChatSessionManager
import com.jarvis.os.app.data.model.ChatMessage
import com.jarvis.os.app.data.model.ChatSession
import com.jarvis.os.app.data.model.Evidence
import com.jarvis.os.app.data.model.MemoryEntry
import com.jarvis.os.app.data.model.MemoryTier
import com.jarvis.os.app.data.model.MessageAuthor
import com.jarvis.os.app.data.model.MessageContentKind
import com.jarvis.os.app.data.model.Milestone
import com.jarvis.os.app.data.model.ProgressDashboard
import com.jarvis.os.app.data.model.Project
import com.jarvis.os.app.data.model.ProjectStatus
import com.jarvis.os.app.data.model.ProjectTask
import com.jarvis.os.app.data.model.RiskLevel
import com.jarvis.os.app.data.model.SprintRecord
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

/**
 * Sprint 10 ProjectOS Foundation extends what was a read-only seeded
 * list (Sprint-7/Part 6) into a real mutation surface, same "interface
 * owns validity, Mock owns today's data" shape as every other
 * repository here. `dashboard` is a StateFlow computed from `projects`
 * rather than separately-tracked state, so it can never drift out of
 * sync with the projects it's summarizing.
 */
interface ProjectRepository {
    val projects: StateFlow<List<Project>>
    val dashboard: StateFlow<ProgressDashboard>

    fun addTask(projectId: String, title: String): ProjectTask?
    fun completeTask(projectId: String, taskId: String)
    fun addMilestone(projectId: String, title: String, targetDate: Instant? = null): Milestone?
    fun reachMilestone(projectId: String, milestoneId: String)
    fun recordEvidence(projectId: String, description: String, reference: String): Evidence?
    fun addSprint(projectId: String, label: String): SprintRecord?
    fun completeSprint(projectId: String, sprintId: String)
    fun updateStatus(projectId: String, status: ProjectStatus)
}

@Singleton
class MockProjectRepository @Inject constructor() : ProjectRepository {
    private val _projects = MutableStateFlow(
        listOf(
            Project(
                "jarvis-os", "JARVIS OS", ProjectStatus.ACTIVE, 62,
                listOf(ProjectTask("t1", "Android app — Sprint-7", false), ProjectTask("t2", "Wake-word design", false)),
                RiskLevel.MODERATE,
                milestones = listOf(Milestone("m1", "PR4 + Sprint 10 + Sprint 11 merged", null, reached = false)),
                sprints = listOf(SprintRecord("s10", "Sprint 10 — Real AI Integration & Memory", completed = false)),
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
    )
    override val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    // Recomputed and pushed alongside `projects` on every mutation below (see mutate()),
    // rather than derived via kotlinx.coroutines.flow.map { } -- so `dashboard.value` is
    // synchronously correct immediately after any mutation call returns, no collector
    // required, matching how every other StateFlow in this codebase behaves.
    private val _dashboard = MutableStateFlow(computeDashboard(_projects.value))
    override val dashboard: StateFlow<ProgressDashboard> = _dashboard.asStateFlow()

    private fun computeDashboard(projects: List<Project>): ProgressDashboard = ProgressDashboard(
        totalProjects = projects.size,
        activeProjects = projects.count { it.status == ProjectStatus.ACTIVE },
        blockedProjects = projects.count { it.status == ProjectStatus.BLOCKED },
        averageProgressPercent = if (projects.isEmpty()) 0 else projects.sumOf { it.progressPercent } / projects.size,
        openTaskCount = projects.sumOf { p -> p.pendingTasks.count { !it.done } },
        reachedMilestoneCount = projects.sumOf { p -> p.milestones.count { it.reached } },
        totalMilestoneCount = projects.sumOf { it.milestones.size },
    )

    private fun mutate(projectId: String, block: (Project) -> Project) {
        _projects.update { list -> list.map { if (it.projectId == projectId) block(it) else it } }
        _dashboard.value = computeDashboard(_projects.value)
    }

    override fun addTask(projectId: String, title: String): ProjectTask? {
        if (_projects.value.none { it.projectId == projectId }) return null
        val task = ProjectTask(UUID.randomUUID().toString(), title, done = false)
        mutate(projectId) { it.copy(pendingTasks = it.pendingTasks + task) }
        return task
    }

    override fun completeTask(projectId: String, taskId: String) {
        mutate(projectId) { project ->
            project.copy(pendingTasks = project.pendingTasks.map { if (it.taskId == taskId) it.copy(done = true) else it })
        }
    }

    override fun addMilestone(projectId: String, title: String, targetDate: Instant?): Milestone? {
        if (_projects.value.none { it.projectId == projectId }) return null
        val milestone = Milestone(UUID.randomUUID().toString(), title, targetDate, reached = false)
        mutate(projectId) { it.copy(milestones = it.milestones + milestone) }
        return milestone
    }

    override fun reachMilestone(projectId: String, milestoneId: String) {
        mutate(projectId) { project ->
            project.copy(milestones = project.milestones.map { if (it.milestoneId == milestoneId) it.copy(reached = true) else it })
        }
    }

    override fun recordEvidence(projectId: String, description: String, reference: String): Evidence? {
        if (_projects.value.none { it.projectId == projectId }) return null
        val evidence = Evidence(UUID.randomUUID().toString(), description, reference, Instant.now())
        mutate(projectId) { it.copy(evidence = it.evidence + evidence) }
        return evidence
    }

    override fun addSprint(projectId: String, label: String): SprintRecord? {
        if (_projects.value.none { it.projectId == projectId }) return null
        val sprint = SprintRecord(UUID.randomUUID().toString(), label, completed = false)
        mutate(projectId) { it.copy(sprints = it.sprints + sprint) }
        return sprint
    }

    override fun completeSprint(projectId: String, sprintId: String) {
        mutate(projectId) { project ->
            project.copy(sprints = project.sprints.map { if (it.sprintId == sprintId) it.copy(completed = true) else it })
        }
    }

    override fun updateStatus(projectId: String, status: ProjectStatus) {
        mutate(projectId) { it.copy(status = status) }
    }
}

// --- Part 7: Memory --------------------------------------------------------------

/**
 * Sprint 10: this interface is the ONE persistence surface every
 * MemoryInterfaces.kt contract (ConversationMemory, ProjectMemory,
 * PersonalMemory, AgentMemory -- see that file's Sprint 8.1 docstring
 * for why they were left unimplemented) now reads and writes through,
 * via MemoryEngine below. There is still exactly one MemoryTier enum
 * and one storage list -- Sprint 10 does not introduce four parallel
 * stores, it introduces four narrow *views* over this one store,
 * exactly as MemoryInterfaces.kt's own docstring said a future PR
 * should do ("adding that dependency, and a real implementation of
 * each, is future work once there is a concrete consumer to build
 * against").
 */
interface MemoryRepository {
    val entries: StateFlow<List<MemoryEntry>>
    fun search(query: String): List<MemoryEntry>

    /** Scoped search: only entries whose relatedId matches, still filtered by query against summary+tags. Empty query returns every entry in scope. */
    fun search(query: String, relatedId: String): List<MemoryEntry>

    /** Sprint 10: the one write path for every tier. Returns the created entry so a caller (e.g. JarvisCore recording a decision) can reference its entryId. */
    fun remember(tier: MemoryTier, summary: String, relatedId: String? = null, tags: Set<String> = emptySet()): MemoryEntry

    /** Convenience over remember() for MemoryTier.DECISION -- summary is required to state both the choice and the reason, since DECISION entries exist specifically to answer "why did we do X" later. */
    fun recordDecision(choice: String, reason: String, relatedId: String? = null): MemoryEntry
}

@Singleton
class MockMemoryRepository @Inject constructor() : MemoryRepository {
    private val seeded = listOf(
        MemoryEntry(UUID.randomUUID().toString(), MemoryTier.CONVERSATION, "Discussed Sprint-6 connection dashboard design", Instant.now().minus(1, ChronoUnit.DAYS)),
        MemoryEntry(UUID.randomUUID().toString(), MemoryTier.WORKING, "Current task: Android Sprint-7 build", Instant.now()),
        MemoryEntry(UUID.randomUUID().toString(), MemoryTier.KNOWLEDGE, "Upstox 90-day historical candle limit (discovered Sprint prior)", Instant.now().minus(10, ChronoUnit.DAYS)),
        MemoryEntry(UUID.randomUUID().toString(), MemoryTier.PREFERENCE, "Accent color: JARVIS Blue", Instant.now().minus(2, ChronoUnit.DAYS)),
        MemoryEntry(
            UUID.randomUUID().toString(), MemoryTier.DECISION,
            "Chose capability-based routing (score by set overlap) over a fixed provider priority list -- keeps AiRouter deterministic without hardcoding provider rank.",
            Instant.now().minus(1, ChronoUnit.HOURS), tags = setOf("ai-router", "pr4"),
        ),
    )
    private val _entries = MutableStateFlow(seeded)
    override val entries: StateFlow<List<MemoryEntry>> = _entries.asStateFlow()

    override fun search(query: String): List<MemoryEntry> =
        if (query.isBlank()) entries.value
        else entries.value.filter { entry ->
            entry.summary.contains(query, ignoreCase = true) ||
                entry.tags.any { it.contains(query, ignoreCase = true) }
        }

    override fun search(query: String, relatedId: String): List<MemoryEntry> =
        search(query).filter { it.relatedId == relatedId }

    override fun remember(tier: MemoryTier, summary: String, relatedId: String?, tags: Set<String>): MemoryEntry {
        val entry = MemoryEntry(UUID.randomUUID().toString(), tier, summary, Instant.now(), relatedId, tags)
        _entries.update { it + entry }
        return entry
    }

    override fun recordDecision(choice: String, reason: String, relatedId: String?): MemoryEntry =
        remember(MemoryTier.DECISION, "$choice -- $reason", relatedId, tags = setOf("decision"))
}

// --- Part 5: Chat ------------------------------------------------------------------

interface ChatRepository {
    val messages: StateFlow<List<ChatMessage>>

    /** Sprint 8.1: reads from ChatSessionManager now — see that class's docstring for why session switching has no UI surface yet despite being real underneath. */
    val activeSessionId: String

    /**
     * Sprint 12 "Context Engine": [contextHint], when non-blank, is
     * prepended to what the active ChatProvider actually receives --
     * NOT to the owner-authored ChatMessage stored in [messages], so
     * the chat transcript still shows exactly what the owner typed.
     * Defaults to "" so every pre-Sprint-12 call site (including every
     * existing test) is source-compatible unchanged; JarvisCore is the
     * only caller that passes a real one, built from
     * ContextManager.buildContext -- see JarvisCore.sendChatMessage.
     *
     * Sprint 16 "Executive Conversation UI": [sourceToolIds] and
     * [hadToolFailure] are stamped onto the resulting JARVIS
     * ChatMessage unchanged -- see that field's own docstring. Both
     * default to empty/false so this is source-compatible the same way
     * contextHint was when it was added.
     */
    suspend fun sendMessage(text: String, contextHint: String = "", sourceToolIds: List<String> = emptyList(), hadToolFailure: Boolean = false)

    /**
     * "OS First" Local Intent Router bypass: appends the owner's message to the transcript
     * exactly like [sendMessage] does, but the JARVIS reply is [response] verbatim -- `router.active`
     * / any ChatProvider is never touched for this turn. This is the literal mechanism behind the
     * product decision "if a request can be answered completely by local services, do not call
     * any AI provider" -- see LocalIntentRouter's own docstring and JarvisCore.sendChatMessage,
     * the only caller. [sourceDomain] is stamped onto the resulting ChatMessage.sourceLocalDomain
     * unchanged, the same "never guessed after the fact" discipline sourceToolIds already follows.
     */
    suspend fun sendLocalMessage(text: String, response: String, sourceDomain: String)
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

    /**
     * "What exactly you are doing wrong": a real, serious bug traced to
     * its exact mechanism. This used to seed a fake JARVIS-authored
     * ChatMessage here ("I'm currently operating in offline mode...").
     * That message was never just UI decoration -- ContextManager.
     * buildContext() treats EVERY message in this list as real
     * conversation history, and JarvisCore.buildConversationalContextHint()
     * takes the last few of those and literally prepends "we recently
     * touched on: ..." to every future message sent to a real provider.
     * Once a real provider (Groq, in the reported case) actually started
     * working, it was still being told on every single turn that JARVIS
     * "is currently operating in offline mode because no AI provider has
     * been connected" -- and a real model, given that framing as recent
     * context, naturally kept describing itself as offline in its own
     * varied words. The request was genuinely reaching a real provider
     * and getting a genuine reply the whole time; the reply was just
     * contaminated by stale seed content that never should have been
     * real conversation history in the first place.
     *
     * Fixed by not seeding a ChatMessage at all -- the empty list here
     * is the honest starting state. The same helpful "not connected
     * yet" text now lives purely in ChatScreen's UI layer (see that
     * file), shown only when this list is empty, and is never a
     * ChatMessage that could be fed back into a real provider's context.
     */
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    override val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    override suspend fun sendMessage(text: String, contextHint: String, sourceToolIds: List<String>, hadToolFailure: Boolean) {
        val sessionId = activeSessionId
        val userMessage = ChatMessage(
            UUID.randomUUID().toString(), MessageAuthor.OWNER, MessageContentKind.TEXT, text,
            timestamp = Instant.now(), sessionId = sessionId,
        )
        _messages.update { it + userMessage }

        // The provider sees context-augmented text; the transcript above
        // (and therefore the owner's own chat bubble) keeps the exact
        // words they typed -- see this interface's docstring for why.
        val promptForProvider = if (contextHint.isBlank()) text else "$contextHint\n\n$text"

        // One fixed id reused across every Token/Complete chunk in this
        // turn: the LazyColumn in ChatScreen keys rows by messageId, so
        // reusing it here makes each Token update replace the SAME
        // bubble in place (real streaming), not append a new one.
        val replyMessageId = UUID.randomUUID().toString()
        var replyAdded = false

        // "End-to-End Conversation Pipeline Audit": logs the one thing
        // no earlier logging pass covered -- WHICH provider this
        // specific message was actually routed to, at the exact point
        // routing happens. Combined with each provider's own request/
        // response logging (added in the prior stabilization pass),
        // this makes the full pipeline traceable end to end: this line
        // confirms routing reached a real provider; that provider's own
        // logs confirm what happened after.
        android.util.Log.d("MockChatRepository", "sendMessage: routing to activeProvider=${router.active.id} (${router.active.displayName})")

        router.active.sendMessage(sessionId, promptForProvider).collect { chunk ->
            when (chunk) {
                is ChatChunk.Token -> {
                    upsertReply(replyMessageId, chunk.text, sessionId, alreadyAdded = replyAdded, sourceToolIds = sourceToolIds, hadToolFailure = hadToolFailure)
                    replyAdded = true
                }
                is ChatChunk.Complete -> {
                    upsertReply(replyMessageId, chunk.fullText, sessionId, alreadyAdded = replyAdded, sourceToolIds = sourceToolIds, hadToolFailure = hadToolFailure)
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

    private fun upsertReply(messageId: String, text: String, sessionId: String, alreadyAdded: Boolean, sourceToolIds: List<String> = emptyList(), hadToolFailure: Boolean = false) {
        val message = ChatMessage(
            messageId, MessageAuthor.JARVIS, MessageContentKind.MARKDOWN, text,
            timestamp = Instant.now(), sessionId = sessionId,
            sourceToolIds = sourceToolIds, toolFailureOccurred = hadToolFailure,
        )
        _messages.update { current ->
            if (alreadyAdded) current.map { if (it.messageId == messageId) message else it }
            else current + message
        }
    }

    /**
     * "OS First" bypass -- see this method's interface docstring. Deliberately does NOT touch
     * `router` at all (not even to log which provider "would have" run, since none does) --
     * every other branch of this class exists to talk to a ChatProvider; this is the one that
     * proves it is possible not to.
     */
    override suspend fun sendLocalMessage(text: String, response: String, sourceDomain: String) {
        val sessionId = activeSessionId
        val userMessage = ChatMessage(
            UUID.randomUUID().toString(), MessageAuthor.OWNER, MessageContentKind.TEXT, text,
            timestamp = Instant.now(), sessionId = sessionId,
        )
        val replyMessage = ChatMessage(
            UUID.randomUUID().toString(), MessageAuthor.JARVIS, MessageContentKind.MARKDOWN, response,
            timestamp = Instant.now(), sessionId = sessionId,
            sourceLocalDomain = sourceDomain,
        )
        _messages.update { it + userMessage + replyMessage }
    }
}

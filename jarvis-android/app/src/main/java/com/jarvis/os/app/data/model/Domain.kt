package com.jarvis.os.app.data.model

import java.time.Instant

// --- Part 6: Projects ---------------------------------------------------------------

enum class ProjectStatus { ACTIVE, BLOCKED, PAUSED, COMPLETED }

data class ProjectTask(val taskId: String, val title: String, val done: Boolean)

/** Sprint 10 "Milestone management" -- a named checkpoint within a project, distinct from ProjectTask (a milestone can be reached by many completed tasks; it is not itself one). */
data class Milestone(
    val milestoneId: String,
    val title: String,
    val targetDate: Instant?,
    val reached: Boolean,
)

/** Sprint 10 "Evidence collection" -- a link between a completed unit of work and proof it happened, mirroring this codebase's own working-style rule ("every claim requires evidence from the actual live repo"). `reference` is free-form: a commit sha, a file path, a PR url. */
data class Evidence(
    val evidenceId: String,
    val description: String,
    val reference: String,
    val recordedAt: Instant,
)

/** Sprint 10 "Sprint tracking" -- one development sprint inside a Project's timeline. Named SprintRecord (not Sprint) to avoid any collision with kotlinx.coroutines' unrelated CoroutineScope-adjacent vocabulary and to read unambiguously wherever it's imported alongside coroutine types. */
data class SprintRecord(
    val sprintId: String,
    val label: String,
    val completed: Boolean,
)

data class Project(
    val projectId: String,
    val name: String,
    val status: ProjectStatus,
    val progressPercent: Int,
    val pendingTasks: List<ProjectTask>,
    val priority: RiskLevel, // reusing RiskLevel's ordering as a stand-in priority scale (LOW..CRITICAL) rather than inventing a second one — see data/model/Approval.kt
    val milestones: List<Milestone> = emptyList(),
    val evidence: List<Evidence> = emptyList(),
    val sprints: List<SprintRecord> = emptyList(),
)

/** Sprint 10 "Progress dashboard" deliverable -- a computed rollup across every Project, not a stored entity. See ProjectRepository.dashboard. */
data class ProgressDashboard(
    val totalProjects: Int,
    val activeProjects: Int,
    val blockedProjects: Int,
    val averageProgressPercent: Int,
    val openTaskCount: Int,
    val reachedMilestoneCount: Int,
    val totalMilestoneCount: Int,
)

// --- Part 7 / Sprint 10: Memory --------------------------------------------------------------------

/**
 * Sprint 10 extends Sprint-7's four tiers (WORKING, CONVERSATION,
 * KNOWLEDGE, PREFERENCE, unchanged in meaning) with the three the
 * sprint brief names explicitly: LONG_TERM (durable facts that outlive
 * any one conversation or project), PROJECT (scoped to one
 * Project.projectId via MemoryEntry.relatedId), and DECISION (an
 * append-style record of a choice JARVIS or the owner made and why --
 * ProjectMemory/AgentMemory read this tier when asked "why did we do
 * X"). KNOWLEDGE is kept rather than renamed to LONG_TERM to avoid
 * breaking every existing seeded entry and MockMemoryRepository call
 * site -- the two are conceptually close but not merged, matching this
 * codebase's stated preference against silently reinterpreting an
 * existing enum value's meaning.
 */
enum class MemoryTier { WORKING, CONVERSATION, KNOWLEDGE, PREFERENCE, LONG_TERM, PROJECT, DECISION }

/**
 * @param relatedId Sprint 10: scopes an entry to a sessionId (tier ==
 * CONVERSATION), a Project.projectId (tier == PROJECT), or an agentId
 * (a future Sprint 11 AgentMemory use) -- null for entries with no
 * natural owner (PREFERENCE, most LONG_TERM/KNOWLEDGE facts). This is
 * additive: every entry seeded before Sprint 10 keeps working with
 * relatedId defaulting to null.
 * @param tags Sprint 10's "searchable memory" requirement -- `search`
 * below matches against both `summary` and `tags`, so an entry can be
 * found by a keyword that doesn't appear verbatim in its summary text.
 */
data class MemoryEntry(
    val entryId: String,
    val tier: MemoryTier,
    val summary: String,
    val timestamp: Instant,
    val relatedId: String? = null,
    val tags: Set<String> = emptySet(),
)

// --- Part 5: Chat ------------------------------------------------------------------------

enum class MessageAuthor { OWNER, JARVIS }

enum class MessageContentKind { TEXT, CODE_BLOCK, MARKDOWN }

/**
 * Sprint-8: sessionId defaults to ChatSession.DEFAULT_SESSION_ID, which
 * is what every message already implicitly belonged to in Sprint-7 —
 * this is additive, not a breaking change. No existing call site needs
 * to change unless it wants to address a non-default session.
 */
data class ChatMessage(
    val messageId: String,
    val author: MessageAuthor,
    val kind: MessageContentKind,
    val content: String,
    val language: String? = null, // set only when kind == CODE_BLOCK
    val timestamp: Instant,
    val sessionId: String = ChatSession.DEFAULT_SESSION_ID,
    /**
     * Sprint 16 "Executive Conversation UI": which connector tool(s), if
     * any, actually produced the real data behind this reply -- set by
     * JarvisCore from IntentRouter's own classification, never guessed
     * from the LLM's text afterward (parsing a reply to infer its
     * source would be exactly the kind of fragile heuristic this
     * codebase's "no fake success" discipline avoids elsewhere). Empty
     * for a plain conversational reply, the owner's own message, or a
     * briefing/orchestration reply. This is the one small, additive,
     * backward-compatible (default empty list) model change this UI
     * sprint made to stable backend architecture -- see JarvisCore's
     * sendChatMessage docstring for why it was judged "absolutely
     * required": no rich "via Google Calendar" indicator or connector-
     * aware error styling is honestly buildable without JARVIS knowing,
     * for certain, which tool (if any) actually ran this turn.
     */
    val sourceToolIds: List<String> = emptyList(),
    /** True if at least one of sourceToolIds' tool calls this turn returned ToolResult.Failure -- lets the UI style a message as an honest connector error rather than a plain reply, without re-parsing the LLM's own wording to guess. */
    val toolFailureOccurred: Boolean = false,
    /**
     * "OS First" Local Intent Router: set (to e.g. "TIDB", "SIGNALS", "MISSION_CONTROL") when
     * this reply was answered entirely by a local repository/service -- see
     * com.jarvis.os.app.core.intelligence.localintent.LocalIntentRouter -- with NO AI provider
     * call made this turn at all. Null for every other reply (plain conversational, tool-backed,
     * briefing, orchestration), including every pre-existing message and test, matching this
     * field's own "additive, default-null" convention already used by sourceToolIds above. Lets
     * the UI show an honest "answered locally, no AI used" indicator instead of a "via <tool>"
     * one, without guessing from the reply text.
     */
    val sourceLocalDomain: String? = null,
)

/**
 * Sprint-8: the session concept Requirement 2 asks for, introduced at
 * the model/repository layer only. This sprint ships exactly one
 * session (DEFAULT_SESSION_ID, created on first app use) — there is no
 * session-switcher UI yet. Building that UI now, with no product
 * decision made about what multi-session chat should look like in this
 * app's design language, would be exactly the kind of speculative
 * feature Sprint 9 (not this one) should own. What Sprint 8 delivers is
 * the seam: ChatMessage already carries a sessionId, ChatRepository
 * already scopes by it — a session list screen is additive from here,
 * not a rework.
 */
data class ChatSession(
    val sessionId: String,
    val title: String,
    val createdAt: Instant,
) {
    companion object {
        const val DEFAULT_SESSION_ID: String = "default"
    }
}

// --- Part 12: Home Automation --------------------------------------------------------------

/**
 * Part 12's explicit supported/unsupported device split. This enum
 * intentionally lists BOTH categories in one place — the allowlist
 * enforcement itself lives in HomeAutomationPolicy.kt as a pure,
 * directly-testable function over this enum, not as scattered `if`
 * checks across the UI.
 */
enum class DeviceType {
    TV, AC, LIGHTS, FANS, SPEAKERS, CURTAINS, ROBOT_VACUUM, // supported
    DOOR_LOCKS, SECURITY_CAMERAS, GAS_VALVE, ALARM_SYSTEM, FIRE_SAFETY, // never supported by default
}

data class HomeDevice(
    val deviceId: String,
    val name: String,
    val type: DeviceType,
    val connectionId: String?, // null until an approved Connection exists for it
    val isOn: Boolean,
)

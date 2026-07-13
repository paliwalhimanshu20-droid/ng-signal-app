package com.jarvis.os.app.data.model

import java.time.Instant

// --- Part 6: Projects ---------------------------------------------------------------

enum class ProjectStatus { ACTIVE, BLOCKED, PAUSED, COMPLETED }

data class ProjectTask(val taskId: String, val title: String, val done: Boolean)

data class Project(
    val projectId: String,
    val name: String,
    val status: ProjectStatus,
    val progressPercent: Int,
    val pendingTasks: List<ProjectTask>,
    val priority: RiskLevel, // reusing RiskLevel's ordering as a stand-in priority scale (LOW..CRITICAL) rather than inventing a second one — see data/model/Approval.kt
)

// --- Part 7: Memory --------------------------------------------------------------------

enum class MemoryTier { WORKING, CONVERSATION, KNOWLEDGE, PREFERENCE }

data class MemoryEntry(
    val entryId: String,
    val tier: MemoryTier,
    val summary: String,
    val timestamp: Instant,
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

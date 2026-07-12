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

data class ChatMessage(
    val messageId: String,
    val author: MessageAuthor,
    val kind: MessageContentKind,
    val content: String,
    val language: String? = null, // set only when kind == CODE_BLOCK
    val timestamp: Instant,
)

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

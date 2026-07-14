package com.jarvis.os.app.data.model

import java.time.Instant

/**
 * Sprint 9 (PR2): the full category taxonomy the spec calls for. Note
 * this replaces the UI-only, unrelated categories the Sprint-8 shell
 * screen invented for itself (GITHUB, PROJECTS, NG_SIGNAL_PRO, ...) --
 * those were never backed by real data, just a taxonomy for sample
 * cards. This one is backed by NotificationFactory (see core package)
 * and is the taxonomy CoreEvents actually map onto.
 */
enum class NotificationCategory { APPROVAL, CONNECTION, AI, PROJECT, WARNING, ERROR, SYSTEM, TOOL }

enum class NotificationPriority { LOW, NORMAL, HIGH }

/**
 * @param source Which subsystem generated this (e.g. "Connections",
 * "Approvals") -- not a category (a Connection notification's category
 * is always CONNECTION regardless of source), just human-readable
 * provenance for the notification list.
 * @param relatedEntityId Optional id of whatever this is about (a
 * connectionId, approvalId, etc.) so a future "tap to open" action has
 * something to navigate to -- not wired to navigation in this PR.
 */
data class Notification(
    val notificationId: String,
    val category: NotificationCategory,
    val priority: NotificationPriority,
    val title: String,
    val message: String,
    val timestamp: Instant,
    val source: String,
    val relatedEntityId: String? = null,
    val read: Boolean = false,
)

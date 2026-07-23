package com.jarvis.os.app.feature.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import com.jarvis.os.app.data.model.Notification
import com.jarvis.os.app.data.model.NotificationCategory
import com.jarvis.os.app.data.model.NotificationPriority
import com.jarvis.os.app.designsystem.JarvisSpacing
import com.jarvis.os.app.designsystem.JarvisStatusColors
import com.jarvis.os.app.designsystem.components.JarvisCard
import com.jarvis.os.app.designsystem.components.StatusPill
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Sprint 9 (PR2): replaces the Sprint-8 shell's hardcoded
 * `sampleNotifications` list with the real, event-driven feed --
 * NotificationsViewModel reads from NotificationRepository via
 * JarvisCore, which is populated exclusively by JarvisCore's own
 * CoreEvent collector (see that class). Nothing on this screen can
 * insert a notification; the two buttons in the header and each card's
 * tap-to-read only ever call markRead/markAllRead/clearRead.
 */
@Composable
fun NotificationsScreen(viewModel: NotificationsViewModel = hiltViewModel()) {
    val notifications by viewModel.notifications.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()

    LazyColumn(contentPadding = PaddingValues(horizontal = JarvisSpacing.md, vertical = JarvisSpacing.sm)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = JarvisSpacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (unreadCount > 0) "Notifications ($unreadCount unread)" else "Notifications",
                    style = MaterialTheme.typography.titleMedium,
                )
                Row {
                    TextButton(onClick = { viewModel.markAllRead() }, enabled = unreadCount > 0) { Text("Mark all read") }
                    TextButton(onClick = { viewModel.clearRead() }, enabled = notifications.any { it.read }) { Text("Clear read") }
                }
            }
        }
        if (notifications.isEmpty()) {
            item {
                Text(
                    "Nothing yet — this fills in as connections, approvals, and other JARVIS activity happens.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = JarvisSpacing.md),
                )
            }
        }
        items(notifications, key = { it.notificationId }) { notification ->
            NotificationRow(notification, onClick = { if (!notification.read) viewModel.markRead(notification.notificationId) })
        }
    }
}

@Composable
private fun NotificationRow(notification: Notification, onClick: () -> Unit) {
    JarvisCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = JarvisSpacing.xs)
            .clickable(onClick = onClick),
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatusPill(notification.category.label(), notification.category.toColor())
                Text(
                    notification.timestamp.atZone(ZoneId.systemDefault()).format(timeFormatter),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                notification.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (notification.read) FontWeight.Normal else FontWeight.Bold,
            )
            Text(notification.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                notification.source + if (notification.priority == NotificationPriority.HIGH) " · High priority" else "",
                style = MaterialTheme.typography.labelSmall,
                color = if (notification.priority == NotificationPriority.HIGH) JarvisStatusColors.Unhealthy else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")

private fun NotificationCategory.label(): String = when (this) {
    NotificationCategory.APPROVAL -> "Approval"
    NotificationCategory.CONNECTION -> "Connection"
    NotificationCategory.AI -> "AI"
    NotificationCategory.PROJECT -> "Project"
    NotificationCategory.WARNING -> "Warning"
    NotificationCategory.ERROR -> "Error"
    NotificationCategory.SYSTEM -> "System"
    NotificationCategory.TOOL -> "Tool"
}

private fun NotificationCategory.toColor() = when (this) {
    NotificationCategory.ERROR, NotificationCategory.WARNING -> JarvisStatusColors.Unhealthy
    NotificationCategory.CONNECTION -> JarvisStatusColors.Healthy
    NotificationCategory.APPROVAL -> JarvisStatusColors.Degraded
    NotificationCategory.AI, NotificationCategory.PROJECT, NotificationCategory.SYSTEM, NotificationCategory.TOOL -> JarvisStatusColors.Unknown
}

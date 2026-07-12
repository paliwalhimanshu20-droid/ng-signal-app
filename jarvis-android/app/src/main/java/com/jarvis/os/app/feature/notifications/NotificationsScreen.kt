package com.jarvis.os.app.feature.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jarvis.os.app.designsystem.JarvisSpacing
import com.jarvis.os.app.designsystem.components.JarvisCard

/** Part 10's notification categories. No real push/notification-channel wiring in this sprint (no backend event stream exists to notify FROM yet — see delivery notes) — this is the UI shell + category taxonomy, matching every other "real UI, mock data" screen in this app. */
private enum class NotificationCategory(val label: String) {
    GITHUB("GitHub"), PROJECTS("Projects"), NG_SIGNAL_PRO("NG Signal Pro"),
    APPROVALS("Approvals"), CALENDAR("Calendar"), WEATHER("Weather"), AI_UPDATES("AI Updates"),
}

private data class MockNotification(val category: NotificationCategory, val message: String)

private val sampleNotifications = listOf(
    MockNotification(NotificationCategory.APPROVALS, "Claude connection request is waiting for approval."),
    MockNotification(NotificationCategory.PROJECTS, "JARVIS OS Sprint-7 marked in progress."),
    MockNotification(NotificationCategory.NG_SIGNAL_PRO, "Research score updated for 3 instruments."),
    MockNotification(NotificationCategory.GITHUB, "New commit on ng-signal-app."),
)

@Composable
fun NotificationsScreen() {
    LazyColumn(contentPadding = PaddingValues(horizontal = JarvisSpacing.md, vertical = JarvisSpacing.sm)) {
        items(sampleNotifications) { notification ->
            JarvisCard(modifier = Modifier.fillMaxWidth().padding(vertical = JarvisSpacing.xs)) {
                Column {
                    Text(notification.category.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(notification.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

package com.jarvis.os.app.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.os.app.core.JarvisCore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sprint 9 (PR2): routes every action through JarvisCore rather than
 * NotificationRepository directly, matching the coordination pattern
 * PR1 established for Connections (see ConnectionsViewModel) and this
 * sprint's own "JarvisCore coordinates every workflow" rule. Reading
 * the list and unread count still comes straight from the repository's
 * own StateFlows -- JarvisCore doesn't re-expose those under a
 * different name, it just owns the mutations.
 */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val core: JarvisCore,
) : ViewModel() {

    val notifications = core.notifications.notifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unreadCount = core.notifications.unreadCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun markRead(notificationId: String) = viewModelScope.launch { core.markNotificationRead(notificationId) }
    fun markAllRead() = viewModelScope.launch { core.markAllNotificationsRead() }
    fun clearRead() = viewModelScope.launch { core.clearReadNotifications() }
}

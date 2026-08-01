package com.jarvis.os.app.data.repository

import com.jarvis.os.app.data.model.Notification
import com.jarvis.os.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 9 (PR2): owns the notification list and nothing else -- it
 * does not decide WHAT becomes a notification (NotificationFactory
 * does, see core package) or WHEN a CoreEvent happens (JarvisCore's
 * event flow does). `insert` exists on this interface because
 * JarvisCore's event collector needs to call it, but nothing else in
 * this codebase calls it -- "notifications must never be manually
 * inserted" is enforced by convention (one call site, in JarvisCore's
 * init block) the same way ConnectionRepository's `transition()` gate
 * enforces state-machine legality: there's nowhere else to reach in
 * from.
 *
 * `unreadCount` is exposed here rather than computed per-screen so a
 * badge anywhere in the UI (drawer item, bottom bar, wherever) reads
 * the exact same number the Notifications screen's own header does --
 * one source of truth, not two places independently filtering the same
 * list and risking drift.
 */
interface NotificationRepository {
    val notifications: StateFlow<List<Notification>>
    val unreadCount: StateFlow<Int>

    fun insert(notification: Notification)
    fun markRead(notificationId: String)
    fun markAllRead()
    fun clearRead()
}

@Singleton
class MockNotificationRepository @Inject constructor(
    @ApplicationScope scope: CoroutineScope,
) : NotificationRepository {

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    override val notifications: StateFlow<List<Notification>> = _notifications.asStateFlow()

    override val unreadCount: StateFlow<Int> = _notifications
        .map { list -> list.count { !it.read } }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    override fun insert(notification: Notification) {
        // Newest first -- a notification center where you have to scroll
        // to find what just happened isn't one anyone would use.
        _notifications.update { listOf(notification) + it }
    }

    override fun markRead(notificationId: String) {
        _notifications.update { list -> list.map { if (it.notificationId == notificationId) it.copy(read = true) else it } }
    }

    override fun markAllRead() {
        _notifications.update { list -> list.map { it.copy(read = true) } }
    }

    override fun clearRead() {
        _notifications.update { list -> list.filterNot { it.read } }
    }
}

package com.jarvis.os.app.data

import com.jarvis.os.app.data.model.Notification
import com.jarvis.os.app.data.model.NotificationCategory
import com.jarvis.os.app.data.model.NotificationPriority
import com.jarvis.os.app.data.repository.MockNotificationRepository
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Uses UnconfinedTestDispatcher so unreadCount's stateIn collector runs
 * eagerly, and backgroundScope (not the test's own scope) so that
 * collector -- which, like JarvisCore's init-block collectors, runs for
 * the repository's entire lifetime and never completes on its own --
 * doesn't trip runTest's UncompletedCoroutinesError check. See
 * JarvisCoreNotificationTest's docstring for the full reasoning; same
 * root cause, same fix, applied here to stateIn's internal collector
 * instead of a SharedFlow.collect one.
 */
class MockNotificationRepositoryTest {

    private fun sample(id: String, read: Boolean = false) = Notification(
        notificationId = id,
        category = NotificationCategory.CONNECTION,
        priority = NotificationPriority.NORMAL,
        title = "Title $id",
        message = "Message $id",
        timestamp = Instant.now(),
        source = "Test",
        read = read,
    )

    @Test
    fun `insert adds newest first`() = runTest(UnconfinedTestDispatcher()) {
        val repo = MockNotificationRepository(backgroundScope)
        repo.insert(sample("n1"))
        repo.insert(sample("n2"))

        assertEquals(listOf("n2", "n1"), repo.notifications.value.map { it.notificationId })
    }

    @Test
    fun `unread count reflects only unread notifications`() = runTest(UnconfinedTestDispatcher()) {
        val repo = MockNotificationRepository(backgroundScope)
        repo.insert(sample("n1"))
        repo.insert(sample("n2", read = true))

        assertEquals(1, repo.unreadCount.value)
    }

    @Test
    fun `markRead flips only the targeted notification`() = runTest(UnconfinedTestDispatcher()) {
        val repo = MockNotificationRepository(backgroundScope)
        repo.insert(sample("n1"))
        repo.insert(sample("n2"))

        repo.markRead("n1")

        assertTrue(repo.notifications.value.first { it.notificationId == "n1" }.read)
        assertTrue(!repo.notifications.value.first { it.notificationId == "n2" }.read)
        assertEquals(1, repo.unreadCount.value)
    }

    @Test
    fun `markAllRead clears unread count to zero`() = runTest(UnconfinedTestDispatcher()) {
        val repo = MockNotificationRepository(backgroundScope)
        repo.insert(sample("n1"))
        repo.insert(sample("n2"))

        repo.markAllRead()

        assertEquals(0, repo.unreadCount.value)
        assertTrue(repo.notifications.value.all { it.read })
    }

    @Test
    fun `clearRead removes only read notifications, unread survive`() = runTest(UnconfinedTestDispatcher()) {
        val repo = MockNotificationRepository(backgroundScope)
        repo.insert(sample("n1", read = true))
        repo.insert(sample("n2"))

        repo.clearRead()

        assertEquals(listOf("n2"), repo.notifications.value.map { it.notificationId })
    }
}

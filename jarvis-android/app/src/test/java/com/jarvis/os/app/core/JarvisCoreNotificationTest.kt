package com.jarvis.os.app.core

import com.jarvis.os.app.core.chat.AiRouter
import com.jarvis.os.app.core.chat.ChatSessionManager
import com.jarvis.os.app.core.chat.MockChatProvider
import com.jarvis.os.app.data.model.NotificationCategory
import com.jarvis.os.app.data.repository.MockApprovalRepository
import com.jarvis.os.app.data.repository.MockChatRepository
import com.jarvis.os.app.data.repository.MockConnectionRepository
import com.jarvis.os.app.data.repository.MockMemoryRepository
import com.jarvis.os.app.data.repository.MockNotificationRepository
import com.jarvis.os.app.data.repository.MockProjectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint 9 (PR2): proves the real claim, not just the pieces --
 * NotificationFactoryTest and MockNotificationRepositoryTest cover the
 * two halves in isolation, this covers that JarvisCore actually wires
 * a live connection transition all the way to a Notification a screen
 * would see, with no manual insert anywhere in the path. Every
 * dependency below is the real Mock* implementation (not a hand-rolled
 * test double) so this exercises the exact object graph Hilt would
 * assemble in the app, just constructed by hand.
 *
 * Uses runTest(UnconfinedTestDispatcher()) and passes `this` straight
 * through as JarvisCore's appScope -- the same fix applied to
 * MockConnectionRepositoryTest's flaky flow-collection test earlier in
 * this sprint. Unconfined means JarvisCore's init-block collectors run
 * eagerly to their first suspension point the moment JarvisCore is
 * constructed, and each emission below resumes an already-subscribed
 * collector synchronously, in-line -- no buffering assumptions, no
 * manual scheduler advancement, no race to reason about.
 */
class JarvisCoreNotificationTest {

    private fun buildCore(scope: CoroutineScope): JarvisCore {
        val chatSessionManager = ChatSessionManager()
        val aiRouter = AiRouter(setOf(MockChatProvider()))
        return JarvisCore(
            connections = MockConnectionRepository(),
            approvals = MockApprovalRepository(),
            memory = MockMemoryRepository(),
            projects = MockProjectRepository(),
            chat = MockChatRepository(aiRouter, chatSessionManager),
            notifications = MockNotificationRepository(scope),
            appScope = scope,
        )
    }

    @Test
    fun `approving and connecting produces a Connected notification with no manual insert`() = runTest(UnconfinedTestDispatcher()) {
        val core = buildCore(this)

        val connectionId = core.connections.connections.value.first { it.providerName == "Claude" }.connectionId
        core.approveConnection(connectionId)
        core.connectConnection(connectionId)
        core.markConnectionConnected(connectionId)

        val notification = core.notifications.notifications.value.firstOrNull { it.relatedEntityId == connectionId }
        requireNotNull(notification) { "expected a notification for the Claude connection reaching CONNECTED" }
        assertEquals(NotificationCategory.CONNECTION, notification.category)
        assertEquals("Claude connected", notification.title)
        assertTrue(!notification.read)
    }

    @Test
    fun `a connection error produces an Error-category notification`() = runTest(UnconfinedTestDispatcher()) {
        val core = buildCore(this)

        val connectionId = core.connections.connections.value.first { it.providerName == "Claude" }.connectionId
        core.approveConnection(connectionId)
        core.connectConnection(connectionId)
        core.markConnectionError(connectionId, "handshake timed out")

        val notification = core.notifications.notifications.value.firstOrNull { it.relatedEntityId == connectionId }
        requireNotNull(notification) { "expected a notification for the Claude connection reaching ERROR" }
        assertEquals(NotificationCategory.ERROR, notification.category)
        assertEquals("handshake timed out", notification.message)
    }

    @Test
    fun `markNotificationRead through JarvisCore actually flips the repository's read state`() = runTest(UnconfinedTestDispatcher()) {
        val core = buildCore(this)

        val connectionId = core.connections.connections.value.first { it.providerName == "Claude" }.connectionId
        core.approveConnection(connectionId)
        core.connectConnection(connectionId)
        core.markConnectionConnected(connectionId)

        val notification = core.notifications.notifications.value.first { it.relatedEntityId == connectionId }
        assertTrue(!notification.read)

        core.markNotificationRead(notification.notificationId)

        assertTrue(core.notifications.notifications.value.first { it.notificationId == notification.notificationId }.read)
        assertEquals(0, core.notifications.unreadCount.value)
    }
}

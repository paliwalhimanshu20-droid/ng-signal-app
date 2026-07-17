package com.jarvis.os.app.core

import com.jarvis.os.app.core.agents.DefaultMultiAiCoordinator
import com.jarvis.os.app.core.agents.MockAgentRegistry
import com.jarvis.os.app.core.agents.WatchTowerOrchestrator
import com.jarvis.os.app.core.chat.AiRouter
import com.jarvis.os.app.core.chat.ChatSessionManager
import com.jarvis.os.app.core.chat.MockChatProvider
import com.jarvis.os.app.core.intelligence.ContextManager
import com.jarvis.os.app.core.intelligence.ExecutiveBriefingEngine
import com.jarvis.os.app.core.intelligence.JarvisDecisionEngine
import com.jarvis.os.app.core.memory.ConversationMemoryImpl
import com.jarvis.os.app.core.memory.PersonalMemoryImpl
import com.jarvis.os.app.data.model.NotificationCategory
import com.jarvis.os.app.data.repository.MockApprovalRepository
import com.jarvis.os.app.data.repository.MockAuditRepository
import com.jarvis.os.app.data.repository.MockChatRepository
import com.jarvis.os.app.data.repository.MockConnectionRepository
import com.jarvis.os.app.data.repository.MockMemoryRepository
import com.jarvis.os.app.testutil.FakeNgSignalProStatusProvider
import com.jarvis.os.app.data.repository.MockNotificationRepository
import com.jarvis.os.app.data.repository.MockProjectRepository
import com.jarvis.os.app.data.repository.MockToolRepository
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
 * Uses runTest(UnconfinedTestDispatcher()) and passes `backgroundScope`
 * (not the test's own coroutine scope, `this`) as JarvisCore's
 * appScope. Unconfined still means JarvisCore's init-block collectors
 * run eagerly to their first suspension point the moment JarvisCore is
 * constructed, and each emission below resumes an already-subscribed
 * collector synchronously, in-line -- no buffering assumptions, no
 * manual scheduler advancement, no race to reason about. backgroundScope
 * specifically (Stabilization Sprint) replaces an earlier version that
 * passed `this`: JarvisCore's init block launches five collectors that
 * run for the object's entire lifetime (they collect a SharedFlow,
 * which never completes on its own), and runTest requires every
 * coroutine launched directly in the test's own scope to finish before
 * the test body returns -- so those five permanent collectors threw
 * UncompletedCoroutinesError on every test here. backgroundScope is
 * TestScope's dedicated home for exactly this shape of background work:
 * coroutines launched there share the same test dispatcher/scheduler
 * (so eager collection still behaves identically) but are cancelled
 * automatically when the test ends instead of being awaited.
 */
class JarvisCoreNotificationTest {

    private fun buildCore(scope: CoroutineScope): JarvisCore {
        val settingsRepository = com.jarvis.os.app.testutil.FakeSettingsRepository()
        val chatSessionManager = ChatSessionManager()
        val aiRouter = AiRouter(setOf(MockChatProvider(settingsRepository)))
        val approvalsRepo = MockApprovalRepository()
        val memoryRepo = MockMemoryRepository()
        val projectsRepo = MockProjectRepository()
        val chatRepo = MockChatRepository(aiRouter, chatSessionManager)
        val toolsRepo = MockToolRepository(emptySet(), approvalsRepo)
        val agentRegistry = MockAgentRegistry(emptySet())
        val multiAiCoordinator = DefaultMultiAiCoordinator(agentRegistry, approvalsRepo)
        val connectionsRepo = MockConnectionRepository()
        val notificationsRepo = MockNotificationRepository(scope)
        val auditRepo = MockAuditRepository()
        return JarvisCore(
            connections = connectionsRepo,
            approvals = approvalsRepo,
            memory = memoryRepo,
            projects = projectsRepo,
            chat = chatRepo,
            notifications = notificationsRepo,
            tools = toolsRepo,
            audit = auditRepo,
            contextManager = ContextManager(ConversationMemoryImpl(memoryRepo), PersonalMemoryImpl(memoryRepo), chatRepo, projectsRepo),
            decisionEngine = JarvisDecisionEngine(toolsRepo, agentRegistry),
            watchTower = WatchTowerOrchestrator(multiAiCoordinator, approvalsRepo),
            briefingEngine = ExecutiveBriefingEngine(
                projectsRepo, approvalsRepo, notificationsRepo, connectionsRepo, memoryRepo, agentRegistry, FakeNgSignalProStatusProvider(), settingsRepository, com.jarvis.os.app.testutil.FakeGitHubStatusProvider(),
            ),
            appScope = scope,
        )
    }

    @Test
    fun `approving and connecting produces a Connected notification with no manual insert`() = runTest(UnconfinedTestDispatcher()) {
        val core = buildCore(backgroundScope)

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
        val core = buildCore(backgroundScope)

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
        val core = buildCore(backgroundScope)

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

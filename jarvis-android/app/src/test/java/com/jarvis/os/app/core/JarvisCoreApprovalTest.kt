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
import com.jarvis.os.app.data.model.ApprovalKind
import com.jarvis.os.app.data.model.ApprovalOutcome
import com.jarvis.os.app.data.model.ConnectionStatus
import com.jarvis.os.app.data.model.NotificationCategory
import com.jarvis.os.app.data.model.PermissionScope
import com.jarvis.os.app.data.model.RiskLevel
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint 9 Final: proves Section 6's whole "Request Connection ->
 * Approval Created -> Owner Approves -> Connection CONNECTING ->
 * ... -> Notification -> Audit Record" flow actually happens end to
 * end through the real object graph, not just its individual pieces --
 * MockApprovalRepositoryTest and NotificationFactoryTest cover the
 * pieces in isolation, this covers the wiring between them that only
 * JarvisCore's init-block collectors provide. Same UnconfinedTestDispatcher
 * pattern as JarvisCoreNotificationTest -- see that file's docstring
 * for why.
 */
class JarvisCoreApprovalTest {

    private fun buildCore(scope: CoroutineScope): JarvisCore {
        val settingsRepository = com.jarvis.os.app.testutil.FakeSettingsRepository()
        val chatSessionManager = ChatSessionManager()
        val aiRouter = AiRouter(setOf(MockChatProvider(settingsRepository)), com.jarvis.os.app.testutil.FakePreferredProviderStore())
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
            intentRouter = com.jarvis.os.app.core.intelligence.KeywordIntentRouter(toolsRepo),
            tradingIntelligenceOrchestrator = com.jarvis.os.app.testutil.FakeTradingIntelligenceOrchestrator(),
            watchTower = WatchTowerOrchestrator(multiAiCoordinator, approvalsRepo),
            briefingEngine = ExecutiveBriefingEngine(
                projectsRepo, approvalsRepo, notificationsRepo, connectionsRepo, memoryRepo, agentRegistry, FakeNgSignalProStatusProvider(), settingsRepository, com.jarvis.os.app.testutil.FakeGitHubStatusProvider(),
            ),
            localIntentRouter = com.jarvis.os.app.core.intelligence.localintent.DefaultLocalIntentRouter(emptySet()),
            languageManager = com.jarvis.os.app.core.intelligence.LanguageManager(settingsRepository),
            appScope = scope,
        )
    }

    @Test
    fun `requesting a connection approval creates both records linked together`() = runTest(UnconfinedTestDispatcher()) {
        val core = buildCore(backgroundScope)

        core.requestConnectionApproval("provider-test", "TestProvider", setOf(PermissionScope.READ), PermissionScope.READ)

        val connection = core.connections.connections.value.first { it.providerName == "TestProvider" }
        val approval = core.approvals.items.value.first { it.relatedConnectionId == connection.connectionId }
        assertEquals(ConnectionStatus.PENDING_APPROVAL, connection.status)
        assertEquals(ApprovalOutcome.PENDING, approval.outcome)

        // ApprovalRequested is no longer dormant (see NotificationFactory's
        // docstring) -- this is a real notification from a real publisher.
        val notification = core.notifications.notifications.value.firstOrNull { it.relatedEntityId == approval.approvalId }
        requireNotNull(notification)
        assertEquals(NotificationCategory.APPROVAL, notification.category)
    }

    @Test
    fun `approving a linked approval drives the connection to CONNECTING automatically`() = runTest(UnconfinedTestDispatcher()) {
        val core = buildCore(backgroundScope)
        core.requestConnectionApproval("provider-test", "TestProvider", setOf(PermissionScope.READ), PermissionScope.READ)
        val connection = core.connections.connections.value.first { it.providerName == "TestProvider" }
        val approval = core.approvals.items.value.first { it.relatedConnectionId == connection.connectionId }

        core.approveApproval(approval.approvalId)

        // "Connections react automatically" -- no code outside this test
        // called connections.approve()/connect() directly.
        assertEquals(ConnectionStatus.CONNECTING, core.connections.connections.value.first { it.connectionId == connection.connectionId }.status)

        val approvedNotification = core.notifications.notifications.value.firstOrNull {
            it.category == NotificationCategory.APPROVAL && it.relatedEntityId == connection.connectionId
        }
        requireNotNull(approvedNotification)
        assertEquals("Connect TestProvider approved", approvedNotification.title)
    }

    @Test
    fun `revoking an approved, connected approval suspends the live connection`() = runTest(UnconfinedTestDispatcher()) {
        val core = buildCore(backgroundScope)
        core.requestConnectionApproval("provider-test", "TestProvider", setOf(PermissionScope.READ), PermissionScope.READ)
        val connection = core.connections.connections.value.first { it.providerName == "TestProvider" }
        val approval = core.approvals.items.value.first { it.relatedConnectionId == connection.connectionId }
        core.approveApproval(approval.approvalId)
        core.markConnectionConnected(connection.connectionId)
        assertEquals(ConnectionStatus.CONNECTED, core.connections.connections.value.first { it.connectionId == connection.connectionId }.status)

        core.revokeApproval(approval.approvalId, reason = "no longer needed")

        assertEquals(ConnectionStatus.SUSPENDED, core.connections.connections.value.first { it.connectionId == connection.connectionId }.status)
        val revokedNotification = core.notifications.notifications.value.first {
            it.category == NotificationCategory.APPROVAL && it.relatedEntityId == connection.connectionId && it.title.endsWith("revoked")
        }
        assertEquals("no longer needed", revokedNotification.message)
    }

    @Test
    fun `rejecting a linked approval rejects the connection, not silently ignored`() = runTest(UnconfinedTestDispatcher()) {
        val core = buildCore(backgroundScope)
        core.requestConnectionApproval("provider-test", "TestProvider", setOf(PermissionScope.READ), PermissionScope.READ)
        val connection = core.connections.connections.value.first { it.providerName == "TestProvider" }
        val approval = core.approvals.items.value.first { it.relatedConnectionId == connection.connectionId }

        core.rejectApproval(approval.approvalId)

        assertEquals(ConnectionStatus.REJECTED, core.connections.connections.value.first { it.connectionId == connection.connectionId }.status)
    }

    @Test
    fun `a plain permission-request approval with no related connection never touches ConnectionRepository`() = runTest(UnconfinedTestDispatcher()) {
        val core = buildCore(backgroundScope)
        val connectionsBefore = core.connections.connections.value

        val approval = core.requestApproval(
            kind = ApprovalKind.PERMISSION_REQUEST,
            title = "Deploy update",
            reason = "Tier 2 action",
            riskLevel = RiskLevel.HIGH,
        )
        core.approveApproval(approval.approvalId)

        // reactToApprovalTransition must be a no-op here -- no
        // relatedConnectionId means nothing to react to.
        assertEquals(connectionsBefore, core.connections.connections.value)
        assertEquals(ApprovalOutcome.APPROVED, core.approvals.items.value.first { it.approvalId == approval.approvalId }.outcome)
    }

    @Test
    fun `audit log survives every stage of the full flow and is never empty`() = runTest(UnconfinedTestDispatcher()) {
        val core = buildCore(backgroundScope)
        core.requestConnectionApproval("provider-test", "TestProvider", setOf(PermissionScope.READ), PermissionScope.READ)
        val approval = core.approvals.items.value.first { it.title == "Connect TestProvider" }

        core.approveApproval(approval.approvalId)
        core.revokeApproval(approval.approvalId)
        core.approveApproval(approval.approvalId)

        val records = core.approvals.auditLog.value.filter { it.approvalId == approval.approvalId }
        assertEquals(4, records.size) // created, approved, revoked, approved again
        assertNull(records.first().previousState)
        assertTrue(records.none { it.newState == ApprovalOutcome.PENDING && it.previousState != null })
    }
}

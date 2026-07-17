package com.jarvis.os.app.core.intelligence

import com.jarvis.os.app.core.agents.BatmanAgent
import com.jarvis.os.app.core.agents.DefaultMultiAiCoordinator
import com.jarvis.os.app.core.agents.MockAgentRegistry
import com.jarvis.os.app.core.chat.AiRouter
import com.jarvis.os.app.core.chat.MockChatProvider
import com.jarvis.os.app.data.repository.MockApprovalRepository
import com.jarvis.os.app.data.repository.MockAuditRepository
import com.jarvis.os.app.data.repository.MockConnectionRepository
import com.jarvis.os.app.data.repository.MockMemoryRepository
import com.jarvis.os.app.testutil.FakeGitHubStatusProvider
import com.jarvis.os.app.testutil.FakeNgSignalProStatusProvider
import com.jarvis.os.app.data.repository.MockNotificationRepository
import com.jarvis.os.app.data.repository.MockProjectRepository
import com.jarvis.os.app.testutil.FakeSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint 12 Phase 3. Every dependency below is the real Mock*
 * implementation, same "exercise the exact object graph Hilt would
 * assemble" convention JarvisCoreNotificationTest already established.
 * A plain CoroutineScope(Dispatchers.Unconfined) (not runTest's
 * backgroundScope) is used for MockNotificationRepository's constructor
 * -- see JarvisCoreNotificationTest's own docstring for the general
 * reasoning; here it's paired with runTest(UnconfinedTestDispatcher())
 * on every test now that generateMorningBriefing is suspend (it reads
 * the Owner's language preference -- see FakeSettingsRepository).
 */
class ExecutiveBriefingEngineTest {

    private fun engine(agents: MockAgentRegistry = MockAgentRegistry(emptySet())) = ExecutiveBriefingEngine(
        projects = MockProjectRepository(),
        approvals = MockApprovalRepository(),
        notifications = MockNotificationRepository(CoroutineScope(Dispatchers.Unconfined)),
        connections = MockConnectionRepository(),
        memory = MockMemoryRepository(),
        agents = agents,
        ngSignalPro = FakeNgSignalProStatusProvider(),
        settingsRepository = FakeSettingsRepository(),
        gitHub = FakeGitHubStatusProvider(),
    )

    @Test
    fun `briefing always includes a greeting and project status`() = runTest(UnconfinedTestDispatcher()) {
        val briefing = engine().generateMorningBriefing()
        // Greeting is genuinely time-of-day dependent now (Personality
        // Bible: "every morning, every afternoon, every evening") -- no
        // longer a fixed string a test can assert on directly.
        assertTrue(briefing.greeting.isNotBlank())
        assertTrue(briefing.lines.any { it.contains("ProjectOS") })
    }

    @Test
    fun `briefing reports NG Signal Pro honestly when there is no live connection`() = runTest(UnconfinedTestDispatcher()) {
        val briefing = engine().generateMorningBriefing()
        assertTrue(briefing.lines.any { it.contains("isn't connected yet") })
    }

    @Test
    fun `briefing has no Watch Tower line when no specialist has ever run`() = runTest(UnconfinedTestDispatcher()) {
        val briefing = engine().generateMorningBriefing()
        assertFalse(briefing.lines.any { it.contains("Batman") })
    }

    @Test
    fun `briefing surfaces the latest result per specialist once one has actually run`() = runTest(UnconfinedTestDispatcher()) {
        val router = AiRouter(setOf(MockChatProvider(FakeSettingsRepository())))
        val batman = BatmanAgent(router)
        val agentRegistry = MockAgentRegistry(setOf(batman))
        val approvals = MockApprovalRepository()
        val coordinator = DefaultMultiAiCoordinator(agentRegistry, approvals)

        val task = com.jarvis.os.app.data.model.AgentTask("t1", "architecture check")
        val approval = approvals.requestApproval(
            kind = com.jarvis.os.app.data.model.ApprovalKind.AGENT_TASK,
            title = "test", reason = "test",
            riskLevel = com.jarvis.os.app.data.model.RiskLevel.MODERATE,
            requestedBy = "test", relatedAgentTaskId = task.taskId,
        )
        approvals.approve(approval.approvalId, "owner")
        coordinator.coordinate(task, approval.approvalId)

        val briefing = engine(agentRegistry).generateMorningBriefing()
        assertTrue("expected a Batman line, got: ${briefing.lines}", briefing.lines.any { it.contains("Batman") })
    }

    @Test
    fun `pending approvals line only appears when something is actually pending`() = runTest(UnconfinedTestDispatcher()) {
        val approvals = MockApprovalRepository() // seeds 3 PENDING approvals by construction
        val engineWithSeeded = ExecutiveBriefingEngine(
            projects = MockProjectRepository(), approvals = approvals,
            notifications = MockNotificationRepository(CoroutineScope(Dispatchers.Unconfined)),
            connections = MockConnectionRepository(), memory = MockMemoryRepository(),
            agents = MockAgentRegistry(emptySet()), ngSignalPro = FakeNgSignalProStatusProvider(),
            settingsRepository = FakeSettingsRepository(), gitHub = FakeGitHubStatusProvider(),
        )
        val briefing = engineWithSeeded.generateMorningBriefing()
        assertTrue(briefing.lines.any { it.contains("waiting on you") })
    }
}

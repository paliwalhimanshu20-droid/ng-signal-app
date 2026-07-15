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
import com.jarvis.os.app.data.repository.MockNgSignalProStatusProvider
import com.jarvis.os.app.data.repository.MockNotificationRepository
import com.jarvis.os.app.data.repository.MockProjectRepository
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
 * in the non-suspend test methods below -- these don't run inside
 * runTest at all (ExecutiveBriefingEngine's own methods aren't
 * suspend), so there's no UncompletedCoroutinesError risk to guard
 * against here the way JarvisCore's tests must.
 */
class ExecutiveBriefingEngineTest {

    private fun engine(agents: MockAgentRegistry = MockAgentRegistry(emptySet())) = ExecutiveBriefingEngine(
        projects = MockProjectRepository(),
        approvals = MockApprovalRepository(),
        notifications = MockNotificationRepository(CoroutineScope(Dispatchers.Unconfined)),
        connections = MockConnectionRepository(),
        memory = MockMemoryRepository(),
        agents = agents,
        ngSignalPro = MockNgSignalProStatusProvider(),
    )

    @Test
    fun `briefing always includes a greeting and project status`() {
        val briefing = engine().generateMorningBriefing()
        assertTrue(briefing.greeting == "Good morning.")
        assertTrue(briefing.lines.any { it.contains("active project") })
    }

    @Test
    fun `briefing reports NG Signal Pro honestly when there is no live connection`() {
        val briefing = engine().generateMorningBriefing()
        assertTrue(briefing.lines.any { it.contains("no live connection") })
    }

    @Test
    fun `briefing has no Watch Tower line when no specialist has ever run`() {
        val briefing = engine().generateMorningBriefing()
        assertFalse(briefing.lines.any { it.contains("Batman") })
    }

    @Test
    fun `briefing surfaces the latest result per specialist once one has actually run`() = runTest(UnconfinedTestDispatcher()) {
        val router = AiRouter(setOf(MockChatProvider()))
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
    fun `pending approvals line only appears when something is actually pending`() {
        val approvals = MockApprovalRepository() // seeds 3 PENDING approvals by construction
        val engineWithSeeded = ExecutiveBriefingEngine(
            projects = MockProjectRepository(), approvals = approvals,
            notifications = MockNotificationRepository(CoroutineScope(Dispatchers.Unconfined)),
            connections = MockConnectionRepository(), memory = MockMemoryRepository(),
            agents = MockAgentRegistry(emptySet()), ngSignalPro = MockNgSignalProStatusProvider(),
        )
        val briefing = engineWithSeeded.generateMorningBriefing()
        assertTrue(briefing.lines.any { it.contains("waiting for your review") })
    }
}

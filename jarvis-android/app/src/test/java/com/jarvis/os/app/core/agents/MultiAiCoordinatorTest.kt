package com.jarvis.os.app.core.agents

import com.jarvis.os.app.core.chat.AiRouter
import com.jarvis.os.app.core.chat.MockChatProvider
import com.jarvis.os.app.data.model.AgentTask
import com.jarvis.os.app.data.model.ApprovalKind
import com.jarvis.os.app.data.repository.MockApprovalRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiAiCoordinatorTest {

    private fun setup(): Triple<DefaultMultiAiCoordinator, MockAgentRegistry, MockApprovalRepository> {
        val router = AiRouter(setOf(MockChatProvider()))
        val registry = MockAgentRegistry(setOf(ResearchAgent(router)))
        val approvals = MockApprovalRepository()
        return Triple(DefaultMultiAiCoordinator(registry, approvals), registry, approvals)
    }

    @Test
    fun `coordinate without an approvalId requests approval and runs nothing`() = runTest {
        val (coordinator, registry, approvals) = setup()
        val results = coordinator.coordinate(AgentTask("t1", "goal"))
        assertFalse(results.first().success)
        assertTrue(registry.results.value.isEmpty())
        assertEquals(1, approvals.items.value.count { it.kind == ApprovalKind.AGENT_TASK })
    }

    @Test
    fun `coordinate runs the task once its approval is APPROVED`() = runTest {
        val (coordinator, registry, approvals) = setup()
        coordinator.coordinate(AgentTask("t1", "goal"))
        val approvalId = approvals.items.value.first { it.kind == ApprovalKind.AGENT_TASK }.approvalId
        approvals.approve(approvalId, "owner")

        val results = coordinator.coordinate(AgentTask("t1", "goal"), approvalId)
        assertTrue(results.isNotEmpty())
        assertTrue(registry.results.value.isNotEmpty())
    }

    @Test
    fun `an approval for a different task cannot authorize this one`() = runTest {
        val (coordinator, _, approvals) = setup()
        val approval = approvals.requestApproval(
            kind = ApprovalKind.AGENT_TASK,
            title = "Run agent task: other",
            reason = "test",
            riskLevel = com.jarvis.os.app.data.model.RiskLevel.MODERATE,
            relatedAgentTaskId = "different-task-id",
        )
        approvals.approve(approval.approvalId, "owner")

        val results = coordinator.coordinate(AgentTask("t1", "goal"), approval.approvalId)
        assertFalse(results.first().success)
    }
}

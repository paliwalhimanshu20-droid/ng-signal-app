package com.jarvis.os.app.core.agents

import com.jarvis.os.app.core.chat.AiRouter
import com.jarvis.os.app.core.chat.MockChatProvider
import com.jarvis.os.app.data.model.AiCapability
import com.jarvis.os.app.data.model.ApprovalOutcome
import com.jarvis.os.app.data.repository.MockApprovalRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint 12 Phase 2. Builds the real object graph (AiRouter with the
 * eight Watch Tower agents, DefaultMultiAiCoordinator,
 * MockApprovalRepository) by hand, same convention as
 * JarvisCoreApprovalTest -- this exercises WatchTowerOrchestrator's
 * actual dependency on MultiAiCoordinator's approval gate, not a stub
 * of it.
 */
class WatchTowerOrchestratorTest {

    private fun orchestrator(): Pair<WatchTowerOrchestrator, MockApprovalRepository> {
        val router = AiRouter(setOf(MockChatProvider()))
        val agents = setOf(
            BatmanAgent(router), FlashAgent(router), IronManAgent(router), DoctorStrangeAgent(router),
            CaptainAmericaAgent(router), SpiderManAgent(router), NickFuryAgent(router), ProfessorXAgent(router),
        )
        val registry = MockAgentRegistry(agents)
        val approvals = MockApprovalRepository()
        val coordinator = DefaultMultiAiCoordinator(registry, approvals)
        return WatchTowerOrchestrator(coordinator, approvals) to approvals
    }

    @Test
    fun `requiredCapabilitiesFor selects the right capability per keyword`() {
        val (orchestrator, _) = orchestrator()
        assertTrue(orchestrator.requiredCapabilitiesFor("architecture review").contains(AiCapability.ARCHITECTURE_REVIEW))
        assertTrue(orchestrator.requiredCapabilitiesFor("check for a performance regression").contains(AiCapability.PERFORMANCE_ANALYSIS))
        assertTrue(orchestrator.requiredCapabilitiesFor("what's our testing coverage").contains(AiCapability.TESTING))
        assertTrue(orchestrator.requiredCapabilitiesFor("strategy and roadmap planning").contains(AiCapability.STRATEGY))
    }

    @Test
    fun `an unrecognized topic matches no capability -- meaning convene everyone`() {
        val (orchestrator, _) = orchestrator()
        assertEquals(emptySet<AiCapability>(), orchestrator.requiredCapabilitiesFor("hello there"))
    }

    @Test
    fun `requestConvene creates a pending approval and runs nothing`() = runTest {
        val (orchestrator, approvals) = orchestrator()
        val summary = orchestrator.requestConvene("full review")
        assertNotNull(summary.approvalId)
        assertTrue(summary.perSpecialist.isEmpty())
        val approval = approvals.items.value.first { it.approvalId == summary.approvalId }
        assertEquals(ApprovalOutcome.PENDING, approval.outcome)
    }

    @Test
    fun `convene without approval fails cleanly and still runs nothing`() = runTest {
        val (orchestrator, _) = orchestrator()
        val summary = orchestrator.convene("full review", approvalId = "does-not-exist")
        assertTrue(summary.perSpecialist.none { it.success })
    }

    @Test
    fun `convene after approval actually runs the matched specialists`() = runTest {
        val (orchestrator, approvals) = orchestrator()
        val requested = orchestrator.requestConvene("architecture review")
        approvals.approve(requested.approvalId!!, "owner")

        val result = orchestrator.convene("architecture review", requested.approvalId)
        assertTrue(result.perSpecialist.isNotEmpty())
        assertTrue(result.perSpecialist.all { it.success })
        // architecture review -> ARCHITECTURE_REVIEW capability -> only Batman declares it
        assertEquals(listOf("batman-agent"), result.perSpecialist.map { it.agentId })
    }

    @Test
    fun `an unrecognized topic convenes every specialist once approved`() = runTest {
        val (orchestrator, approvals) = orchestrator()
        val requested = orchestrator.requestConvene("hello there")
        approvals.approve(requested.approvalId!!, "owner")

        val result = orchestrator.convene("hello there", requested.approvalId)
        assertEquals(8, result.perSpecialist.size)
    }
}

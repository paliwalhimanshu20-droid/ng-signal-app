package com.jarvis.os.app.core.agents

import com.jarvis.os.app.core.chat.AiRouter
import com.jarvis.os.app.core.chat.MockChatProvider
import com.jarvis.os.app.core.chat.MockClaudeProvider
import com.jarvis.os.app.core.chat.MockGptProvider
import com.jarvis.os.app.data.model.AgentTask
import com.jarvis.os.app.data.model.AiCapability
import com.jarvis.os.app.testutil.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRegistryTest {

    private fun router() = AiRouter(setOf(MockChatProvider(FakeSettingsRepository()), MockClaudeProvider(), MockGptProvider()))

    private fun registry(router: AiRouter = router()) =
        MockAgentRegistry(setOf(ResearchAgent(router), CodeAgent(router)))

    @Test
    fun `assign runs the named agent and records the result`() = runTest {
        val registry = registry()
        val result = registry.assign("research-agent", AgentTask("t1", "Summarize the sprint"))
        assertNotNull(result)
        assertEquals(1, registry.results.value.size)
    }

    @Test
    fun `assign for unknown agent returns null and records nothing`() = runTest {
        val registry = registry()
        val result = registry.assign("nonexistent", AgentTask("t1", "goal"))
        assertEquals(null, result)
        assertTrue(registry.results.value.isEmpty())
    }

    @Test
    fun `broadcast only runs agents matching required capabilities`() = runTest {
        val registry = registry()
        val results = registry.broadcast(AgentTask("t2", "write code", requiredCapabilities = setOf(AiCapability.CODE_GENERATION)))
        assertEquals(1, results.size)
        assertEquals("code-agent", results.first().agentId)
    }

    @Test
    fun `broadcast with no required capabilities runs every agent`() = runTest {
        val registry = registry()
        val results = registry.broadcast(AgentTask("t3", "general task"))
        assertEquals(2, results.size)
    }

    @Test
    fun `resolveConflict prefers success and higher capability overlap`() = runTest {
        val registry = registry()
        val task = AgentTask("t4", "goal", requiredCapabilities = setOf(AiCapability.CODE_GENERATION))
        val results = registry.broadcast(AgentTask("t4", "goal"))
        val winner = registry.resolveConflict(task, results)
        assertEquals("code-agent", winner?.agentId)
    }
}

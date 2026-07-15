package com.jarvis.os.app.core.intelligence

import com.jarvis.os.app.core.agents.CodeAgent
import com.jarvis.os.app.core.agents.MockAgentRegistry
import com.jarvis.os.app.core.agents.ResearchAgent
import com.jarvis.os.app.core.chat.AiRouter
import com.jarvis.os.app.core.chat.MockChatProvider
import com.jarvis.os.app.core.tools.CalculatorTool
import com.jarvis.os.app.core.tools.ProjectNoteTool
import com.jarvis.os.app.data.repository.MockApprovalRepository
import com.jarvis.os.app.data.repository.MockToolRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisDecisionEngineTest {

    private fun engine(): JarvisDecisionEngine {
        val router = AiRouter(setOf(MockChatProvider()))
        val agentRegistry = MockAgentRegistry(setOf(ResearchAgent(router), CodeAgent(router)))
        val toolRepository = MockToolRepository(setOf(CalculatorTool(), ProjectNoteTool()), MockApprovalRepository())
        return JarvisDecisionEngine(toolRepository, agentRegistry)
    }

    @Test
    fun `a message mentioning a project keyword needs project context`() {
        assertTrue(engine().decide("what is pending right now").needsProjectContext)
        assertTrue(engine().decide("is anything blocked").needsProjectContext)
    }

    @Test
    fun `a plain conversational message does not need project context`() {
        assertFalse(engine().decide("good morning").needsProjectContext)
    }

    @Test
    fun `a message naming a registered tool is matched to it`() {
        val decision = engine().decide("use the Calculator to total this")
        assertEquals("calculator", decision.matchedTool?.toolId)
    }

    @Test
    fun `a message naming no tool has a null matchedTool`() {
        assertNull(engine().decide("good morning").matchedTool)
    }

    @Test
    fun `a message naming a registered agent is matched to it`() {
        val decision = engine().decide("can the research agent look into this")
        assertEquals("research-agent", decision.matchedAgent?.agentId)
    }

    @Test
    fun `a message naming no agent has a null matchedAgent`() {
        assertNull(engine().decide("good morning").matchedAgent)
    }

    @Test
    fun `a message can match project context, a tool, and an agent all at once`() {
        val decision = engine().decide("project status: use the calculator, then ask the code agent to review it")
        assertTrue(decision.needsProjectContext)
        assertEquals("calculator", decision.matchedTool?.toolId)
        assertEquals("code-agent", decision.matchedAgent?.agentId)
    }

    @Test
    fun `a briefing-style message sets needsBriefing`() {
        assertTrue(engine().decide("good morning").needsBriefing)
        assertTrue(engine().decide("can you brief me").needsBriefing)
        assertTrue(engine().decide("catch me up on everything").needsBriefing)
    }

    @Test
    fun `a plain question does not set needsBriefing`() {
        assertFalse(engine().decide("what is 2 plus 2").needsBriefing)
    }

    @Test
    fun `an orchestration-style message sets needsOrchestration`() {
        assertTrue(engine().decide("let's convene watch tower on this").needsOrchestration)
        assertTrue(engine().decide("can we do a full review of the sprint").needsOrchestration)
    }

    @Test
    fun `a plain question does not set needsOrchestration`() {
        assertFalse(engine().decide("what is 2 plus 2").needsOrchestration)
    }
}

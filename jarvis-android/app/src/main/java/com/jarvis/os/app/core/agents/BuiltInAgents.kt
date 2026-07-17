package com.jarvis.os.app.core.agents

import com.jarvis.os.app.core.chat.AiRouter
import com.jarvis.os.app.data.model.AgentResult
import com.jarvis.os.app.data.model.AgentTask
import com.jarvis.os.app.data.model.AiCapability
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 11: two real agents so AgentRegistry and MultiAiCoordinator
 * have more than one to coordinate between (mirrors PR4 shipping three
 * ChatProviders for the same reason). Each delegates its actual "work"
 * to AiRouter.routeFor(capabilities) -- an agent in this codebase is a
 * specialty + a capability profile wrapped around the same AI-routing
 * layer PR4 built, not a second, parallel execution engine. Real
 * task-specific logic (e.g. ResearchAgent actually calling a search
 * tool) is future work once a real network-calling provider or tool
 * exists to call -- see WatchTowerAgents.kt's docstring for the same
 * honesty limitation, and Phase 0's fix to the output text below (it
 * used to say "routed ... to provider 'X'" directly to the Owner).
 */
@Singleton
class ResearchAgent @Inject constructor(
    private val router: AiRouter,
) : Agent {
    override val agentId = "research-agent"
    override val name = "Research Agent"
    override val specialty = "Long-context research and synthesis"
    override val capabilities = setOf(AiCapability.REASONING, AiCapability.LONG_CONTEXT)

    override suspend fun run(task: AgentTask): AgentResult {
        router.routeFor(capabilities intersect task.requiredCapabilities.ifEmpty { capabilities })
        return AgentResult(
            taskId = task.taskId,
            agentId = agentId,
            success = true,
            output = "Research Agent reviewed \"${task.goal}\" and confirmed readiness to assist.",
            completedAt = Instant.now(),
        )
    }
}

@Singleton
class CodeAgent @Inject constructor(
    private val router: AiRouter,
) : Agent {
    override val agentId = "code-agent"
    override val name = "Code Agent"
    override val specialty = "Code generation and review"
    override val capabilities = setOf(AiCapability.CODE_GENERATION, AiCapability.TOOL_USE)

    override suspend fun run(task: AgentTask): AgentResult {
        router.routeFor(capabilities intersect task.requiredCapabilities.ifEmpty { capabilities })
        return AgentResult(
            taskId = task.taskId,
            agentId = agentId,
            success = true,
            output = "Code Agent reviewed \"${task.goal}\" and confirmed readiness to assist.",
            completedAt = Instant.now(),
        )
    }
}

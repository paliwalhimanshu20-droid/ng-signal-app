package com.jarvis.os.app.core.agents

import com.jarvis.os.app.core.chat.AiRouter
import com.jarvis.os.app.data.model.AgentResult
import com.jarvis.os.app.data.model.AgentTask
import com.jarvis.os.app.data.model.AiCapability
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 12 "Watch Tower Orchestration": eight named specialists,
 * mirroring the same multi-agent review framework Ankush's NG Signal
 * Pro platform already uses for PR-based proposals (Batman, Flash,
 * Iron Man, Doctor Strange, Captain America, Spider-Man, Nick Fury,
 * Professor X, with Ankush as The Watcher) -- brought into JARVIS OS
 * itself as real, registered Agent implementations rather than a
 * process convention that only exists outside the app.
 *
 * HONESTY LIMITATION, same shape as ResearchAgent/CodeAgent's own
 * docstring: each agent here delegates to AiRouter.routeFor exactly
 * like Sprint 11's two agents did, and this codebase still has no live
 * AI provider wired in (see MockChatProvider/ContextManager's
 * docstrings). That means a specialist's `run()` result right now is a
 * ROUTING CONFIRMATION ("routed to provider X"), not an actual
 * architectural opinion, performance measurement, or test coverage
 * finding -- there is no model call happening yet that could produce
 * one. WatchTowerOrchestrator's docstring repeats this same note where
 * it's most visible (the executive summary it produces), and the
 * Sprint 12 integration report calls it out as a named limitation --
 * this is intentional, not an oversight: shipping invented-sounding
 * "Batman found one issue" text with nothing real behind it would be
 * exactly the fake-success dishonesty this codebase's "no fake
 * success" rule exists to prevent, applied here to fabricated
 * *findings* instead of a fabricated tool result.
 *
 * Each agent's `specialty` string is deliberately short and
 * name-derived (matches JarvisDecisionEngine's existing keyword
 * heuristic -- "batman", "flash", etc. all appear literally in each
 * agent's own name, so no separate keyword table is needed there).
 */
@Singleton
class BatmanAgent @Inject constructor(private val router: AiRouter) : Agent {
    override val agentId = "batman-agent"
    override val name = "Batman"
    override val specialty = "Architecture review and technical risk detection"
    override val capabilities = setOf(AiCapability.REASONING, AiCapability.ARCHITECTURE_REVIEW)

    override suspend fun run(task: AgentTask): AgentResult = routedResult(router, this, task)
}

@Singleton
class FlashAgent @Inject constructor(private val router: AiRouter) : Agent {
    override val agentId = "flash-agent"
    override val name = "Flash"
    override val specialty = "Performance and regression analysis"
    override val capabilities = setOf(AiCapability.PERFORMANCE_ANALYSIS)

    override suspend fun run(task: AgentTask): AgentResult = routedResult(router, this, task)
}

@Singleton
class IronManAgent @Inject constructor(private val router: AiRouter) : Agent {
    override val agentId = "ironman-agent"
    override val name = "Iron Man"
    override val specialty = "Engineering feasibility and build strategy"
    override val capabilities = setOf(AiCapability.CODE_GENERATION, AiCapability.TOOL_USE)

    override suspend fun run(task: AgentTask): AgentResult = routedResult(router, this, task)
}

@Singleton
class DoctorStrangeAgent @Inject constructor(private val router: AiRouter) : Agent {
    override val agentId = "doctorstrange-agent"
    override val name = "Doctor Strange"
    override val specialty = "Edge-case and failure-scenario analysis"
    override val capabilities = setOf(AiCapability.REASONING, AiCapability.LONG_CONTEXT)

    override suspend fun run(task: AgentTask): AgentResult = routedResult(router, this, task)
}

@Singleton
class CaptainAmericaAgent @Inject constructor(private val router: AiRouter) : Agent {
    override val agentId = "captainamerica-agent"
    override val name = "Captain America"
    override val specialty = "Governance and approval recommendation"
    override val capabilities = setOf(AiCapability.REASONING)

    override suspend fun run(task: AgentTask): AgentResult = routedResult(router, this, task)
}

@Singleton
class SpiderManAgent @Inject constructor(private val router: AiRouter) : Agent {
    override val agentId = "spiderman-agent"
    override val name = "Spider-Man"
    override val specialty = "Test coverage and quality assurance"
    override val capabilities = setOf(AiCapability.TESTING)

    override suspend fun run(task: AgentTask): AgentResult = routedResult(router, this, task)
}

@Singleton
class NickFuryAgent @Inject constructor(private val router: AiRouter) : Agent {
    override val agentId = "nickfury-agent"
    override val name = "Nick Fury"
    override val specialty = "Cross-team coordination and intelligence gathering"
    override val capabilities = setOf(AiCapability.COORDINATION)

    override suspend fun run(task: AgentTask): AgentResult = routedResult(router, this, task)
}

@Singleton
class ProfessorXAgent @Inject constructor(private val router: AiRouter) : Agent {
    override val agentId = "professorx-agent"
    override val name = "Professor X"
    override val specialty = "Strategic planning and long-term insight"
    override val capabilities = setOf(AiCapability.STRATEGY, AiCapability.LONG_CONTEXT)

    override suspend fun run(task: AgentTask): AgentResult = routedResult(router, this, task)
}

/** Shared by all eight agents above -- identical shape to ResearchAgent/CodeAgent's own run() body, factored out once instead of copy-pasted eight times. */
private suspend fun routedResult(router: AiRouter, agent: Agent, task: AgentTask): AgentResult {
    val capabilities = agent.capabilities intersect task.requiredCapabilities.ifEmpty { agent.capabilities }
    val provider = router.routeFor(capabilities)
    return AgentResult(
        taskId = task.taskId,
        agentId = agent.agentId,
        success = true,
        output = "${agent.name} routed \"${task.goal}\" to provider '${provider.id}' (${provider.displayName}).",
        completedAt = Instant.now(),
    )
}

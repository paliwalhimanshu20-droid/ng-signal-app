package com.jarvis.os.app.core.agents

import com.jarvis.os.app.data.model.AgentDescriptor
import com.jarvis.os.app.data.model.AgentResult
import com.jarvis.os.app.data.model.AgentStatus
import com.jarvis.os.app.data.model.AgentTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 11 "Agent Orchestration": register specialist agents, assign
 * tasks, track status, collect results -- one repository-shaped class
 * over the Hilt-injected Set<Agent> (same multibinding-discovery
 * pattern AiRouter uses for ChatProvider). `results` is append-only,
 * same guarantee as every other audit-shaped list in this codebase.
 *
 * "Resolve conflicts / merge outputs" (the sprint brief's other two
 * Agent Orchestration bullets): with only two, non-overlapping-goal
 * agents shipped this sprint (see BuiltInAgents.kt), there is no real
 * conflict for `resolveConflict` to arbitrate yet beyond the
 * documented policy below -- shipping a conflict resolver with no real
 * conflict to verify it against would be exactly the kind of
 * unverified speculative code this codebase's working rules argue
 * against (see this repo's own "instrument first, evidence before
 * fixing" rule, applied here as "a real second opinion before a real
 * resolver").
 */
interface AgentRegistry {
    val agents: StateFlow<List<AgentDescriptor>>
    val results: StateFlow<List<AgentResult>>

    suspend fun assign(agentId: String, task: AgentTask): AgentResult?

    /** Runs `task` on every bound agent that declares at least one of task.requiredCapabilities (or every agent, if the task requires none) and returns all results -- the "collect results" half of orchestration. */
    suspend fun broadcast(task: AgentTask): List<AgentResult>

    /**
     * Deterministic policy, not a model call (see this interface's
     * class docstring): prefer the result with `success == true`; among
     * successes (or if none succeeded) prefer the one from the agent
     * whose declared capability set has the largest overlap with the
     * task's requiredCapabilities; ties keep the first result in
     * `results` order. Returns null for an empty list.
     */
    fun resolveConflict(task: AgentTask, candidates: List<AgentResult>): AgentResult?
}

@Singleton
class MockAgentRegistry @Inject constructor(
    private val boundAgents: Set<@JvmSuppressWildcards Agent>,
) : AgentRegistry {

    private val agentsById = boundAgents.associateBy { it.agentId }

    private val _statusByAgent = MutableStateFlow(boundAgents.associate { it.agentId to AgentStatus.IDLE })

    override val agents: StateFlow<List<AgentDescriptor>> = MutableStateFlow(
        boundAgents.map { AgentDescriptor(it.agentId, it.name, it.specialty, it.capabilities, AgentStatus.IDLE) },
    ).asStateFlow()

    private val _results = MutableStateFlow<List<AgentResult>>(emptyList())
    override val results: StateFlow<List<AgentResult>> = _results.asStateFlow()

    override suspend fun assign(agentId: String, task: AgentTask): AgentResult? {
        val agent = agentsById[agentId] ?: return null
        _statusByAgent.update { it + (agentId to AgentStatus.RUNNING) }
        val result = try {
            agent.run(task)
        } catch (e: Exception) {
            AgentResult(task.taskId, agentId, success = false, output = "Agent threw: ${e.message}", completedAt = java.time.Instant.now())
        }
        _statusByAgent.update { it + (agentId to if (result.success) AgentStatus.SUCCEEDED else AgentStatus.FAILED) }
        _results.update { it + result }
        return result
    }

    override suspend fun broadcast(task: AgentTask): List<AgentResult> {
        val eligible = boundAgents.filter { agent ->
            task.requiredCapabilities.isEmpty() || (agent.capabilities intersect task.requiredCapabilities).isNotEmpty()
        }
        return eligible.mapNotNull { assign(it.agentId, task) }
    }

    override fun resolveConflict(task: AgentTask, candidates: List<AgentResult>): AgentResult? {
        if (candidates.isEmpty()) return null
        return candidates
            .sortedWith(
                compareByDescending<AgentResult> { it.success }
                    .thenByDescending { result ->
                        val agent = agentsById[result.agentId]
                        agent?.capabilities?.intersect(task.requiredCapabilities)?.size ?: 0
                    },
            )
            .first()
    }
}

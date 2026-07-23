package com.jarvis.os.app.core.agents

import com.jarvis.os.app.data.model.AgentResult
import com.jarvis.os.app.data.model.AgentTask
import com.jarvis.os.app.data.model.AiCapability
import com.jarvis.os.app.data.repository.ApprovalRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 12 "Watch Tower Orchestration" (Phase 2): "JARVIS coordinates
 * all specialist agents... owner never manually routes work." This is
 * deliberately a thin layer over Sprint 11's existing
 * AgentRegistry/MultiAiCoordinator, not a new parallel execution path
 * -- "Preserve Sprint 1-11 architecture" (this sprint's own design
 * rule) means the approval-gating that already lives in
 * MultiAiCoordinator.coordinate (unconditional, every call, see that
 * class's docstring) is reused here exactly as-is, not re-decided.
 * requestConvene/convene below are named to mirror
 * JarvisCore.runTool's existing "ask first, run once approved" two-call
 * shape on purpose, for the same reason: one governance pattern in this
 * codebase, not a second one that could quietly drift from the first.
 *
 * HONESTY LIMITATION: see WatchTowerAgents.kt's docstring. Each
 * specialist's real output today is a routing confirmation, not an
 * actual finding -- [WatchTowerSummary.headline] and each
 * [AgentResult.output] reflect that honestly rather than inventing
 * "Batman found one issue" text with nothing real behind it.
 */
data class WatchTowerSummary(
    val topic: String,
    /** Non-null only while convening is still awaiting approval -- see requestConvene. */
    val approvalId: String?,
    val perSpecialist: List<AgentResult>,
    val headline: String,
)

@Singleton
class WatchTowerOrchestrator @Inject constructor(
    private val coordinator: MultiAiCoordinator,
    private val approvals: ApprovalRepository,
) {
    /**
     * "Determine required specialists" (Phase 2's own wording): a
     * deterministic keyword -> capability mapping, same "auditable
     * rule, not an LLM call" philosophy PlanningEngine and
     * JarvisDecisionEngine already use. An empty result is meaningful,
     * not an error: AgentRegistry.broadcast() already treats an empty
     * requiredCapabilities set as "every registered specialist is
     * eligible" (see that class's docstring) -- exactly the "convene
     * everyone" behavior a general, non-specific review topic should
     * get.
     */
    fun requiredCapabilitiesFor(topic: String): Set<AiCapability> {
        val lower = topic.lowercase()
        return buildSet {
            if (KEYWORDS_ARCHITECTURE.any { lower.contains(it) }) add(AiCapability.ARCHITECTURE_REVIEW)
            if (KEYWORDS_PERFORMANCE.any { lower.contains(it) }) add(AiCapability.PERFORMANCE_ANALYSIS)
            if (KEYWORDS_ENGINEERING.any { lower.contains(it) }) {
                add(AiCapability.CODE_GENERATION)
                add(AiCapability.TOOL_USE)
            }
            if (KEYWORDS_RISK.any { lower.contains(it) }) {
                add(AiCapability.REASONING)
                add(AiCapability.LONG_CONTEXT)
            }
            if (KEYWORDS_GOVERNANCE.any { lower.contains(it) }) add(AiCapability.REASONING)
            if (KEYWORDS_TESTING.any { lower.contains(it) }) add(AiCapability.TESTING)
            if (KEYWORDS_COORDINATION.any { lower.contains(it) }) add(AiCapability.COORDINATION)
            if (KEYWORDS_STRATEGY.any { lower.contains(it) }) add(AiCapability.STRATEGY)
        }
    }

    /**
     * Requests approval to convene Watch Tower on [topic] -- never runs
     * a specialist itself. Looks the created approval up by
     * relatedAgentTaskId rather than parsing MultiAiCoordinator's
     * human-readable denial message, so this stays correct even if that
     * message's wording ever changes.
     */
    suspend fun requestConvene(topic: String): WatchTowerSummary {
        val task = AgentTask(UUID.randomUUID().toString(), topic, requiredCapabilitiesFor(topic))
        coordinator.coordinate(task, approvalId = null)
        val approvalId = approvals.items.value.firstOrNull { it.relatedAgentTaskId == task.taskId }?.approvalId
        return WatchTowerSummary(
            topic = topic,
            approvalId = approvalId,
            perSpecialist = emptyList(),
            headline = "Convening Watch Tower on \"$topic\" needs your approval before any specialist runs.",
        )
    }

    /**
     * Runs Watch Tower on [topic] once [approvalId] has been approved.
     * Reuses the EXACT taskId requestConvene's approval was created
     * against (read back from the approval record itself) rather than
     * a fresh random one -- MultiAiCoordinator.coordinate rejects an
     * approval whose relatedAgentTaskId doesn't match the task passed
     * in (the same "this approval doesn't authorize this exact
     * invocation" check ToolRepository.execute applies via
     * relatedToolId), and a fresh UUID here would never match the id
     * requestConvene's approval was actually stamped with.
     */
    suspend fun convene(topic: String, approvalId: String): WatchTowerSummary {
        val approval = approvals.items.value.firstOrNull { it.approvalId == approvalId }
        val taskId = approval?.relatedAgentTaskId ?: UUID.randomUUID().toString()
        val task = AgentTask(taskId, topic, requiredCapabilitiesFor(topic))
        val results = coordinator.coordinate(task, approvalId)
        return WatchTowerSummary(
            topic = topic,
            approvalId = null,
            perSpecialist = results,
            headline = summarize(results),
        )
    }

    private fun summarize(results: List<AgentResult>): String {
        if (results.isEmpty()) return "No specialists were eligible for this topic."
        if (results.size == 1 && !results.first().success) return results.first().output
        val succeeded = results.count { it.success }
        return "$succeeded of ${results.size} specialist(s) responded."
    }

    companion object {
        private val KEYWORDS_ARCHITECTURE = setOf("architecture", "design", "structure")
        private val KEYWORDS_PERFORMANCE = setOf("performance", "speed", "regression", "slow")
        private val KEYWORDS_ENGINEERING = setOf("build", "implement", "engineering")
        private val KEYWORDS_RISK = setOf("risk", "edge case", "failure", "what if")
        private val KEYWORDS_GOVERNANCE = setOf("approve", "approval", "governance", "recommend")
        private val KEYWORDS_TESTING = setOf("test", "qa", "quality", "coverage")
        private val KEYWORDS_COORDINATION = setOf("coordinate", "team", "sync")
        private val KEYWORDS_STRATEGY = setOf("strategy", "plan", "priority", "roadmap")
    }
}

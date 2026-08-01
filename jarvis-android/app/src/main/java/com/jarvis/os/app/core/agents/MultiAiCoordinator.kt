package com.jarvis.os.app.core.agents

import com.jarvis.os.app.data.model.AgentResult
import com.jarvis.os.app.data.model.AgentTask
import com.jarvis.os.app.data.model.ApprovalKind
import com.jarvis.os.app.data.model.ApprovalOutcome
import com.jarvis.os.app.data.model.RiskLevel
import com.jarvis.os.app.data.repository.ApprovalRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 11 "Multi-AI Coordination": expertise-based routing across
 * every registered Agent (via AgentRegistry, which itself routes each
 * agent's actual work through AiRouter -- see BuiltInAgents.kt) plus
 * the sprint's explicit "human approval before execution" requirement,
 * gated through ApprovalRepository exactly as ToolRepository gates
 * MODERATE+ tools (see that class's docstring for the same
 * "ToolRepository/MultiAiCoordinator read Approvals directly rather
 * than routing a fourth thing through JarvisCore" tradeoff, applied
 * here identically).
 *
 * `coordinate` always requires approval, regardless of risk level --
 * unlike ToolRepository's LOW-risk fast path, Sprint 11's own wording
 * ("Human approval before execution") names this as unconditional for
 * multi-AI coordination, not risk-scaled like Tool Framework's gate.
 */
interface MultiAiCoordinator {
    /**
     * approvalId == null -> requests an AGENT_TASK approval and returns
     * null results with the created approval id embedded in a synthetic
     * AgentResult.output on a failed-style entry -- mirrors
     * ToolRepository.execute's "ask first" shape rather than throwing,
     * so a caller can inspect why nothing ran.
     */
    suspend fun coordinate(task: AgentTask, approvalId: String? = null): List<AgentResult>
}

@Singleton
class DefaultMultiAiCoordinator @Inject constructor(
    private val registry: AgentRegistry,
    private val approvals: ApprovalRepository,
) : MultiAiCoordinator {

    override suspend fun coordinate(task: AgentTask, approvalId: String?): List<AgentResult> {
        if (approvalId == null) {
            val approval = approvals.requestApproval(
                kind = ApprovalKind.AGENT_TASK,
                title = "Run agent task: ${task.goal}",
                reason = "Multi-AI coordination requires owner approval before execution.",
                riskLevel = RiskLevel.MODERATE,
                requestedBy = "system",
                relatedAgentTaskId = task.taskId,
            )
            return listOf(deniedResult(task, "Approval required -- requested as '${approval.approvalId}'. Re-run once approved."))
        }
        val approval = approvals.items.value.firstOrNull { it.approvalId == approvalId }
            ?: return listOf(deniedResult(task, "No approval found with id '$approvalId'"))
        if (approval.kind != ApprovalKind.AGENT_TASK || approval.relatedAgentTaskId != task.taskId) {
            return listOf(deniedResult(task, "Approval '$approvalId' does not authorize task '${task.taskId}'"))
        }
        if (approval.outcome != ApprovalOutcome.APPROVED) {
            return listOf(deniedResult(task, "Approval '$approvalId' is ${approval.outcome}, not APPROVED"))
        }
        return registry.broadcast(task)
    }

    private fun deniedResult(task: AgentTask, message: String) =
        AgentResult(task.taskId, agentId = "coordinator", success = false, output = message, completedAt = java.time.Instant.now())
}

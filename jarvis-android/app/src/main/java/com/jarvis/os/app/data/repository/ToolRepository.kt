package com.jarvis.os.app.data.repository

import com.jarvis.os.app.core.tools.Tool
import com.jarvis.os.app.core.tools.ToolResult
import com.jarvis.os.app.data.model.ApprovalKind
import com.jarvis.os.app.data.model.ApprovalOutcome
import com.jarvis.os.app.data.model.ToolDefinition
import com.jarvis.os.app.data.model.ToolExecutionRecord
import com.jarvis.os.app.data.model.ToolHealthStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 10 Tool Framework: registry + discovery + execution + health +
 * approval workflow in one repository, mirroring ConnectionRepository
 * and ApprovalRepository's own "mock data, real state machine" shape.
 *
 * Honesty note on the ApprovalRepository dependency below: every other
 * repository in this codebase stays ignorant of Approvals, with
 * JarvisCore as the sole cross-repository coordinator (see its class
 * docstring's "single coordinator" discussion). ToolRepository breaks
 * that pattern deliberately and narrowly -- it reads
 * ApprovalRepository.items (to check whether a given approvalId is an
 * APPROVED TOOL_EXECUTION for this tool) and calls requestApproval (to
 * create one when execute() is attempted without a valid approval).
 * The alternative -- routing every tool execution through JarvisCore
 * as a third coordination surface alongside Connections and Approvals
 * -- was judged to add a layer of indirection with no behavioral
 * difference for a framework whose entire job IS approval-gated
 * execution; JarvisCore still remains the coordinator for what happens
 * AFTER a tool runs (publishing a CoreEvent), see JarvisCore.runTool.
 */
interface ToolRepository {
    val tools: StateFlow<List<ToolDefinition>>
    val health: StateFlow<Map<String, ToolHealthStatus>>
    val executionLog: StateFlow<List<ToolExecutionRecord>>

    fun discover(): List<ToolDefinition> = tools.value
    fun checkHealth(toolId: String): ToolHealthStatus

    /**
     * LOW risk tools execute immediately. MODERATE+ risk tools require
     * a matching APPROVED TOOL_EXECUTION approval:
     * - approvalId == null -> creates a new PENDING approval and
     *   returns ToolResult.Failure describing it; caller must re-call
     *   execute() with that id once approved.
     * - approvalId set but not an APPROVED TOOL_EXECUTION for this
     *   exact toolId -> Failure, no approval is silently created.
     */
    suspend fun execute(toolId: String, input: String, approvalId: String? = null): ToolResult
}

@Singleton
class MockToolRepository @Inject constructor(
    tools: Set<@JvmSuppressWildcards Tool>,
    private val approvals: ApprovalRepository,
) : ToolRepository {

    private val toolsById: Map<String, Tool> = tools.associateBy { it.definition.toolId }

    override val tools: StateFlow<List<ToolDefinition>> =
        MutableStateFlow(toolsById.values.map { it.definition }).asStateFlow()

    private val _health = MutableStateFlow(toolsById.keys.associateWith { ToolHealthStatus.HEALTHY })
    override val health: StateFlow<Map<String, ToolHealthStatus>> = _health.asStateFlow()

    private val _executionLog = MutableStateFlow<List<ToolExecutionRecord>>(emptyList())
    override val executionLog: StateFlow<List<ToolExecutionRecord>> = _executionLog.asStateFlow()

    override fun checkHealth(toolId: String): ToolHealthStatus =
        _health.value[toolId] ?: ToolHealthStatus.UNAVAILABLE

    override suspend fun execute(toolId: String, input: String, approvalId: String?): ToolResult {
        val tool = toolsById[toolId] ?: return ToolResult.Failure("No tool registered with id '$toolId'")

        if (checkHealth(toolId) == ToolHealthStatus.UNAVAILABLE) {
            return ToolResult.Failure("Tool '$toolId' is currently unavailable")
        }

        if (tool.definition.requiresApproval) {
            val gate = checkApprovalGate(tool.definition, approvalId)
            if (gate != null) return gate
        }

        val result = try {
            tool.execute(input)
        } catch (e: Exception) {
            _health.update { it + (toolId to ToolHealthStatus.DEGRADED) }
            ToolResult.Failure("Tool threw: ${e.message}")
        }

        record(toolId, input, result, approvalId)
        return result
    }

    /** Returns null when execution may proceed; returns the Failure to short-circuit with otherwise. */
    private fun checkApprovalGate(definition: ToolDefinition, approvalId: String?): ToolResult? {
        if (approvalId == null) {
            val approval = approvals.requestApproval(
                kind = ApprovalKind.TOOL_EXECUTION,
                title = "Run tool: ${definition.name}",
                reason = "${definition.name} is risk tier ${definition.riskLevel} and requires owner approval before running.",
                riskLevel = definition.riskLevel,
                requestedBy = "system",
                relatedToolId = definition.toolId,
            )
            return ToolResult.Failure("Approval required -- requested as '${approval.approvalId}'. Re-run once approved.")
        }
        val approval = approvals.items.value.firstOrNull { it.approvalId == approvalId }
            ?: return ToolResult.Failure("No approval found with id '$approvalId'")
        if (approval.kind != ApprovalKind.TOOL_EXECUTION || approval.relatedToolId != definition.toolId) {
            return ToolResult.Failure("Approval '$approvalId' does not authorize tool '${definition.toolId}'")
        }
        if (approval.outcome != ApprovalOutcome.APPROVED) {
            return ToolResult.Failure("Approval '$approvalId' is ${approval.outcome}, not APPROVED")
        }
        return null
    }

    private fun record(toolId: String, input: String, result: ToolResult, approvalId: String?) {
        val (success, summary) = when (result) {
            is ToolResult.Success -> true to result.output.take(120)
            is ToolResult.Failure -> false to result.message
        }
        _executionLog.update {
            it + ToolExecutionRecord(UUID.randomUUID().toString(), toolId, input, summary, success, Instant.now(), approvalId)
        }
    }
}

package com.jarvis.os.app.core.monitoring

import com.jarvis.os.app.core.workflow.WorkflowEngine
import com.jarvis.os.app.data.model.ConnectionStatus
import com.jarvis.os.app.data.model.SystemHealthLevel
import com.jarvis.os.app.data.model.SystemHealthSnapshot
import com.jarvis.os.app.data.model.ToolHealthStatus
import com.jarvis.os.app.data.repository.ConnectionRepository
import com.jarvis.os.app.data.repository.ToolRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 11 "Production Readiness": deterministic, computed-on-demand
 * (not a background job -- see this class's docstring parallel to
 * PlanningEngine's "verify what you can actually run" reasoning)
 * rollup across the three subsystems that already expose their own
 * health signals -- ConnectionRepository (ERROR/SUSPENDED status),
 * ToolRepository (health map, Sprint 10), WorkflowEngine (recent run
 * failure rate). Thresholds are intentionally simple percentages, not
 * tuned against real production data this sandbox has no way to
 * generate -- a real deployment would replace `snapshot()`'s constants
 * with configured thresholds, not this class's shape.
 */
@Singleton
class SystemHealthMonitor @Inject constructor(
    private val connections: ConnectionRepository,
    private val tools: ToolRepository,
    private val workflows: WorkflowEngine,
) {
    fun snapshot(): SystemHealthSnapshot {
        val allConnections = connections.connections.value
        val healthyConnections = allConnections.count { it.status !in setOf(ConnectionStatus.ERROR, ConnectionStatus.SUSPENDED) }

        val allTools = tools.health.value
        val healthyTools = allTools.values.count { it == ToolHealthStatus.HEALTHY }

        val recentRuns = workflows.runs.value.filter { it.completedAt != null }.takeLast(20)
        val failureRate = if (recentRuns.isEmpty()) 0.0 else recentRuns.count { !it.succeeded }.toDouble() / recentRuns.size

        val reasons = mutableListOf<String>()
        if (allConnections.isNotEmpty() && healthyConnections < allConnections.size) reasons += "${allConnections.size - healthyConnections} connection(s) in ERROR/SUSPENDED"
        if (allTools.isNotEmpty() && healthyTools < allTools.size) reasons += "${allTools.size - healthyTools} tool(s) not HEALTHY"
        if (failureRate > 0.0) reasons += "Recent workflow failure rate: ${(failureRate * 100).toInt()}%"

        val level = when {
            failureRate >= 0.5 || (allConnections.isNotEmpty() && healthyConnections == 0) -> SystemHealthLevel.CRITICAL
            reasons.isNotEmpty() -> SystemHealthLevel.DEGRADED
            else -> SystemHealthLevel.HEALTHY
        }

        return SystemHealthSnapshot(level, healthyConnections, allConnections.size, healthyTools, allTools.size, failureRate, reasons)
    }
}

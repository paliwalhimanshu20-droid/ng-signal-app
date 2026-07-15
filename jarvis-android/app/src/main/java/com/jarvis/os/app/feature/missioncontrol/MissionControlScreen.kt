package com.jarvis.os.app.feature.missioncontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.os.app.core.JarvisCore
import com.jarvis.os.app.core.agents.AgentRegistry
import com.jarvis.os.app.core.chat.AiRouter
import com.jarvis.os.app.core.workflow.WorkflowEngine
import com.jarvis.os.app.data.model.AuditEntry
import com.jarvis.os.app.data.model.ConnectionStatus
import com.jarvis.os.app.data.model.ProgressDashboard
import com.jarvis.os.app.data.repository.NgSignalProStatus
import com.jarvis.os.app.data.repository.NgSignalProStatusProvider
import com.jarvis.os.app.designsystem.JarvisBrand
import com.jarvis.os.app.designsystem.JarvisSpacing
import com.jarvis.os.app.designsystem.JarvisStatusColors
import com.jarvis.os.app.designsystem.components.ExecutiveTimeline
import com.jarvis.os.app.designsystem.components.GlassPanel
import com.jarvis.os.app.designsystem.components.MissionControlTile
import com.jarvis.os.app.designsystem.jarvisHudLabelStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Sprint 13 "Mission Control": replaces Sprint 11's
 * ExecutiveDashboardScreen -- same underlying read-only view across
 * every subsystem (renamed here, not re-architected: still reads
 * through JarvisCore plus the two engines JarvisCore doesn't itself
 * expose, WorkflowEngine and AgentRegistry, matching the exact
 * precedent ExecutiveDashboardViewModel already set by injecting
 * WorkflowEngine directly alongside `core`), extended with the two
 * subsystems Sprint 13 explicitly names that Sprint 11's dashboard
 * didn't yet surface: AI Providers (AiRouter) and Watch Tower
 * (AgentRegistry, Sprint 12).
 *
 * "Everything displayed as live operational status. Not a settings
 * page. A command center." -- every tile below reads a real StateFlow;
 * nothing here is static placeholder text.
 */
data class MissionControlState(
    val activeProviderId: String = "",
    val totalProviders: Int = 0,
    val watchTowerAgentCount: Int = 0,
    val watchTowerLastActivity: String = "No specialists convened yet.",
    val connectedCount: Int = 0,
    val totalConnections: Int = 0,
    val projectDashboard: ProgressDashboard? = null,
    val ngSignalPro: NgSignalProStatus? = null,
    val memoryEntryCount: Int = 0,
    val unreadNotifications: Int = 0,
    val activeWorkflowCount: Int = 0,
    val recentAudit: List<AuditEntry> = emptyList(),
)

@HiltViewModel
class MissionControlViewModel @Inject constructor(
    private val core: JarvisCore,
    private val workflowEngine: WorkflowEngine,
    private val agentRegistry: AgentRegistry,
    private val aiRouter: AiRouter,
    private val ngSignalPro: NgSignalProStatusProvider,
) : ViewModel() {

    val state = combine(
        core.connections.connections,
        core.projects.dashboard,
        workflowEngine.runs,
        core.audit.entries,
        core.notifications.unreadCount,
    ) { connections, projectDashboard, workflowRuns, audit, unread ->
        FirstFive(connections, projectDashboard, workflowRuns, audit, unread)
    }.combine(core.memory.entries) { first, memoryEntries ->
        FirstSix(first, memoryEntries)
    }.combine(agentRegistry.results) { firstSix, agentResults ->
        FirstSeven(firstSix, agentResults)
    }.combine(aiRouter.activeProviderId) { firstSeven, activeProviderId ->
        FirstEight(firstSeven, activeProviderId)
    }.combine(ngSignalPro.status) { firstEight, ngStatus ->
        val first = firstEight.firstSeven.firstSix.first
        val agentResults = firstEight.firstSeven.agentResults
        val memoryEntries = firstEight.firstSeven.firstSix.memoryEntries
        MissionControlState(
            activeProviderId = firstEight.activeProviderId,
            totalProviders = aiRouter.available.size,
            watchTowerAgentCount = agentRegistry.agents.value.size,
            watchTowerLastActivity = agentResults.maxByOrNull { it.completedAt }?.let { latest ->
                val name = agentRegistry.agents.value.firstOrNull { it.agentId == latest.agentId }?.name ?: latest.agentId
                "$name: ${latest.output}"
            } ?: "No specialists convened yet.",
            connectedCount = first.connections.count { it.status == ConnectionStatus.CONNECTED },
            totalConnections = first.connections.size,
            projectDashboard = first.projectDashboard,
            ngSignalPro = ngStatus,
            memoryEntryCount = memoryEntries.size,
            unreadNotifications = first.unread,
            activeWorkflowCount = first.workflowRuns.count { it.completedAt == null },
            recentAudit = first.audit.takeLast(6).reversed(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MissionControlState())

    private data class FirstFive(
        val connections: List<com.jarvis.os.app.data.model.Connection>,
        val projectDashboard: ProgressDashboard,
        val workflowRuns: List<com.jarvis.os.app.data.model.WorkflowRunRecord>,
        val audit: List<AuditEntry>,
        val unread: Int,
    )

    private data class FirstSix(val first: FirstFive, val memoryEntries: List<com.jarvis.os.app.data.model.MemoryEntry>)
    private data class FirstSeven(val firstSix: FirstSix, val agentResults: List<com.jarvis.os.app.data.model.AgentResult>)
    private data class FirstEight(val firstSeven: FirstSeven, val activeProviderId: String)
}

@Composable
fun MissionControlScreen(viewModel: MissionControlViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(JarvisSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(JarvisSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(JarvisSpacing.sm),
    ) {
        item {
            MissionControlTile(
                label = "AI PROVIDERS",
                value = "${state.totalProviders} online",
                detail = "Active: ${state.activeProviderId.ifBlank { "none" }}",
                accentColor = JarvisBrand.CoreCyan,
            )
        }
        item {
            MissionControlTile(
                label = "WATCH TOWER",
                value = "${state.watchTowerAgentCount} specialists",
                detail = state.watchTowerLastActivity,
                accentColor = JarvisBrand.CorePlasma,
            )
        }
        item {
            val dashboard = state.projectDashboard
            MissionControlTile(
                label = "PROJECTOS",
                value = if (dashboard != null) "${dashboard.activeProjects}/${dashboard.totalProjects} active" else "—",
                detail = dashboard?.let { "${it.averageProgressPercent}% avg · ${it.openTaskCount} open tasks" } ?: "No projects tracked",
                accentColor = JarvisStatusColors.Healthy,
            )
        }
        item {
            val ng = state.ngSignalPro
            MissionControlTile(
                label = "NG SIGNAL PRO",
                value = if (ng?.lastUpdated != null) ng.marketBias else "No live connection",
                detail = if (ng?.lastUpdated != null) "${ng.buyCandidateCount} BUY candidate(s) · ${ng.confidencePercent}% confidence" else "Bridge not connected yet",
                accentColor = JarvisStatusColors.Unknown,
            )
        }
        item {
            MissionControlTile(
                label = "CONNECTED SYSTEMS",
                value = "${state.connectedCount}/${state.totalConnections}",
                detail = "Providers, tools, and services online",
                accentColor = JarvisBrand.CoreBlue,
            )
        }
        item {
            MissionControlTile(
                label = "MEMORY",
                value = "${state.memoryEntryCount} entries",
                detail = "Conversation, preference, and project memory",
                accentColor = JarvisStatusColors.Unknown,
            )
        }
        item {
            MissionControlTile(
                label = "HEALTH",
                value = if (state.activeWorkflowCount > 0) "${state.activeWorkflowCount} running" else "All clear",
                detail = "${state.unreadNotifications} unread notification(s)",
                accentColor = if (state.activeWorkflowCount > 0) JarvisStatusColors.Degraded else JarvisStatusColors.Healthy,
            )
        }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Column {
                    Text("TIMELINE", style = jarvisHudLabelStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(JarvisSpacing.md))
                    if (state.recentAudit.isEmpty()) {
                        Text("Nothing recorded yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        ExecutiveTimeline(entries = state.recentAudit)
                    }
                }
            }
        }
    }
}

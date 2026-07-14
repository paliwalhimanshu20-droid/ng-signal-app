package com.jarvis.os.app.feature.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.os.app.core.JarvisCore
import com.jarvis.os.app.data.model.AuditEntry
import com.jarvis.os.app.data.model.ConnectionStatus
import com.jarvis.os.app.data.model.ProgressDashboard
import com.jarvis.os.app.data.model.WorkflowRunRecord
import com.jarvis.os.app.designsystem.JarvisSpacing
import com.jarvis.os.app.designsystem.components.JarvisCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Sprint 11 "Executive Dashboard": one screen reading across every
 * subsystem this and prior sprints built -- system status (connection
 * health rollup), active projects (ProjectRepository.dashboard),
 * running workflows (WorkflowEngine.runs), connected services
 * (ConnectionRepository.connections), audit logs (AuditRepository.entries)
 * and notifications (NotificationRepository.unreadCount) -- via
 * JarvisCore rather than injecting six repositories directly, since
 * JarvisCore already exposes all of them as read-only properties (see
 * its class docstring).
 */
data class ExecutiveDashboardState(
    val connectedCount: Int = 0,
    val totalConnections: Int = 0,
    val projectDashboard: ProgressDashboard? = null,
    val activeWorkflowRuns: List<WorkflowRunRecord> = emptyList(),
    val recentAudit: List<AuditEntry> = emptyList(),
    val unreadNotifications: Int = 0,
)

@HiltViewModel
class ExecutiveDashboardViewModel @Inject constructor(
    private val core: JarvisCore,
    private val workflowEngine: com.jarvis.os.app.core.workflow.WorkflowEngine,
) : ViewModel() {

    val state = combine(
        core.connections.connections,
        core.projects.dashboard,
        workflowEngine.runs,
        core.audit.entries,
        core.notifications.unreadCount,
    ) { connections, projectDashboard, runs, audit, unread ->
        ExecutiveDashboardState(
            connectedCount = connections.count { it.status == ConnectionStatus.CONNECTED },
            totalConnections = connections.size,
            projectDashboard = projectDashboard,
            activeWorkflowRuns = runs.filter { it.completedAt == null },
            recentAudit = audit.takeLast(10).reversed(),
            unreadNotifications = unread,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExecutiveDashboardState())
}

@Composable
fun ExecutiveDashboardScreen(viewModel: ExecutiveDashboardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LazyColumn(contentPadding = PaddingValues(horizontal = JarvisSpacing.md, vertical = JarvisSpacing.sm)) {
        item {
            JarvisCard(modifier = Modifier.fillMaxWidth().padding(vertical = JarvisSpacing.xs)) {
                Column {
                    Text("System Status", style = MaterialTheme.typography.titleMedium)
                    Text("${state.connectedCount}/${state.totalConnections} connections active", style = MaterialTheme.typography.bodyMedium)
                    Text("${state.unreadNotifications} unread notifications", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        state.projectDashboard?.let { dashboard ->
            item {
                JarvisCard(modifier = Modifier.fillMaxWidth().padding(vertical = JarvisSpacing.xs)) {
                    Column {
                        Text("Projects", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${dashboard.activeProjects}/${dashboard.totalProjects} active · " +
                                "${dashboard.averageProgressPercent}% avg progress · " +
                                "${dashboard.openTaskCount} open tasks",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        item {
            JarvisCard(modifier = Modifier.fillMaxWidth().padding(vertical = JarvisSpacing.xs)) {
                Column {
                    Text("Running Workflows", style = MaterialTheme.typography.titleMedium)
                    if (state.activeWorkflowRuns.isEmpty()) {
                        Text("None currently running", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        state.activeWorkflowRuns.forEach { run ->
                            Text("${run.workflowId} -- ${run.stepStatuses.values.count { it.name == "SUCCEEDED" }}/${run.stepStatuses.size} steps done", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item { Text("Recent Audit", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = JarvisSpacing.sm)) }
        items(state.recentAudit, key = { it.entryId }) { entry ->
            JarvisCard(modifier = Modifier.fillMaxWidth().padding(vertical = JarvisSpacing.xs)) {
                Column {
                    Text("[${entry.category}] ${entry.summary}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

package com.jarvis.os.app.feature.watchtower

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.os.app.core.agents.AgentRegistry
import com.jarvis.os.app.data.model.AgentResult
import com.jarvis.os.app.designsystem.JarvisBrand
import com.jarvis.os.app.designsystem.JarvisSpacing
import com.jarvis.os.app.designsystem.components.GlassPanel
import com.jarvis.os.app.designsystem.jarvisHudLabelStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class WatchTowerAgentUiState(
    val agentId: String,
    val name: String,
    val roleTitle: String,
    val specialty: String,
    val statusLine: String,
    val recentResults: List<AgentResult>,
)

/**
 * Sprint 12 "Watch Tower Comes Alive": "the specialists should never
 * appear as a number only... every specialist has a live identity."
 * [roleTitleFor] is real, deliberate UI copy layered on top of real
 * data (AgentRegistry.agents/results, Sprint 12's WatchTowerOrchestrator) --
 * not fabricated confidence scores or invented conversation transcripts.
 * A specialist's [statusLine]/[recentResults] are exactly whatever
 * WatchTowerOrchestrator has actually run for them; a specialist that
 * has never been convened honestly says so rather than showing a
 * plausible-looking placeholder task.
 *
 * "Confidence," "evidence," and inter-agent "conversation" (sections 5
 * and 6 of this sprint's brief) are deliberately NOT included here --
 * no real confidence-scoring or agent-to-agent messaging data model
 * exists anywhere in this codebase (AgentResult is only
 * taskId/agentId/success/output/completedAt), and inventing numbers or
 * transcripts to fill those fields would be exactly the fabricated
 * intelligence this same sprint's brief explicitly forbids elsewhere.
 * See this sprint's integration report for the full reasoning.
 */
@HiltViewModel
class WatchTowerViewModel @Inject constructor(
    private val agentRegistry: AgentRegistry,
) : ViewModel() {

    val agents = agentRegistry.agents.combine(agentRegistry.results) { descriptors, results ->
        descriptors.map { descriptor ->
            val ownResults = results.filter { it.agentId == descriptor.agentId }.sortedByDescending { it.completedAt }
            WatchTowerAgentUiState(
                agentId = descriptor.agentId,
                name = descriptor.name,
                roleTitle = roleTitleFor(descriptor.name),
                specialty = descriptor.specialty,
                statusLine = ownResults.firstOrNull()?.let { "Last task: ${it.output}" } ?: "Not yet convened.",
                recentResults = ownResults,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun roleTitleFor(name: String): String = when (name) {
        "Batman" -> "Chief Architect"
        "Flash" -> "Performance Engineer"
        "Iron Man" -> "Systems Engineer"
        "Spider-Man" -> "Code Reviewer"
        "Captain America" -> "Quality Assurance"
        "Doctor Strange" -> "Trading Intelligence"
        "Professor X" -> "Documentation"
        "Nick Fury" -> "Mission Commander"
        "Research Agent" -> "Research Specialist"
        "Code Agent" -> "Code Specialist"
        else -> "Specialist"
    }
}

@Composable
fun WatchTowerScreen(viewModel: WatchTowerViewModel = hiltViewModel()) {
    val agents by viewModel.agents.collectAsState()
    var expandedAgentId by remember { mutableStateOf<String?>(null) }

    LazyColumn(contentPadding = PaddingValues(JarvisSpacing.md)) {
        items(agents, key = { it.agentId }) { agent ->
            GlassPanel(modifier = Modifier.fillMaxWidth().padding(bottom = JarvisSpacing.sm)) {
                WatchTowerAgentCard(
                    agent = agent,
                    expanded = expandedAgentId == agent.agentId,
                    onToggle = { expandedAgentId = if (expandedAgentId == agent.agentId) null else agent.agentId },
                )
            }
        }
    }
}

@Composable
private fun WatchTowerAgentCard(agent: WatchTowerAgentUiState, expanded: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(agent.name, style = MaterialTheme.typography.titleMedium)
                Text(agent.roleTitle, style = jarvisHudLabelStyle(), color = JarvisBrand.CoreCyan)
            }
        }
        Text(
            agent.statusLine,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = JarvisSpacing.sm),
        )
        if (expanded && agent.recentResults.isNotEmpty()) {
            val formatter = remember { DateTimeFormatter.ofPattern("MMM d, HH:mm") }
            Column(modifier = Modifier.padding(top = JarvisSpacing.sm)) {
                Text("HISTORY", style = jarvisHudLabelStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                agent.recentResults.take(10).forEach { result ->
                    Column(modifier = Modifier.padding(top = JarvisSpacing.xs)) {
                        Text(
                            formatter.format(result.completedAt.atZone(ZoneId.systemDefault())),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(result.output, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

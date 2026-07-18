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
import com.jarvis.os.app.core.chat.AnthropicChatProvider
import com.jarvis.os.app.core.chat.GeminiChatProvider
import com.jarvis.os.app.core.chat.OpenAiCompatibleChatProvider
import com.jarvis.os.app.core.chat.ProviderConnectionState
import com.jarvis.os.app.core.workflow.WorkflowEngine
import com.jarvis.os.app.data.model.AuditEntry
import com.jarvis.os.app.data.model.ConnectionStatus
import com.jarvis.os.app.data.model.ProgressDashboard
import com.jarvis.os.app.data.repository.GitHubFetchResult
import com.jarvis.os.app.data.repository.GitHubStatusProvider
import com.jarvis.os.app.data.repository.NgSignalProStatus
import com.jarvis.os.app.data.repository.NgSignalProStatusProvider
import com.jarvis.os.app.data.settings.AnthropicKeyStore
import com.jarvis.os.app.data.settings.ApiKeyStore
import com.jarvis.os.app.data.settings.GeminiKeyStore
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
import kotlinx.coroutines.launch
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
 *
 * "Universal Connection Ecosystem -- Phase 1": GitHubStatusProvider and
 * NgSignalProStatusProvider are now real network-backed classes (see
 * their own docstrings), not mocks -- neither fetches anything until
 * [refresh] is actually called, which the init block below does once
 * when this ViewModel is created. Both fail honestly (an explicit
 * Failure/summary string) rather than showing stale or fabricated data
 * if the Owner hasn't configured a GitHub token yet.
 */
data class MissionControlState(
    val activeProviderName: String = "",
    // "Fix Mission Control -- Critical": "Mission Control must always
    // display the real ProviderConnectionState... No custom wording."
    // Replaces the old isOnline: Boolean (which produced the exact
    // contradiction reported -- "JARVIS OFFLINE" next to "OpenAI is
    // active and ready," two independently-computed pieces of text
    // that could disagree). This is the same enum SettingsViewModel
    // computes for the AI Provider screen -- one shared source, read
    // here, not reinterpreted.
    val providerConnectionState: ProviderConnectionState = ProviderConnectionState.NOT_CONFIGURED,
    val totalProviders: Int = 0,
    val watchTowerAgentCount: Int = 0,
    val watchTowerLastActivity: String = "No specialists convened yet.",
    val connectedCount: Int = 0,
    val totalConnections: Int = 0,
    val projectDashboard: ProgressDashboard? = null,
    val ngSignalPro: NgSignalProStatus? = null,
    val github: GitHubFetchResult? = null,
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
    private val gitHub: GitHubStatusProvider,
    private val apiKeyStore: ApiKeyStore,
    private val geminiKeyStore: GeminiKeyStore,
    private val anthropicKeyStore: AnthropicKeyStore,
    private val geminiChatProvider: GeminiChatProvider,
    private val openAiChatProvider: OpenAiCompatibleChatProvider,
    private val anthropicChatProvider: AnthropicChatProvider,
    private val groqKeyStore: com.jarvis.os.app.data.settings.GroqKeyStore,
    private val groqChatProvider: com.jarvis.os.app.core.chat.GroqChatProvider,
) : ViewModel() {

    init {
        viewModelScope.launch { ngSignalPro.refresh() }
        viewModelScope.launch { gitHub.refresh() }
    }

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
        FirstNine(firstEight, ngStatus)
    }.combine(gitHub.status) { firstNine, githubStatus ->
        FirstTen(firstNine, githubStatus)
    }.combine(
        combine(
            geminiChatProvider.lastOutcome,
            openAiChatProvider.lastOutcome,
            anthropicChatProvider.lastOutcome,
            groqChatProvider.lastOutcome,
        ) { geminiOutcome, openAiOutcome, anthropicOutcome, groqOutcome ->
            FourOutcomes(geminiOutcome, openAiOutcome, anthropicOutcome, groqOutcome)
        },
    ) { firstTen, outcomes ->
        val firstNine = firstTen.firstNine
        val githubStatus = firstTen.githubStatus
        val first = firstNine.firstEight.firstSeven.firstSix.first
        val agentResults = firstNine.firstEight.firstSeven.agentResults
        val memoryEntries = firstNine.firstEight.firstSeven.firstSix.memoryEntries
        val activeProviderId = firstNine.firstEight.activeProviderId
        // "JARVIS Experience Transformation" (Phase 0): the Owner never
        // sees a raw provider id like "openai-compatible" -- displayName
        // is what a real conversational partner's name actually is.
        val activeProviderName = aiRouter.available
            .firstOrNull { it.id == activeProviderId }
            ?.displayName
            ?: activeProviderId

        // "Fix Mission Control -- Critical": the exact same
        // ProviderConnectionState.compute() SettingsViewModel uses,
        // fed the exact same three inputs (hasStoredKey, lastSuccessAt,
        // live AttemptOutcome) for whichever provider is currently
        // active. One shared function, one shared truth -- Mission
        // Control cannot say something different from Settings anymore
        // because they're not two interpretations, they're the same
        // calculation.
        val providerConnectionState = when (activeProviderId) {
            "openai-compatible" -> ProviderConnectionState.compute(apiKeyStore.currentConfig() != null, apiKeyStore.currentConfig()?.lastSuccessAt, outcomes.openAi)
            "gemini" -> ProviderConnectionState.compute(geminiKeyStore.currentConfig() != null, geminiKeyStore.currentConfig()?.lastSuccessAt, outcomes.gemini)
            "anthropic" -> ProviderConnectionState.compute(anthropicKeyStore.currentConfig() != null, anthropicKeyStore.currentConfig()?.lastSuccessAt, outcomes.anthropic)
            "groq" -> ProviderConnectionState.compute(groqKeyStore.currentConfig() != null, groqKeyStore.currentConfig()?.lastSuccessAt, outcomes.groq)
            else -> ProviderConnectionState.OFFLINE
        }

        MissionControlState(
            activeProviderName = activeProviderName,
            providerConnectionState = providerConnectionState,
            totalProviders = aiRouter.available.size,
            watchTowerAgentCount = agentRegistry.agents.value.size,
            watchTowerLastActivity = agentResults.maxByOrNull { it.completedAt }?.let { latest ->
                val name = agentRegistry.agents.value.firstOrNull { it.agentId == latest.agentId }?.name ?: latest.agentId
                "$name: ${latest.output}"
            } ?: "No specialists convened yet.",
            connectedCount = first.connections.count { it.status == ConnectionStatus.CONNECTED },
            totalConnections = first.connections.size,
            projectDashboard = first.projectDashboard,
            ngSignalPro = firstNine.ngStatus,
            github = githubStatus,
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
    private data class FirstNine(val firstEight: FirstEight, val ngStatus: NgSignalProStatus)
    private data class FirstTen(val firstNine: FirstNine, val githubStatus: GitHubFetchResult?)

    /** Kotlin has no built-in 4-tuple -- named fields here instead of a Quadruple, so the state-computation block below reads clearly by name rather than .first/.second/.third/.fourth. */
    private data class FourOutcomes(
        val gemini: ProviderConnectionState.AttemptOutcome?,
        val openAi: ProviderConnectionState.AttemptOutcome?,
        val anthropic: ProviderConnectionState.AttemptOutcome?,
        val groq: ProviderConnectionState.AttemptOutcome?,
    )
}

@Composable
fun MissionControlScreen(navController: androidx.navigation.NavHostController, viewModel: MissionControlViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(JarvisSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(JarvisSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(JarvisSpacing.sm),
    ) {
        item {
            // "Fix Mission Control -- Critical": "Mission Control must
            // always display the real ProviderConnectionState... No
            // custom wording." label is now literally
            // state.providerConnectionState.label -- the exact same
            // text ProviderConnectionState's own docstring defines and
            // AIProviderScreen shows -- not a second, independently
            // worded description that could ever disagree with it
            // again.
            val pcs = state.providerConnectionState
            MissionControlTile(
                label = pcs.label.uppercase(),
                value = if (state.activeProviderName.isNotBlank()) state.activeProviderName else "No provider selected yet.",
                detail = "${state.totalProviders} provider(s) available",
                accentColor = when (pcs) {
                    com.jarvis.os.app.core.chat.ProviderConnectionState.VERIFIED, com.jarvis.os.app.core.chat.ProviderConnectionState.CONNECTED -> JarvisStatusColors.Healthy
                    com.jarvis.os.app.core.chat.ProviderConnectionState.RATE_LIMITED -> JarvisStatusColors.Degraded
                    com.jarvis.os.app.core.chat.ProviderConnectionState.ERROR -> JarvisStatusColors.Unhealthy
                    else -> JarvisBrand.CoreCyan
                },
                onClick = { navController.navigate(com.jarvis.os.app.navigation.JarvisDestination.Settings.route) },
            )
        }
        item {
            MissionControlTile(
                label = "WATCH TOWER",
                value = if (state.watchTowerAgentCount > 0 && state.watchTowerLastActivity == "No specialists convened yet.") {
                    "${state.watchTowerAgentCount} specialists are standing by."
                } else {
                    state.watchTowerLastActivity
                },
                detail = "Tap to see the full team",
                accentColor = JarvisBrand.CorePlasma,
                onClick = { navController.navigate(com.jarvis.os.app.navigation.JarvisDestination.WatchTower.route) },
            )
        }
        item {
            val dashboard = state.projectDashboard
            MissionControlTile(
                label = "PROJECTOS",
                value = if (dashboard != null && dashboard.totalProjects > 0) {
                    "${dashboard.activeProjects} of ${dashboard.totalProjects} mission(s) in progress."
                } else {
                    "No missions tracked yet."
                },
                detail = dashboard?.let { "${it.averageProgressPercent}% average progress · ${it.openTaskCount} open task(s)" } ?: "",
                accentColor = JarvisStatusColors.Healthy,
            )
        }
        item {
            val ng = state.ngSignalPro
            MissionControlTile(
                label = "NG SIGNAL PRO",
                value = if (ng?.lastUpdated != null) ng.scannerStatusSummary else "Not connected yet.",
                detail = if (ng?.lastUpdated != null) {
                    "Warehouse: ${if (ng.warehouseSynchronized) "synced" else "out of sync"} · Alerts: ${if (ng.alertPipelineHealthy) "healthy" else "check needed"}"
                } else {
                    "Waiting to be connected"
                },
                accentColor = JarvisStatusColors.Unknown,
            )
        }
        item {
            val github = state.github
            MissionControlTile(
                label = "GITHUB",
                value = when (github) {
                    is com.jarvis.os.app.data.repository.GitHubFetchResult.Success ->
                        if (github.status.openPullRequestCount > 0) {
                            "${github.status.openPullRequestCount} pull request(s) awaiting review."
                        } else {
                            "No pull requests waiting."
                        }
                    is com.jarvis.os.app.data.repository.GitHubFetchResult.Failure -> "Not connected yet."
                    null -> "Checking…"
                },
                detail = when (github) {
                    is com.jarvis.os.app.data.repository.GitHubFetchResult.Success ->
                        "${github.status.repoFullName} · ${github.status.openIssueCount} open issue(s)"
                    is com.jarvis.os.app.data.repository.GitHubFetchResult.Failure -> github.message
                    null -> ""
                },
                accentColor = JarvisBrand.CoreBlue,
            )
        }
        item {
            MissionControlTile(
                label = "CONNECTED SYSTEMS",
                value = "${state.connectedCount} of ${state.totalConnections} systems online.",
                detail = "Providers, tools, and services",
                accentColor = JarvisBrand.CoreBlue,
            )
        }
        item {
            MissionControlTile(
                label = "MEMORY",
                value = "I'm holding on to ${state.memoryEntryCount} memories from our conversations.",
                detail = "Conversation, preference, and project memory",
                accentColor = JarvisStatusColors.Unknown,
            )
        }
        item {
            MissionControlTile(
                label = "HEALTH",
                value = if (state.activeWorkflowCount > 0) "${state.activeWorkflowCount} task(s) currently in progress." else "Everything running smoothly.",
                detail = if (state.unreadNotifications > 0) "${state.unreadNotifications} unread notification(s)" else "No open notifications",
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

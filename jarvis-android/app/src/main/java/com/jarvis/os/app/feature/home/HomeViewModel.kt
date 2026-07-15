package com.jarvis.os.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.os.app.core.JarvisCore
import com.jarvis.os.app.data.model.ApprovalOutcome
import com.jarvis.os.app.data.model.AuditEntry
import com.jarvis.os.app.designsystem.components.JarvisAvatarState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

data class HomeUiState(
    val greeting: String = "Good day.",
    val briefingLines: List<String> = emptyList(),
    val avatarState: JarvisAvatarState = JarvisAvatarState.Idle,
    val recentTimeline: List<AuditEntry> = emptyList(),
    val pendingApprovalCount: Int = 0,
)

/**
 * Sprint 13 "Home Screen": the previous HomeViewModel drove a
 * customizable reorderable card grid (DashboardLayout,
 * moveCard/setCardVisible) -- Sprint 13's brief ("the owner should
 * never feel they are opening another dashboard") replaces that
 * composition entirely with the fixed Avatar / Executive Briefing /
 * Conversation Entry layout below. DashboardLayout and
 * SettingsRepository.dashboardLayout are untouched and still work
 * (confirmed nothing else in the app references DashboardCardId/DashboardLayout
 * before this change) -- simply no longer read by Home, available
 * intact for a future customization surface if one is ever wanted.
 *
 * Injects JarvisCore directly (same pattern ExecutiveDashboardViewModel
 * and ChatViewModel already established), both to read
 * core.briefingEngine (Sprint 12) and to send a message from Home's own
 * conversation entry via core.sendChatMessage -- the same single
 * coordination point every other send path in this app already goes
 * through, not a second one.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val core: JarvisCore,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        core.projects.projects,
        core.approvals.items,
        core.notifications.unreadCount,
        core.connections.connections,
        core.audit.entries,
    ) { _, approvals, _, _, audit ->
        HomeUiState(
            greeting = greetingForNow(),
            briefingLines = core.briefingEngine.generateMorningBriefing().lines,
            avatarState = JarvisAvatarState.Idle,
            recentTimeline = audit.takeLast(5).reversed(),
            pendingApprovalCount = approvals.count { it.outcome == ApprovalOutcome.PENDING },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /**
     * Home's own conversation entry -- sends through the exact same
     * JarvisCore.sendChatMessage every other entry point uses (Phase 1's
     * context/decision engine, Phase 2's orchestration routing, and
     * Phase 3's briefing routing all apply here identically, since this
     * is the same call, not a parallel one). The caller (HomeScreen) is
     * responsible for navigating to Chat afterward so the owner sees the
     * reply -- Home itself doesn't render the conversation transcript.
     */
    fun send(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { core.sendChatMessage(text) }
    }
}

private fun greetingForNow(): String {
    val hour = LocalTime.now().hour
    return when {
        hour < 12 -> "Good Morning."
        hour < 17 -> "Good Afternoon."
        else -> "Good Evening."
    }
}

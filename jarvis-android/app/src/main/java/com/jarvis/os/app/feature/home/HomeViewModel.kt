package com.jarvis.os.app.feature.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.os.app.data.model.ApprovalOutcome
import com.jarvis.os.app.data.model.ConnectionStatus
import com.jarvis.os.app.data.repository.ApprovalRepository
import com.jarvis.os.app.data.repository.ConnectionRepository
import com.jarvis.os.app.data.repository.ProjectRepository
import com.jarvis.os.app.data.settings.DashboardCardId
import com.jarvis.os.app.data.settings.DashboardLayout
import com.jarvis.os.app.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// TEMPORARY DEBUG INSTRUMENTATION (Sprint 7.2 pipeline trace) — remove
// in the final cleanup commit once the root cause is confirmed.
private const val TRACE_TAG = "JARVIS-TRACE"

data class HomeUiState(
    val layout: DashboardLayout = DashboardLayout.default(),
    val connectedCount: Int = 0,
    val pendingApprovalCount: Int = 0,
    val activeProjectCount: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    connectionRepository: ConnectionRepository,
    approvalRepository: ApprovalRepository,
    projectRepository: ProjectRepository,
) : ViewModel() {

    // DEBUG: every stage below emits here; HomeScreen collects and
    // renders it as an on-screen panel (no logcat/adb required).
    private val _trace = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val trace: SharedFlow<String> = _trace

    private fun trace(line: String) {
        Log.d(TRACE_TAG, line)
        _trace.tryEmit(line)
    }

    val uiState = combine(
        settingsRepository.dashboardLayout,
        connectionRepository.connections,
        approvalRepository.items,
        projectRepository.projects,
    ) { layout, connections, approvals, projects ->
        // STEP 5/6: this fires every time settingsRepository.dashboardLayout
        // emits a new value AND every time it recombines with the other
        // three flows. Logs the full per-card visibility snapshot exactly
        // as it exists at HomeUiState creation time.
        trace("STEP5/6 combine() layout=${layout.cards.joinToString { "${it.id.name}:${it.visible}" }}")
        HomeUiState(
            layout = layout,
            connectedCount = connections.count { it.status == ConnectionStatus.CONNECTED },
            pendingApprovalCount = approvals.count { it.outcome == ApprovalOutcome.WAITING },
            activeProjectCount = projects.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Acceptance Scenario 5: reorder must persist immediately, not on some later "save" action — the Owner should never be able to lose a rearrangement by navigating away before an explicit save. */
    fun moveCard(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val current = uiState.value.layout
            settingsRepository.setDashboardLayout(current.moved(fromIndex, toIndex))
        }
    }

    fun setCardVisible(id: DashboardCardId, visible: Boolean) {
        // STEP 2: what the ViewModel actually received from the Switch.
        trace("STEP2 setCardVisible received id=${id.name} visible=$visible")
        viewModelScope.launch {
            val current = uiState.value.layout
            val currentVisibleForId = current.cards.firstOrNull { it.id == id }?.visible
            trace("STEP2b uiState.value.layout BEFORE write: ${id.name}=$currentVisibleForId (full snapshot: ${current.cards.joinToString { "${it.id.name}:${it.visible}" }})")
            val newLayout = current.withVisibility(id, visible)
            val newVisibleForId = newLayout.cards.firstOrNull { it.id == id }?.visible
            trace("STEP2c withVisibility() computed: ${id.name}=$newVisibleForId")
            settingsRepository.setDashboardLayout(newLayout)
            trace("STEP2d setDashboardLayout() call returned (write suspend fun completed)")
        }
    }
}

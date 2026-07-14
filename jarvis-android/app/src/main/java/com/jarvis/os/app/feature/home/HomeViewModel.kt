package com.jarvis.os.app.feature.home

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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    val uiState = combine(
        settingsRepository.dashboardLayout,
        connectionRepository.connections,
        approvalRepository.items,
        projectRepository.projects,
    ) { layout, connections, approvals, projects ->
        HomeUiState(
            layout = layout,
            connectedCount = connections.count { it.status == ConnectionStatus.CONNECTED },
            pendingApprovalCount = approvals.count { it.outcome == ApprovalOutcome.PENDING },
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
        viewModelScope.launch {
            val current = uiState.value.layout
            settingsRepository.setDashboardLayout(current.withVisibility(id, visible))
        }
    }
}

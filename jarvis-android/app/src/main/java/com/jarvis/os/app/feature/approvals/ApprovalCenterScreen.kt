package com.jarvis.os.app.feature.approvals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.os.app.data.model.ApprovalItem
import com.jarvis.os.app.data.model.ApprovalOutcome
import com.jarvis.os.app.data.model.RiskLevel
import com.jarvis.os.app.data.repository.ApprovalRepository
import com.jarvis.os.app.designsystem.JarvisSpacing
import com.jarvis.os.app.designsystem.JarvisStatusColors
import com.jarvis.os.app.designsystem.components.JarvisCard
import com.jarvis.os.app.designsystem.components.StatusPill
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ApprovalCenterViewModel @Inject constructor(
    private val repository: ApprovalRepository,
) : ViewModel() {
    val items = repository.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun approve(id: String) = viewModelScope.launch { repository.approve(id, approvedBy = "owner") }
    fun reject(id: String) = viewModelScope.launch { repository.reject(id, approvedBy = "owner") }
}

@Composable
fun ApprovalCenterScreen(viewModel: ApprovalCenterViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsState()
    val waiting = items.filter { it.outcome == ApprovalOutcome.WAITING }
    val history = items.filter { it.outcome != ApprovalOutcome.WAITING }

    LazyColumn(contentPadding = PaddingValues(horizontal = JarvisSpacing.md, vertical = JarvisSpacing.sm)) {
        item {
            Text(
                "Pending (${waiting.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = JarvisSpacing.sm),
            )
        }
        items(waiting, key = { it.approvalId }) { approval ->
            ApprovalCard(approval, onApprove = { viewModel.approve(approval.approvalId) }, onReject = { viewModel.reject(approval.approvalId) })
        }
        item {
            Text(
                "History (${history.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = JarvisSpacing.lg, bottom = JarvisSpacing.sm),
            )
        }
        items(history, key = { it.approvalId }) { approval ->
            ApprovalCard(approval, onApprove = null, onReject = null)
        }
    }
}

@Composable
private fun ApprovalCard(approval: ApprovalItem, onApprove: (() -> Unit)?, onReject: (() -> Unit)?) {
    JarvisCard(modifier = Modifier.fillMaxWidth().padding(vertical = JarvisSpacing.xs)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(approval.title, style = MaterialTheme.typography.titleMedium)
                StatusPill(approval.riskLevel.name, approval.riskLevel.toColor())
            }
            Text(approval.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${approval.kind.name.lowercase().replace('_', ' ')} · ${approval.outcome.name.lowercase()}" +
                    (approval.resolvedBy?.let { " by $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onApprove != null && onReject != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = JarvisSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(JarvisSpacing.xs),
                ) {
                    Button(onClick = onApprove) { Text("Approve") }
                    OutlinedButton(
                        onClick = onReject,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisStatusColors.Unhealthy),
                    ) { Text("Reject") }
                }
            }
        }
    }
}

private fun RiskLevel.toColor() = when (this) {
    RiskLevel.LOW -> JarvisStatusColors.Healthy
    RiskLevel.MODERATE -> JarvisStatusColors.Degraded
    RiskLevel.HIGH, RiskLevel.CRITICAL -> JarvisStatusColors.Unhealthy
}

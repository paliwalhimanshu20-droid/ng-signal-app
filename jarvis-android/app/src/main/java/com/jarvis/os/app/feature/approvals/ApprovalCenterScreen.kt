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
import com.jarvis.os.app.core.JarvisCore
import com.jarvis.os.app.data.model.ApprovalAuditRecord
import com.jarvis.os.app.data.model.ApprovalItem
import com.jarvis.os.app.data.model.ApprovalOutcome
import com.jarvis.os.app.data.model.RiskLevel
import com.jarvis.os.app.data.repository.ApprovalOperationError
import com.jarvis.os.app.designsystem.JarvisSpacing
import com.jarvis.os.app.designsystem.JarvisStatusColors
import com.jarvis.os.app.designsystem.components.JarvisCard
import com.jarvis.os.app.designsystem.components.StatusPill
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Sprint 9 Final: routes every action through JarvisCore instead of
 * ApprovalRepository directly -- the same coordination pattern PR1
 * established for Connections and PR2 for Notifications (see
 * ConnectionsViewModel / NotificationsViewModel). This is also this
 * screen's first-ever ViewModel to do so; before this PR it called
 * ApprovalRepository directly (a pre-Sprint-9 gap this PR closes, not
 * a regression).
 */
@HiltViewModel
class ApprovalCenterViewModel @Inject constructor(
    private val core: JarvisCore,
) : ViewModel() {
    val items = core.approvals.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val auditLog = core.approvals.auditLog.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _errors = MutableSharedFlow<String>()
    val errors: SharedFlow<String> = _errors

    private fun runGuarded(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: ApprovalOperationError) {
                _errors.emit(e.message ?: "That action is no longer valid for this approval's current state.")
            }
        }
    }

    fun approve(id: String) = runGuarded { core.approveApproval(id) }
    fun reject(id: String) = runGuarded { core.rejectApproval(id) }
    fun cancel(id: String) = runGuarded { core.cancelApproval(id) }
    fun expire(id: String) = runGuarded { core.expireApproval(id, actor = "owner") }
    fun revoke(id: String) = runGuarded { core.revokeApproval(id) }
}

/**
 * Sprint 9 Final: adds Active (approved, revocable) and Audit Trail
 * sections to the Sprint-8 Pending/History split -- additive, not a
 * redesign, per this sprint's own "do not redesign the UI unless
 * required" rule; the immutable audit trail IS required (spec: "History
 * is immutable" / "History entries must never disappear"), and there
 * was no way to show that without a section that renders auditLog
 * directly rather than deriving it from `items`' current state.
 */
@Composable
fun ApprovalCenterScreen(viewModel: ApprovalCenterViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsState()
    val auditLog by viewModel.auditLog.collectAsState()
    val pending = items.filter { it.outcome == ApprovalOutcome.PENDING }
    val active = items.filter { it.outcome == ApprovalOutcome.APPROVED }
    val history = items.filter { it.outcome !in setOf(ApprovalOutcome.PENDING, ApprovalOutcome.APPROVED) }

    LazyColumn(contentPadding = PaddingValues(horizontal = JarvisSpacing.md, vertical = JarvisSpacing.sm)) {
        item { SectionHeader("Pending (${pending.size})", topPadding = false) }
        items(pending, key = { it.approvalId }) { approval ->
            ApprovalCard(approval) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = JarvisSpacing.sm), horizontalArrangement = Arrangement.spacedBy(JarvisSpacing.xs)) {
                    Button(onClick = { viewModel.approve(approval.approvalId) }) { Text("Approve") }
                    OutlinedButton(onClick = { viewModel.reject(approval.approvalId) }, colors = dangerButtonColors()) { Text("Reject") }
                    OutlinedButton(onClick = { viewModel.cancel(approval.approvalId) }) { Text("Cancel") }
                    OutlinedButton(onClick = { viewModel.expire(approval.approvalId) }, colors = dangerButtonColors()) { Text("Expire") }
                }
            }
        }
        item { SectionHeader("Active (${active.size})") }
        items(active, key = { it.approvalId }) { approval ->
            ApprovalCard(approval) {
                OutlinedButton(
                    onClick = { viewModel.revoke(approval.approvalId) },
                    colors = dangerButtonColors(),
                    modifier = Modifier.padding(top = JarvisSpacing.sm),
                ) { Text("Revoke") }
            }
        }
        item { SectionHeader("History (${history.size})") }
        items(history, key = { it.approvalId }) { approval ->
            ApprovalCard(approval) {
                if (approval.outcome == ApprovalOutcome.REVOKED) {
                    Button(onClick = { viewModel.approve(approval.approvalId) }, modifier = Modifier.padding(top = JarvisSpacing.sm)) {
                        Text("Approve again")
                    }
                }
            }
        }
        item { SectionHeader("Audit trail (${auditLog.size})") }
        if (auditLog.isEmpty()) {
            item {
                Text(
                    "No audit records yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = JarvisSpacing.sm),
                )
            }
        }
        items(auditLog.sortedByDescending { it.timestamp }, key = { it.recordId }) { record ->
            AuditRow(record, approvalTitle = items.firstOrNull { it.approvalId == record.approvalId }?.title ?: record.approvalId)
        }
    }
}

@Composable
private fun SectionHeader(text: String, topPadding: Boolean = true) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = if (topPadding) JarvisSpacing.lg else JarvisSpacing.sm, bottom = JarvisSpacing.sm),
    )
}

@Composable
private fun dangerButtonColors() = ButtonDefaults.outlinedButtonColors(contentColor = JarvisStatusColors.Unhealthy)

@Composable
private fun ApprovalCard(approval: ApprovalItem, actions: @Composable () -> Unit) {
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
            actions()
        }
    }
}

@Composable
private fun AuditRow(record: ApprovalAuditRecord, approvalTitle: String) {
    JarvisCard(modifier = Modifier.fillMaxWidth().padding(vertical = JarvisSpacing.xs)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(approvalTitle, style = MaterialTheme.typography.titleSmall)
                Text(
                    record.timestamp.atZone(ZoneId.systemDefault()).format(auditTimeFormatter),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                (record.previousState?.let { "${it.name.lowercase()} → " } ?: "") + record.newState.name.lowercase() + " · by ${record.actor}",
                style = MaterialTheme.typography.bodySmall,
            )
            record.reason?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val auditTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")

private fun RiskLevel.toColor() = when (this) {
    RiskLevel.LOW -> JarvisStatusColors.Healthy
    RiskLevel.MODERATE -> JarvisStatusColors.Degraded
    RiskLevel.HIGH, RiskLevel.CRITICAL -> JarvisStatusColors.Unhealthy
}

package com.jarvis.os.app.feature.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.jarvis.os.app.data.model.ApprovalAuditRecord
import com.jarvis.os.app.data.model.Connection
import com.jarvis.os.app.data.model.ConnectionHealth
import com.jarvis.os.app.data.model.ConnectionStatus
import com.jarvis.os.app.designsystem.JarvisSpacing
import com.jarvis.os.app.designsystem.JarvisStatusColors
import com.jarvis.os.app.designsystem.components.JarvisCard
import com.jarvis.os.app.designsystem.components.StatusPill
import kotlinx.coroutines.launch

@Composable
fun ConnectionsScreen(viewModel: ConnectionsViewModel = hiltViewModel()) {
    val connections by viewModel.connections.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var auditDialogFor by remember { mutableStateOf<Connection?>(null) }

    // Sprint-7.1: governance failures (e.g. a stale button firing after
    // status already changed) surface here instead of crashing or
    // vanishing silently — see ConnectionsViewModel.errors.
    LaunchedEffect(Unit) {
        viewModel.errors.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(JarvisSpacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${connections.size} connection(s)", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { viewModel.disableAll() }) { Text("Disable All") }
            }

            LazyColumn(contentPadding = PaddingValues(horizontal = JarvisSpacing.md, vertical = JarvisSpacing.sm)) {
                items(connections, key = { it.connectionId }) { connection ->
                    ConnectionCard(
                        connection = connection,
                        onApprove = { viewModel.approve(connection.connectionId) },
                        onReject = { viewModel.reject(connection.connectionId) },
                        onConnect = { viewModel.connect(connection.connectionId) },
                        onSuspend = { viewModel.suspend(connection.connectionId) },
                        onDisconnect = { viewModel.disconnect(connection.connectionId) },
                        onReconnect = { viewModel.reconnect(connection.connectionId) },
                        onTest = { viewModel.testConnection(connection.connectionId) },
                        onViewAudit = { auditDialogFor = connection },
                    )
                }
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))

        auditDialogFor?.let { connection ->
            val records = viewModel.auditFor(connection.connectionId)
            AlertDialog(
                onDismissRequest = { auditDialogFor = null },
                title = { Text("${connection.providerName} audit trail") },
                text = {
                    if (records.isEmpty()) {
                        Text("No approval activity recorded for this connection yet.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Column {
                            records.sortedByDescending { it.timestamp }.forEach { record ->
                                AuditRecordRow(record)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { auditDialogFor = null }) { Text("Close") }
                },
            )
        }
    }
}

@Composable
private fun AuditRecordRow(record: ApprovalAuditRecord) {
    Column(modifier = Modifier.padding(bottom = JarvisSpacing.sm)) {
        Text(
            "${record.newState.name.lowercase().replaceFirstChar { it.uppercase() }} by ${record.actor}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            record.timestamp.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        record.reason?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConnectionCard(
    connection: Connection,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onConnect: () -> Unit,
    onSuspend: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
    onTest: () -> Unit,
    onViewAudit: () -> Unit,
) {
    JarvisCard(modifier = Modifier.fillMaxWidth().padding(vertical = JarvisSpacing.xs)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(connection.providerName, style = MaterialTheme.typography.titleMedium)
                StatusPill(connection.health.name, connection.health.toColor())
            }
            Text(
                "Status: ${connection.status.name.lowercase().replace('_', ' ')} · Trust ceiling: ${connection.trustLevel.maximumPermission.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (connection.trustLevel.grantedPermissions.isNotEmpty()) {
                Text(
                    "Permissions: ${connection.trustLevel.grantedPermissions.joinToString { it.name.lowercase() }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            connection.lastSync?.let {
                Text("Last sync: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = JarvisSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(JarvisSpacing.xs),
            ) {
                when (connection.status) {
                    ConnectionStatus.PENDING_APPROVAL -> {
                        Button(onClick = onApprove) { Text("Approve") }
                        OutlinedButton(
                            onClick = onReject,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisStatusColors.Unhealthy),
                        ) { Text("Reject") }
                    }
                    ConnectionStatus.APPROVED -> {
                        Button(onClick = onConnect) { Text("Connect") }
                        OutlinedButton(
                            onClick = onDisconnect,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisStatusColors.Unhealthy),
                        ) { Text("Cancel") }
                    }
                    ConnectionStatus.CONNECTING -> {
                        // No resolving action here by design — Sprint 9 wires the
                        // state machine's shape, not a real network handshake (see
                        // ConnectionRepository's BACKEND STATUS docstring). A real
                        // ConnectionRepository would move this to CONNECTED or
                        // ERROR on its own; Disconnect still lets the owner bail
                        // out of a stuck attempt rather than being stranded.
                        Text("Connecting…", style = MaterialTheme.typography.labelMedium)
                        OutlinedButton(
                            onClick = onDisconnect,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisStatusColors.Unhealthy),
                        ) { Text("Cancel") }
                    }
                    ConnectionStatus.CONNECTED -> {
                        OutlinedButton(onClick = onTest) { Text("Test") }
                        OutlinedButton(onClick = onSuspend) { Text("Suspend") }
                        OutlinedButton(
                            onClick = onDisconnect,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisStatusColors.Unhealthy),
                        ) { Text("Disconnect") }
                    }
                    ConnectionStatus.SUSPENDED -> {
                        Button(onClick = onReconnect) { Text("Reconnect") }
                        OutlinedButton(
                            onClick = onDisconnect,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisStatusColors.Unhealthy),
                        ) { Text("Disconnect") }
                    }
                    ConnectionStatus.ERROR -> {
                        Text("Connection error.", style = MaterialTheme.typography.labelMedium, color = JarvisStatusColors.Unhealthy)
                        Button(onClick = onReconnect) { Text("Retry") }
                        OutlinedButton(
                            onClick = onDisconnect,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisStatusColors.Unhealthy),
                        ) { Text("Disconnect") }
                    }
                    ConnectionStatus.DISCONNECTED, ConnectionStatus.REJECTED -> {
                        Text("Inactive — request a new connection to re-enable.", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Sprint 12: real data now -- ApprovalRepository.auditLog
            // filtered to this connection's own approval/rejection/
            // connect/suspend history (see ConnectionsViewModel.auditFor).
            TextButton(onClick = onViewAudit) { Text("View Audit") }
        }
    }
}

private fun ConnectionHealth.toColor() = when (this) {
    ConnectionHealth.HEALTHY -> JarvisStatusColors.Healthy
    ConnectionHealth.DEGRADED -> JarvisStatusColors.Degraded
    ConnectionHealth.UNHEALTHY -> JarvisStatusColors.Unhealthy
    ConnectionHealth.UNKNOWN -> JarvisStatusColors.Unknown
}

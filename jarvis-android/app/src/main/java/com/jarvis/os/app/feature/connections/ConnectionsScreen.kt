package com.jarvis.os.app.feature.connections

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.jarvis.os.app.data.model.Connection
import com.jarvis.os.app.data.model.ConnectionHealth
import com.jarvis.os.app.data.model.ConnectionStatus
import com.jarvis.os.app.designsystem.JarvisSpacing
import com.jarvis.os.app.designsystem.JarvisStatusColors
import com.jarvis.os.app.designsystem.components.JarvisCard
import com.jarvis.os.app.designsystem.components.StatusPill

@Composable
fun ConnectionsScreen(viewModel: ConnectionsViewModel = hiltViewModel()) {
    val connections by viewModel.connections.collectAsState()

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
                    onSuspend = { viewModel.suspend(connection.connectionId) },
                    onDisconnect = { viewModel.disconnect(connection.connectionId) },
                    onReconnect = { viewModel.reconnect(connection.connectionId) },
                    onTest = { viewModel.testConnection(connection.connectionId) },
                )
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    connection: Connection,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onSuspend: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
    onTest: () -> Unit,
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
                    ConnectionStatus.APPROVED -> Text("Awaiting connect…", style = MaterialTheme.typography.labelMedium)
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
                    ConnectionStatus.DISCONNECTED, ConnectionStatus.REJECTED, ConnectionStatus.FAILED -> {
                        Text("Inactive — request a new connection to re-enable.", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

private fun ConnectionHealth.toColor() = when (this) {
    ConnectionHealth.HEALTHY -> JarvisStatusColors.Healthy
    ConnectionHealth.DEGRADED -> JarvisStatusColors.Degraded
    ConnectionHealth.UNHEALTHY -> JarvisStatusColors.Unhealthy
    ConnectionHealth.UNKNOWN -> JarvisStatusColors.Unknown
}

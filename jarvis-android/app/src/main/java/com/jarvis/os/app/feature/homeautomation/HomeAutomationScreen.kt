package com.jarvis.os.app.feature.homeautomation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.os.app.data.model.HomeDevice
import com.jarvis.os.app.data.repository.HomeAutomationRepository
import com.jarvis.os.app.designsystem.JarvisSpacing
import com.jarvis.os.app.designsystem.components.JarvisCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeAutomationViewModel @Inject constructor(
    private val repository: HomeAutomationRepository,
) : ViewModel() {
    val devices = repository.devices.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The ViewModel also checks the policy before calling the repository — belt-and-suspenders with the repository's own check (see HomeAutomationRepository's docstring) and, more importantly, what lets the UI never even attempt the call for a security-tier device. */
    fun toggle(deviceId: String) {
        val device = devices.value.firstOrNull { it.deviceId == deviceId } ?: return
        if (!HomeAutomationPolicy.isSupported(device.type)) return
        repository.toggle(deviceId)
    }
}

@Composable
fun HomeAutomationScreen(viewModel: HomeAutomationViewModel = hiltViewModel()) {
    val devices by viewModel.devices.collectAsState()

    LazyColumn(contentPadding = PaddingValues(horizontal = JarvisSpacing.md, vertical = JarvisSpacing.sm)) {
        items(devices, key = { it.deviceId }) { device ->
            DeviceCard(device = device, onToggle = { viewModel.toggle(device.deviceId) })
        }
    }
}

@Composable
private fun DeviceCard(device: HomeDevice, onToggle: () -> Unit) {
    val supported = HomeAutomationPolicy.isSupported(device.type)
    val reason = HomeAutomationPolicy.unavailableReason(device.type)

    JarvisCard(modifier = Modifier.fillMaxWidth().padding(vertical = JarvisSpacing.xs)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(device.name, style = MaterialTheme.typography.titleMedium)
                    Text(device.type.name.lowercase().replace('_', ' '), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = device.isOn && supported,
                    onCheckedChange = { if (supported) onToggle() },
                    enabled = supported,
                    colors = SwitchDefaults.colors(),
                )
            }
            if (!supported && reason != null) {
                Text(reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = JarvisSpacing.xs))
            }
        }
    }
}

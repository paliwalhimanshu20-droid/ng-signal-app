package com.jarvis.os.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jarvis.os.app.designsystem.AccentColor
import com.jarvis.os.app.designsystem.AppearanceMode
import com.jarvis.os.app.designsystem.JarvisFontFamily
import com.jarvis.os.app.designsystem.JarvisFontScale
import com.jarvis.os.app.designsystem.JarvisSpacing
import com.jarvis.os.app.designsystem.components.JarvisCard
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val appearance by viewModel.appearance.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(JarvisSpacing.md)) {
        item { SectionHeader("Appearance") }

        item {
            JarvisCard(modifier = Modifier.fillMaxWidth().padding(bottom = JarvisSpacing.sm)) {
                Column {
                    Text("Mode", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.padding(top = JarvisSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(JarvisSpacing.sm),
                    ) {
                        AppearanceMode.values().forEach { mode ->
                            FilterChip(
                                selected = appearance.mode == mode,
                                onClick = { viewModel.setMode(mode) },
                                label = { Text(mode.name) },
                            )
                        }
                    }
                }
            }
        }

        item {
            JarvisCard(modifier = Modifier.fillMaxWidth().padding(bottom = JarvisSpacing.sm)) {
                Column {
                    Text("Accent Color", style = MaterialTheme.typography.titleMedium)
                    LazyRow(
                        modifier = Modifier.padding(top = JarvisSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(JarvisSpacing.sm),
                    ) {
                        items(AccentColor.values().toList()) { color ->
                            AccentSwatch(color = color, selected = appearance.accentColor == color, onClick = { viewModel.setAccentColor(color) })
                        }
                    }
                }
            }
        }

        item {
            JarvisCard(modifier = Modifier.fillMaxWidth().padding(bottom = JarvisSpacing.sm)) {
                Column {
                    Text("Font", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.padding(top = JarvisSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(JarvisSpacing.sm),
                    ) {
                        JarvisFontFamily.values().forEach { family ->
                            FilterChip(
                                selected = appearance.fontFamily == family,
                                onClick = { viewModel.setFontFamily(family) },
                                label = { Text(family.label) },
                            )
                        }
                    }

                    Text("Font Size", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = JarvisSpacing.md))
                    val scales = JarvisFontScale.values().toList()
                    val currentIndex = scales.indexOf(appearance.fontScale).coerceAtLeast(0)
                    Slider(
                        value = currentIndex.toFloat(),
                        onValueChange = { viewModel.setFontScale(scales[it.toInt().coerceIn(scales.indices)]) },
                        valueRange = 0f..(scales.size - 1).toFloat(),
                        steps = scales.size - 2,
                    )
                    Text(appearance.fontScale.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item { SectionHeader("Other settings") }
        item {
            JarvisCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ComingSoonSettingsRow("Voice") {
                        scope.launch { snackbarHostState.showSnackbar("Voice settings will be enabled in a future sprint.") }
                    }
                    ComingSoonSettingsRow("AI") {
                        scope.launch { snackbarHostState.showSnackbar("AI settings require a connected JARVIS Core backend.") }
                    }
                    ComingSoonSettingsRow("Connections") {
                        scope.launch { snackbarHostState.showSnackbar("Manage connections from the Connections tab; deeper settings are a future sprint.") }
                    }
                    ComingSoonSettingsRow("Notifications", showDivider = true) {
                        scope.launch { snackbarHostState.showSnackbar("Notification settings will be enabled once push is wired in a future sprint.") }
                    }
                    ComingSoonSettingsRow("Privacy", showDivider = false) {
                        scope.launch { snackbarHostState.showSnackbar("Privacy settings are under development.") }
                    }
                }
            }
        }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * Sprint-7.1 UX polish: each of these five areas used to be a single
 * paragraph of prose explaining they weren't built yet. That's honest
 * but not actionable — a tap now gives a specific, per-area status
 * message instead of nothing, matching every other "mock functional or
 * backend-pending" affordance in this app (Connections' View Audit,
 * Chat's voice button). No new settings screens are added — tapping a
 * row surfaces its status, it does not navigate anywhere.
 */
@Composable
private fun ComingSoonSettingsRow(label: String, showDivider: Boolean = true, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = JarvisSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                "Coming soon",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(top = JarvisSpacing.md, bottom = JarvisSpacing.sm),
    )
}

@Composable
private fun AccentSwatch(color: AccentColor, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onClick)
            .background(color.seed, CircleShape)
            .border(2.dp, if (selected) Color.White else Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = "${color.label} selected", tint = Color.White)
    }
}

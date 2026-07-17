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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

        item {
            JarvisCard(modifier = Modifier.fillMaxWidth().padding(bottom = JarvisSpacing.sm)) {
                Column {
                    Text("Motion", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "How alive JARVIS's background and avatar feel -- particle density, glow, and animation speed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = JarvisSpacing.xs, bottom = JarvisSpacing.sm),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(JarvisSpacing.sm)) {
                        com.jarvis.os.app.designsystem.JarvisMotionIntensity.values().forEach { intensity ->
                            FilterChip(
                                selected = appearance.motionIntensity == intensity,
                                onClick = { viewModel.setMotionIntensity(intensity) },
                                label = { Text(intensity.label) },
                            )
                        }
                    }
                }
            }
        }

        item {
            JarvisCard(modifier = Modifier.fillMaxWidth().padding(bottom = JarvisSpacing.sm)) {
                Column {
                    Text("Language", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "How JARVIS speaks with you by default. She'll still follow you if you switch languages mid-conversation -- this just sets her starting point.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = JarvisSpacing.xs, bottom = JarvisSpacing.sm),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(JarvisSpacing.sm)) {
                        com.jarvis.os.app.designsystem.JarvisLanguage.values().forEach { language ->
                            FilterChip(
                                selected = appearance.language == language,
                                onClick = { viewModel.setLanguage(language) },
                                label = { Text(language.label) },
                            )
                        }
                    }
                }
            }
        }

        item {
            JarvisCard(modifier = Modifier.fillMaxWidth().padding(bottom = JarvisSpacing.sm)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Speak replies aloud", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "JARVIS reads its responses using your device's text-to-speech voice.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = JarvisSpacing.xs),
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = appearance.voiceOutputEnabled,
                        onCheckedChange = { viewModel.setVoiceOutputEnabled(it) },
                    )
                }
            }
        }

        item {
            val aiState by viewModel.aiProviderState.collectAsState()
            val activeProviderId by viewModel.activeProviderId.collectAsState()
            JarvisCard(modifier = Modifier.fillMaxWidth().padding(bottom = JarvisSpacing.sm)) {
                Column {
                    Text("AI Provider", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Connect a real AI provider so JARVIS can actually converse, instead of running offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = JarvisSpacing.xs, bottom = JarvisSpacing.sm),
                    )

                    Text("Active provider", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(JarvisSpacing.sm), modifier = Modifier.padding(top = JarvisSpacing.xs, bottom = JarvisSpacing.md)) {
                        viewModel.availableProviderIds.forEach { (id, label) ->
                            FilterChip(
                                selected = activeProviderId == id,
                                onClick = { viewModel.switchProvider(id) },
                                label = { Text(label) },
                            )
                        }
                    }

                    if (aiState.hasStoredKey) {
                        Text("An API key is saved.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Row(modifier = Modifier.padding(top = JarvisSpacing.sm)) {
                            TextButton(onClick = { viewModel.testConnection() }, enabled = !aiState.testInProgress) {
                                Text(if (aiState.testInProgress) "Testing…" else "Test connection")
                            }
                            TextButton(onClick = { viewModel.clearApiKey() }) { Text("Remove key") }
                        }
                        aiState.testResult?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = JarvisSpacing.xs))
                        }
                    } else {
                        OutlinedTextField(
                            value = aiState.baseUrl,
                            onValueChange = { viewModel.updateBaseUrl(it) },
                            label = { Text("Base URL") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = JarvisSpacing.sm),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = aiState.model,
                            onValueChange = { viewModel.updateModel(it) },
                            label = { Text("Model") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = JarvisSpacing.sm),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = aiState.apiKeyInput,
                            onValueChange = { viewModel.updateApiKeyInput(it) },
                            label = { Text("API key") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        )
                        TextButton(onClick = { viewModel.saveApiKey() }, modifier = Modifier.padding(top = JarvisSpacing.sm)) {
                            Text("Save")
                        }
                    }
                }
            }
        }

        item {
            val geminiState by viewModel.geminiState.collectAsState()
            JarvisCard(modifier = Modifier.fillMaxWidth().padding(bottom = JarvisSpacing.sm)) {
                Column {
                    Text("Gemini", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Connect Google Gemini as an additional AI provider.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = JarvisSpacing.xs, bottom = JarvisSpacing.sm),
                    )
                    if (geminiState.hasStoredKey) {
                        Text("A Gemini API key is saved (model: ${geminiState.model}).", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        TextButton(onClick = { viewModel.clearGeminiKey() }, modifier = Modifier.padding(top = JarvisSpacing.sm)) { Text("Remove key") }
                    } else {
                        OutlinedTextField(
                            value = geminiState.model,
                            onValueChange = { viewModel.updateGeminiModel(it) },
                            label = { Text("Model") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = JarvisSpacing.sm),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = geminiState.apiKeyInput,
                            onValueChange = { viewModel.updateGeminiApiKeyInput(it) },
                            label = { Text("API key") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        )
                        TextButton(onClick = { viewModel.saveGeminiKey() }, modifier = Modifier.padding(top = JarvisSpacing.sm)) { Text("Save") }
                    }
                }
            }
        }

        item {
            val gitHubState by viewModel.gitHubState.collectAsState()
            JarvisCard(modifier = Modifier.fillMaxWidth().padding(bottom = JarvisSpacing.sm)) {
                Column {
                    Text("GitHub", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Connect GitHub so JARVIS can report real repository, pull request, and NG Signal Pro workflow status.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = JarvisSpacing.xs, bottom = JarvisSpacing.sm),
                    )
                    if (gitHubState.hasStoredToken) {
                        Text("Connected to ${gitHubState.owner}/${gitHubState.repo}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Row(modifier = Modifier.padding(top = JarvisSpacing.sm)) {
                            TextButton(onClick = { viewModel.refreshGitHubBackedStatus() }, enabled = !gitHubState.refreshInProgress) {
                                Text(if (gitHubState.refreshInProgress) "Refreshing…" else "Refresh now")
                            }
                            TextButton(onClick = { viewModel.clearGitHubToken() }) { Text("Remove") }
                        }
                    } else {
                        OutlinedTextField(
                            value = gitHubState.owner,
                            onValueChange = { viewModel.updateGitHubOwner(it) },
                            label = { Text("Repository owner") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = JarvisSpacing.sm),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = gitHubState.repo,
                            onValueChange = { viewModel.updateGitHubRepo(it) },
                            label = { Text("Repository name") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = JarvisSpacing.sm),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = gitHubState.tokenInput,
                            onValueChange = { viewModel.updateGitHubTokenInput(it) },
                            label = { Text("Personal Access Token") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        )
                        TextButton(onClick = { viewModel.saveGitHubToken() }, modifier = Modifier.padding(top = JarvisSpacing.sm)) { Text("Save") }
                    }
                }
            }
        }

        item { SectionHeader("Other settings") }
        item {
            JarvisCard(modifier = Modifier.fillMaxWidth()) {
                Column {
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

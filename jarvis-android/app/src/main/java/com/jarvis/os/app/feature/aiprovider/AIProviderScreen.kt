package com.jarvis.os.app.feature.aiprovider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import com.jarvis.os.app.core.chat.ProviderConnectionState
import com.jarvis.os.app.designsystem.JarvisBrand
import com.jarvis.os.app.designsystem.JarvisSpacing
import com.jarvis.os.app.designsystem.JarvisStatusColors
import com.jarvis.os.app.designsystem.components.GlassPanel
import com.jarvis.os.app.designsystem.jarvisHudLabelStyle
import com.jarvis.os.app.feature.settings.SettingsViewModel
import java.text.DateFormat
import java.util.Date

/**
 * "JARVIS Goes Live": "Tapping the AI Provider card should open a
 * dedicated AI Provider screen." Reuses SettingsViewModel rather than
 * introducing a second ViewModel with duplicated state.
 *
 * "AI Provider Stabilization & Truthfulness Audit": the "Connected"
 * label used to come from `hasStoredKey` alone -- the exact bug the
 * Owner found by testing the app (a saved key showing "Connected" next
 * to "No successful connection yet"). Every card below now reads
 * [ProviderConnectionState] from SettingsViewModel instead, the one
 * shared interpretation every screen uses (see that enum's own
 * docstring).
 *
 * Gemini is listed first -- "Implement Gemini as the first recommended
 * provider" -- followed by OpenAI and Claude. "Preferred provider" at
 * the bottom is real (AiRouter.switchProvider, the actual mechanism
 * driving which provider a real chat message goes through).
 *
 * "Auto Select" is deliberately NOT a toggle here -- see this
 * delivery's integration report: real chat always uses the manually
 * preferred provider, never capability-based auto-routing, and that
 * core send path wasn't changed in this pass.
 */
@Composable
fun AIProviderScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val geminiState by viewModel.geminiState.collectAsState()
    val geminiTestResult by viewModel.geminiTestResult.collectAsState()
    val geminiConnectionState by viewModel.geminiConnectionState.collectAsState()
    val aiState by viewModel.aiProviderState.collectAsState()
    val openAiConnectionState by viewModel.openAiConnectionState.collectAsState()
    val anthropicState by viewModel.anthropicState.collectAsState()
    val anthropicTestResult by viewModel.anthropicTestResult.collectAsState()
    val anthropicConnectionState by viewModel.anthropicConnectionState.collectAsState()
    val activeProviderId by viewModel.activeProviderId.collectAsState()

    LazyColumn(contentPadding = PaddingValues(JarvisSpacing.md)) {
        item {
            Text(
                "Connect a real AI provider so JARVIS can actually converse. Gemini is the fastest to set up.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = JarvisSpacing.md),
            )
        }

        item {
            ProviderCard(
                title = "Gemini",
                subtitle = "Google's Gemini models",
                connectionState = geminiConnectionState,
                hasStoredKey = geminiState.hasStoredKey,
                lastSuccessAt = geminiState.lastSuccessAt,
                testResult = geminiTestResult,
                modelValue = geminiState.model,
                onModelChange = { viewModel.updateGeminiModel(it) },
                apiKeyInput = geminiState.apiKeyInput,
                onApiKeyChange = { viewModel.updateGeminiApiKeyInput(it) },
                onSave = { viewModel.saveGeminiKey() },
                onRemove = { viewModel.clearGeminiKey() },
                onTest = { viewModel.testConnection("gemini") },
            )
        }

        item {
            ProviderCard(
                title = "OpenAI",
                subtitle = "GPT models via OpenAI's API",
                connectionState = openAiConnectionState,
                hasStoredKey = aiState.hasStoredKey,
                lastSuccessAt = aiState.lastSuccessAt,
                testResult = aiState.testResult,
                testInProgress = aiState.testInProgress,
                modelValue = aiState.model,
                onModelChange = { viewModel.updateModel(it) },
                extraField = Triple("Base URL", aiState.baseUrl, { url: String -> viewModel.updateBaseUrl(url) }),
                apiKeyInput = aiState.apiKeyInput,
                onApiKeyChange = { viewModel.updateApiKeyInput(it) },
                onSave = { viewModel.saveApiKey() },
                onRemove = { viewModel.clearApiKey() },
                onTest = { viewModel.testConnection("openai-compatible") },
            )
        }

        item {
            ProviderCard(
                title = "Claude",
                subtitle = "Anthropic's Claude models",
                connectionState = anthropicConnectionState,
                hasStoredKey = anthropicState.hasStoredKey,
                lastSuccessAt = anthropicState.lastSuccessAt,
                testResult = anthropicTestResult,
                modelValue = anthropicState.model,
                onModelChange = { viewModel.updateAnthropicModel(it) },
                apiKeyInput = anthropicState.apiKeyInput,
                onApiKeyChange = { viewModel.updateAnthropicApiKeyInput(it) },
                onSave = { viewModel.saveAnthropicKey() },
                onRemove = { viewModel.clearAnthropicKey() },
                onTest = { viewModel.testConnection("anthropic") },
            )
        }

        item {
            GlassPanel(modifier = Modifier.fillMaxWidth().padding(top = JarvisSpacing.sm)) {
                Column {
                    Text("PREFERRED PROVIDER", style = jarvisHudLabelStyle(), color = JarvisBrand.CoreCyan)
                    Text(
                        "Which provider your conversations actually go through right now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = JarvisSpacing.xs, bottom = JarvisSpacing.sm),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(JarvisSpacing.sm)) {
                        viewModel.availableProviderIds.forEach { (id, label) ->
                            FilterChip(
                                selected = activeProviderId == id,
                                onClick = { viewModel.switchProvider(id) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(
    title: String,
    subtitle: String,
    connectionState: ProviderConnectionState,
    hasStoredKey: Boolean,
    lastSuccessAt: Long?,
    testResult: String?,
    modelValue: String,
    onModelChange: (String) -> Unit,
    apiKeyInput: String,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onTest: () -> Unit,
    testInProgress: Boolean = false,
    extraField: Triple<String, String, (String) -> Unit>? = null,
) {
    GlassPanel(modifier = Modifier.fillMaxWidth().padding(bottom = JarvisSpacing.sm)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    connectionState.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = when (connectionState) {
                        ProviderConnectionState.VERIFIED, ProviderConnectionState.CONNECTED -> JarvisStatusColors.Healthy
                        ProviderConnectionState.RATE_LIMITED -> JarvisStatusColors.Degraded
                        ProviderConnectionState.ERROR -> JarvisStatusColors.Unhealthy
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = JarvisSpacing.xs, bottom = JarvisSpacing.sm))

            if (hasStoredKey) {
                Text(
                    lastSuccessAt?.let { "Last successful connection: ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))}" }
                        ?: "No successful connection yet -- try Test Connection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                testResult?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = JarvisSpacing.xs))
                }
                Row(modifier = Modifier.padding(top = JarvisSpacing.sm)) {
                    TextButton(onClick = onTest, enabled = !testInProgress) { Text(if (testInProgress) "Testing…" else "Test connection") }
                    TextButton(onClick = onRemove) { Text("Remove") }
                }
            } else {
                extraField?.let { (label, value, onChange) ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = onChange,
                        label = { Text(label) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = JarvisSpacing.sm),
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = modelValue,
                    onValueChange = onModelChange,
                    label = { Text("Model") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = JarvisSpacing.sm),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = onApiKeyChange,
                    label = { Text("API key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                TextButton(onClick = onSave, modifier = Modifier.padding(top = JarvisSpacing.sm)) { Text("Connect") }
            }
        }
    }
}

package com.jarvis.os.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.os.app.core.chat.AiRouter
import com.jarvis.os.app.core.chat.ChatChunk
import com.jarvis.os.app.data.settings.AiProviderConfig
import com.jarvis.os.app.data.settings.ApiKeyStore
import com.jarvis.os.app.data.settings.AppearanceSettings
import com.jarvis.os.app.data.settings.EncryptedApiKeyStore
import com.jarvis.os.app.data.settings.SettingsRepository
import com.jarvis.os.app.designsystem.AccentColor
import com.jarvis.os.app.designsystem.AppearanceMode
import com.jarvis.os.app.designsystem.JarvisFontFamily
import com.jarvis.os.app.designsystem.JarvisFontScale
import com.jarvis.os.app.designsystem.JarvisLanguage
import com.jarvis.os.app.designsystem.JarvisMotionIntensity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiProviderUiState(
    val baseUrl: String = EncryptedApiKeyStore.DEFAULT_BASE_URL,
    val model: String = EncryptedApiKeyStore.DEFAULT_MODEL,
    val apiKeyInput: String = "",
    val hasStoredKey: Boolean = false,
    val testInProgress: Boolean = false,
    val testResult: String? = null,
)

/**
 * Acceptance Scenario 2: "Owner customizes Accent Color, Font,
 * Background, Dashboard. Changes persist after restart." Every setter
 * below writes straight through SettingsRepository (DataStore) — there
 * is no local-only draft state that could be lost; the UI reflects
 * whatever was last durably written, on every recomposition, which is
 * what makes "persists after restart" true by construction rather than
 * by a save button the Owner has to remember to press.
 *
 * Sprint 12 "Real AI Conversation": AI Provider configuration is
 * different on purpose — it's a real secret (ApiKeyStore /
 * EncryptedSharedPreferences), not an ordinary DataStore preference,
 * so it does go through an explicit Save action rather than writing on
 * every keystroke. [aiProviderState] starts from whatever's already
 * stored (never re-displaying the key itself once saved -- only
 * [AiProviderUiState.hasStoredKey], never the key text) and
 * [testConnection] makes a REAL call through the real provider to
 * verify the key actually works, not a local validation check.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val apiKeyStore: ApiKeyStore,
    private val aiRouter: AiRouter,
) : ViewModel() {

    val appearance = repository.appearance.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppearanceSettings())

    fun setMode(mode: AppearanceMode) = viewModelScope.launch { repository.setAppearanceMode(mode) }
    fun setAccentColor(color: AccentColor) = viewModelScope.launch { repository.setAccentColor(color) }
    fun setFontFamily(family: JarvisFontFamily) = viewModelScope.launch { repository.setFontFamily(family) }
    fun setFontScale(scale: JarvisFontScale) = viewModelScope.launch { repository.setFontScale(scale) }
    fun setBackgroundColorHex(hex: String?) = viewModelScope.launch { repository.setBackgroundColorHex(hex) }
    fun setMotionIntensity(intensity: JarvisMotionIntensity) = viewModelScope.launch { repository.setMotionIntensity(intensity) }
    fun setVoiceOutputEnabled(enabled: Boolean) = viewModelScope.launch { repository.setVoiceOutputEnabled(enabled) }
    fun setLanguage(language: JarvisLanguage) = viewModelScope.launch { repository.setLanguage(language) }

    // --- Sprint 12 "Real AI Conversation": AI Provider configuration ---

    val activeProviderId: StateFlow<String> = aiRouter.activeProviderId
    val availableProviderIds: List<Pair<String, String>> get() = aiRouter.available.map { it.id to it.displayName }

    private val _aiProviderState = MutableStateFlow(loadStoredState())
    val aiProviderState: StateFlow<AiProviderUiState> = _aiProviderState.asStateFlow()

    private fun loadStoredState(): AiProviderUiState {
        val config = apiKeyStore.currentConfig() ?: return AiProviderUiState()
        return AiProviderUiState(baseUrl = config.baseUrl, model = config.model, hasStoredKey = true)
    }

    fun updateBaseUrl(url: String) {
        _aiProviderState.value = _aiProviderState.value.copy(baseUrl = url)
    }

    fun updateModel(model: String) {
        _aiProviderState.value = _aiProviderState.value.copy(model = model)
    }

    fun updateApiKeyInput(key: String) {
        _aiProviderState.value = _aiProviderState.value.copy(apiKeyInput = key)
    }

    fun saveApiKey() {
        val state = _aiProviderState.value
        if (state.apiKeyInput.isBlank()) return
        apiKeyStore.save(AiProviderConfig(baseUrl = state.baseUrl, model = state.model, apiKey = state.apiKeyInput))
        _aiProviderState.value = state.copy(apiKeyInput = "", hasStoredKey = true, testResult = null)
    }

    fun clearApiKey() {
        apiKeyStore.clear()
        _aiProviderState.value = AiProviderUiState()
    }

    fun switchProvider(providerId: String) {
        aiRouter.switchProvider(providerId)
    }

    /** A REAL call through the real provider -- confirms the key actually works, not a local format check. */
    fun testConnection() {
        viewModelScope.launch {
            _aiProviderState.value = _aiProviderState.value.copy(testInProgress = true, testResult = null)
            val provider = aiRouter.available.firstOrNull { it.id == "openai-compatible" }
            var outcome = "That provider isn't available."
            if (provider != null) {
                outcome = "No response received."
                provider.sendMessage("settings-connection-test", "Reply with just the word OK.").collect { chunk ->
                    when (chunk) {
                        is ChatChunk.Complete -> outcome = if (chunk.fullText.isBlank()) "Connected, but the reply was empty." else "Connected: ${chunk.fullText.take(80)}"
                        is ChatChunk.Error -> outcome = chunk.message
                        else -> Unit
                    }
                }
            }
            _aiProviderState.value = _aiProviderState.value.copy(testInProgress = false, testResult = outcome)
        }
    }
}

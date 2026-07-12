package com.jarvis.os.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.os.app.data.settings.AppearanceSettings
import com.jarvis.os.app.data.settings.SettingsRepository
import com.jarvis.os.app.designsystem.AccentColor
import com.jarvis.os.app.designsystem.AppearanceMode
import com.jarvis.os.app.designsystem.JarvisFontFamily
import com.jarvis.os.app.designsystem.JarvisFontScale
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Acceptance Scenario 2: "Owner customizes Accent Color, Font,
 * Background, Dashboard. Changes persist after restart." Every setter
 * below writes straight through SettingsRepository (DataStore) — there
 * is no local-only draft state that could be lost; the UI reflects
 * whatever was last durably written, on every recomposition, which is
 * what makes "persists after restart" true by construction rather than
 * by a save button the Owner has to remember to press.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    val appearance = repository.appearance.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppearanceSettings())

    fun setMode(mode: AppearanceMode) = viewModelScope.launch { repository.setAppearanceMode(mode) }
    fun setAccentColor(color: AccentColor) = viewModelScope.launch { repository.setAccentColor(color) }
    fun setFontFamily(family: JarvisFontFamily) = viewModelScope.launch { repository.setFontFamily(family) }
    fun setFontScale(scale: JarvisFontScale) = viewModelScope.launch { repository.setFontScale(scale) }
    fun setBackgroundColorHex(hex: String?) = viewModelScope.launch { repository.setBackgroundColorHex(hex) }
}

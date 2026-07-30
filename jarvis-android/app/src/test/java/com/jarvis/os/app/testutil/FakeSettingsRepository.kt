package com.jarvis.os.app.testutil

import com.jarvis.os.app.data.settings.AppearanceSettings
import com.jarvis.os.app.data.settings.DashboardLayout
import com.jarvis.os.app.data.settings.SettingsRepository
import com.jarvis.os.app.designsystem.AccentColor
import com.jarvis.os.app.designsystem.AppearanceMode
import com.jarvis.os.app.designsystem.JarvisFontFamily
import com.jarvis.os.app.designsystem.JarvisFontScale
import com.jarvis.os.app.designsystem.JarvisLanguage
import com.jarvis.os.app.designsystem.JarvisMotionIntensity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * "JARVIS Personality & Experience Bible": MockChatProvider,
 * ExecutiveBriefingEngine, and OpenAiCompatibleChatProvider all now
 * read the Owner's language preference through SettingsRepository --
 * this is the one shared, minimal in-memory fake every test that
 * constructs any of those needs, rather than eight near-identical
 * private copies scattered across as many test files. Fixed to
 * English by default so tests asserting on exact wording aren't
 * flaky depending on which language happens to be selected; a real
 * SettingsRepository (DataStore-backed) needs a real Android Context
 * these plain JVM tests don't have.
 */
class FakeSettingsRepository(language: JarvisLanguage = JarvisLanguage.English) : SettingsRepository {
    private val _appearance = MutableStateFlow(AppearanceSettings(language = language))
    override val appearance: Flow<AppearanceSettings> = _appearance
    override val dashboardLayout: Flow<DashboardLayout> = MutableStateFlow(DashboardLayout.default())

    override suspend fun setAppearanceMode(mode: AppearanceMode) { _appearance.value = _appearance.value.copy(mode = mode) }
    override suspend fun setAccentColor(color: AccentColor) { _appearance.value = _appearance.value.copy(accentColor = color) }
    override suspend fun setFontFamily(family: JarvisFontFamily) { _appearance.value = _appearance.value.copy(fontFamily = family) }
    override suspend fun setFontScale(scale: JarvisFontScale) { _appearance.value = _appearance.value.copy(fontScale = scale) }
    override suspend fun setBackgroundColorHex(hex: String?) { _appearance.value = _appearance.value.copy(backgroundColorHex = hex) }
    override suspend fun setDashboardLayout(layout: DashboardLayout) {}
    override suspend fun setMotionIntensity(intensity: JarvisMotionIntensity) { _appearance.value = _appearance.value.copy(motionIntensity = intensity) }
    override suspend fun setVoiceOutputEnabled(enabled: Boolean) { _appearance.value = _appearance.value.copy(voiceOutputEnabled = enabled) }
    override suspend fun setLanguage(language: JarvisLanguage) { _appearance.value = _appearance.value.copy(language = language) }
    override suspend fun setPersonaDisplayName(name: String?) { _appearance.value = _appearance.value.copy(personaDisplayName = name) }
}

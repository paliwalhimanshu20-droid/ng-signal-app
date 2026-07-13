package com.jarvis.os.app.data.settings

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jarvis.os.app.designsystem.AccentColor
import com.jarvis.os.app.designsystem.AppearanceMode
import com.jarvis.os.app.designsystem.JarvisFontFamily
import com.jarvis.os.app.designsystem.JarvisFontScale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Part 13: "Everything must persist." This is the ONE place every
 * persisted Owner preference (appearance, dashboard layout, and — as
 * this app grows — voice/AI/notification preferences) is read from and
 * written to, mirroring the Python backend's own "one gateway per
 * concern" pattern (MemoryManager, ConnectionManager). DataStore
 * Preferences is used rather than SharedPreferences per current
 * Android guidance (async, Flow-based, no UI-thread disk I/O) and
 * rather than a database, since every value here is a small, singular
 * setting, not queryable structured data.
 *
 * KNOWN GAP, stated plainly: this repository has no counterpart on the
 * Python backend yet — Sprint-6 built no API surface for a mobile app to
 * sync preferences THROUGH (see this module's package-level note in
 * the repository layer). Everything here persists locally, on-device,
 * correctly and durably — it does not yet sync to or from JARVIS Core.
 */
interface SettingsRepository {
    val appearance: Flow<AppearanceSettings>
    val dashboardLayout: Flow<DashboardLayout>

    suspend fun setAppearanceMode(mode: AppearanceMode)
    suspend fun setAccentColor(color: AccentColor)
    suspend fun setFontFamily(family: JarvisFontFamily)
    suspend fun setFontScale(scale: JarvisFontScale)
    suspend fun setBackgroundColorHex(hex: String?)
    suspend fun setDashboardLayout(layout: DashboardLayout)
}

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private object Keys {
        val APPEARANCE_MODE = stringPreferencesKey("appearance_mode")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val FONT_SCALE = stringPreferencesKey("font_scale")
        val BACKGROUND_COLOR_HEX = stringPreferencesKey("background_color_hex")
        val DASHBOARD_LAYOUT = stringPreferencesKey("dashboard_layout")
    }

    override val appearance: Flow<AppearanceSettings> = dataStore.data.map { prefs ->
        AppearanceSettings(
            mode = prefs[Keys.APPEARANCE_MODE]?.let { runCatching { AppearanceMode.valueOf(it) }.getOrNull() }
                ?: AppearanceMode.Dark,
            accentColor = prefs[Keys.ACCENT_COLOR]?.let { runCatching { AccentColor.valueOf(it) }.getOrNull() }
                ?: AccentColor.ArcBlue,
            fontFamily = prefs[Keys.FONT_FAMILY]?.let { runCatching { JarvisFontFamily.valueOf(it) }.getOrNull() }
                ?: JarvisFontFamily.SansDefault,
            fontScale = prefs[Keys.FONT_SCALE]?.let { runCatching { JarvisFontScale.valueOf(it) }.getOrNull() }
                ?: JarvisFontScale.Default,
            backgroundColorHex = prefs[Keys.BACKGROUND_COLOR_HEX],
        )
    }

    override val dashboardLayout: Flow<DashboardLayout> = dataStore.data.map { prefs ->
        val raw = prefs[Keys.DASHBOARD_LAYOUT].orEmpty()
        val deserialized = DashboardLayout.deserialize(raw)
        // STEP 5: exactly what came OUT of DataStore on this emission,
        // both the raw stored string and what it deserialized to.
        Log.d("JARVIS-TRACE", "STEP5 dataStore.data emission raw=\"$raw\" -> ${deserialized.cards.joinToString { "${it.id.name}:${it.visible}" }}")
        deserialized
    }

    override suspend fun setAppearanceMode(mode: AppearanceMode) {
        dataStore.edit { it[Keys.APPEARANCE_MODE] = mode.name }
    }

    override suspend fun setAccentColor(color: AccentColor) {
        dataStore.edit { it[Keys.ACCENT_COLOR] = color.name }
    }

    override suspend fun setFontFamily(family: JarvisFontFamily) {
        dataStore.edit { it[Keys.FONT_FAMILY] = family.name }
    }

    override suspend fun setFontScale(scale: JarvisFontScale) {
        dataStore.edit { it[Keys.FONT_SCALE] = scale.name }
    }

    override suspend fun setBackgroundColorHex(hex: String?) {
        dataStore.edit { prefs ->
            if (hex == null) prefs.remove(Keys.BACKGROUND_COLOR_HEX) else prefs[Keys.BACKGROUND_COLOR_HEX] = hex
        }
    }

    override suspend fun setDashboardLayout(layout: DashboardLayout) {
        // STEP 3/4: exactly what's being serialized and written into
        // DataStore's edit transaction.
        val serialized = layout.serialize()
        Log.d("JARVIS-TRACE", "STEP3/4 setDashboardLayout() writing serialize()=\"$serialized\"")
        dataStore.edit { it[Keys.DASHBOARD_LAYOUT] = serialized }
        Log.d("JARVIS-TRACE", "STEP3/4 dataStore.edit{} transaction completed")
    }
}

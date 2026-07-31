package com.jarvis.os.app.core.intelligence.localintent

import com.jarvis.os.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings: answers "what's my theme", "what language is JARVIS set to", "is voice output on"
 * directly from [SettingsRepository] -- the single, real, persisted source of every Owner
 * preference (see that interface's own "Everything must persist" docstring). Read-only, same as
 * every other handler in this router; changing a setting still requires the Settings screen.
 */
@Singleton
class SettingsLocalIntentHandler @Inject constructor(
    private val settings: SettingsRepository,
) : LocalIntentHandler {

    override val domain = LocalServiceDomain.SETTINGS

    override suspend fun tryHandle(text: String): String? {
        val lower = text.lowercase()
        if (KEYWORDS.none { it in lower }) return null

        val appearance = settings.appearance.first()
        val layout = settings.dashboardLayout.first()

        return "Appearance: mode ${appearance.mode}, accent ${appearance.accentColor}, language ${appearance.language}, " +
            "voice output ${if (appearance.voiceOutputEnabled) "on" else "off"}. " +
            "Dashboard has ${layout.cards.count { it.visible }} of ${layout.cards.size} card(s) visible."
    }

    companion object {
        private val KEYWORDS = setOf(
            "my theme", "my appearance", "current settings", "my settings", "dashboard layout",
            "voice output", "what language is jarvis", "my accent color", "font settings",
        )
    }
}

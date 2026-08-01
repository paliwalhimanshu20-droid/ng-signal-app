package com.jarvis.os.app.core.intelligence.localintent

import com.jarvis.os.app.core.chat.AiRouter
import com.jarvis.os.app.data.settings.SettingsRepository
import com.jarvis.os.app.designsystem.AppearanceMode
import com.jarvis.os.app.designsystem.JarvisFontScale
import com.jarvis.os.app.designsystem.JarvisLanguage
import com.jarvis.os.app.designsystem.JarvisMotionIntensity
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Phase 3C, Section 7+8+9+12 -- DEVICE_ACTION routing + Command Authority + JARVIS Personality."
 * "User should NOT have to open Settings" is the literal point of this class: every command below
 * calls a real, EXISTING [SettingsRepository] setter or [AiRouter.switchProvider] directly --
 * per this phase's "reuse the existing SettingsRepository... do NOT build another settings
 * system" rule, there is no new settings storage here, only a natural-language front door onto
 * settings storage that already existed and was already real (see the Phase 3B survey notes on
 * why this was the one piece actually missing from an otherwise-complete Settings Engine).
 *
 * Every match here is a "safe" command per this phase's own Permission Engine list (Language,
 * Theme, Font, Voice, Animations, Provider) -- destructive commands (delete, reset, erase,
 * disconnect, factory reset) are explicitly OUT of this handler's scope; Section 10's
 * confirmation-gated Permission Engine is future work this class deliberately does not reach for.
 *
 * Returns [LocalIntentOutcome.DEVICE_ACTION] rather than LOCAL_ONLY -- same "no AI call, ever"
 * rendering as LOCAL_ONLY (see that outcome's own docstring), but a distinct, honest domain tag
 * for the Response Source Engine: this is JARVIS acting, not JARVIS answering.
 */
@Singleton
class DeviceActionLocalIntentHandler @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val aiRouter: AiRouter,
) : LocalIntentHandler {

    override val domain = LocalServiceDomain.DEVICE_ACTION

    override suspend fun tryHandle(text: String): LocalIntentAnswer? {
        val lower = text.trim().lowercase()

        languageCommand(lower)?.let { language ->
            settingsRepository.setLanguage(language)
            return act(confirmLanguage(language))
        }

        themeCommand(lower)?.let { mode ->
            settingsRepository.setAppearanceMode(mode)
            return act("${mode.name} mode is on now.")
        }

        fontCommand(lower)?.let { increase ->
            val current = settingsRepository.appearance.first().fontScale
            val options = JarvisFontScale.entries
            val next = options.getOrNull(current.ordinal + if (increase) 1 else -1) ?: current
            settingsRepository.setFontScale(next)
            return act(
                if (next == current) {
                    "Font is already at its ${if (increase) "largest" else "smallest"} setting."
                } else {
                    "Font size set to ${next.label}."
                },
            )
        }

        motionCommand(lower)?.let { increase ->
            val current = settingsRepository.appearance.first().motionIntensity
            val options = JarvisMotionIntensity.entries
            val next = options.getOrNull(current.ordinal + if (increase) 1 else -1) ?: current
            settingsRepository.setMotionIntensity(next)
            return act(
                if (next == current) {
                    "Motion is already at its ${if (increase) "most vivid" else "calmest"} setting."
                } else {
                    "Motion set to ${next.label}."
                },
            )
        }

        voiceCommand(lower)?.let { enabled ->
            settingsRepository.setVoiceOutputEnabled(enabled)
            return act(if (enabled) "Voice replies are on now." else "Voice replies are off now.")
        }

        providerCommand(lower)?.let { providerId ->
            val switched = aiRouter.switchProvider(providerId)
            return act(
                if (switched) {
                    val displayName = aiRouter.available.first { it.id == providerId }.displayName
                    "Switched to $displayName."
                } else {
                    "I don't have a provider called '$providerId' connected -- check AI Provider Settings for what's available."
                },
            )
        }

        return null
    }

    private suspend fun act(message: String): LocalIntentAnswer = LocalIntentAnswer(message, LocalIntentOutcome.DEVICE_ACTION)

    private fun confirmLanguage(language: JarvisLanguage): String = when (language) {
        JarvisLanguage.English -> "Switched to English."
        JarvisLanguage.Hindi -> "हिंदी में बदल दिया गया।"
        JarvisLanguage.Hinglish -> "Hinglish mein switch kar diya."
    }

    private fun languageCommand(lower: String): JarvisLanguage? = when {
        "speak english" in lower || "switch to english" in lower || "talk in english" in lower || "english mein baat" in lower -> JarvisLanguage.English
        "speak hindi" in lower || "switch to hindi" in lower || "hindi mein baat" in lower || "hindi me baat" in lower -> JarvisLanguage.Hindi
        "speak hinglish" in lower || "switch to hinglish" in lower || "talk in hinglish" in lower || "hinglish mein baat" in lower -> JarvisLanguage.Hinglish
        else -> null
    }

    private fun themeCommand(lower: String): AppearanceMode? = when {
        "dark mode" in lower || "switch to dark" in lower || "dark theme" in lower -> AppearanceMode.Dark
        "light mode" in lower || "switch to light" in lower || "light theme" in lower -> AppearanceMode.Light
        "amoled mode" in lower || "amoled theme" in lower -> AppearanceMode.Amoled
        else -> null
    }

    /** Returns true for "increase", false for "decrease", null for no match. */
    private fun fontCommand(lower: String): Boolean? = when {
        "increase font" in lower || "bigger font" in lower || "larger font" in lower || "larger text" in lower || "increase text size" in lower -> true
        "decrease font" in lower || "smaller font" in lower || "reduce font" in lower || "smaller text" in lower || "decrease text size" in lower -> false
        else -> null
    }

    /** Returns true for "increase", false for "decrease", null for no match. */
    private fun motionCommand(lower: String): Boolean? = when {
        "increase motion" in lower || "more animation" in lower || "increase animation" in lower -> true
        "reduce motion" in lower || "reduce animations" in lower || "less motion" in lower || "decrease motion" in lower || "disable animations" in lower -> false
        else -> null
    }

    private fun voiceCommand(lower: String): Boolean? = when {
        "enable voice" in lower || "turn on voice" in lower || "voice replies on" in lower -> true
        "disable voice" in lower || "turn off voice" in lower || "voice replies off" in lower || "stop talking" in lower -> false
        else -> null
    }

    private fun providerCommand(lower: String): String? = when {
        "use openai" in lower || "use chatgpt" in lower -> "openai-compatible"
        "use claude" in lower -> "anthropic"
        "use gemini" in lower -> "gemini"
        "use groq" in lower -> "groq"
        "use local mode" in lower || "go offline" in lower || "offline mode" in lower -> "mock"
        else -> null
    }
}

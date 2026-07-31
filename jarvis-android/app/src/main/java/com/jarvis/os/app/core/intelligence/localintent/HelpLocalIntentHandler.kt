package com.jarvis.os.app.core.intelligence.localintent

import com.jarvis.os.app.BuildConfig
import com.jarvis.os.app.core.chat.AiRouter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Offline Completion" milestone, requirement 2: built-in questions about JARVIS itself --
 * identity, capabilities, version, quick status -- answered locally and deterministically, the
 * same reasoning as [GreetingLocalIntentHandler] (see that class's docstring for why exact-phrase
 * matching, not substring "contains", is used here too).
 *
 * [statusResponse] deliberately reports whether an AI provider is CONFIGURED via
 * [com.jarvis.os.app.core.chat.ChatProvider.isConfigured] rather than guessing or omitting that
 * fact -- this is the one place in this router that's honest about the thing the rest of the
 * "Offline Completion" milestone exists to work around, matching this codebase's "no fake
 * success" discipline: JARVIS should say plainly that no provider is configured yet, not pretend
 * the question doesn't exist.
 */
@Singleton
class HelpLocalIntentHandler @Inject constructor(
    private val aiRouter: AiRouter,
) : LocalIntentHandler {

    override val domain = LocalServiceDomain.HELP

    override suspend fun tryHandle(text: String): LocalIntentAnswer? {
        val normalized = text.trim().lowercase().trimEnd('!', '.', '?', ',')
        val response = when (normalized) {
            in IDENTITY_PHRASES -> IDENTITY_RESPONSE
            in CAPABILITY_PHRASES -> CAPABILITY_RESPONSE
            in VERSION_PHRASES -> "JARVIS OS version ${BuildConfig.VERSION_NAME}."
            in STATUS_PHRASES -> statusResponse()
            else -> null
        } ?: return null
        return LocalIntentAnswer(response)
    }

    private fun statusResponse(): String {
        val configuredCount = aiRouter.available.count { it.isConfigured() }
        val aiStatus = if (configuredCount > 0) {
            val activeNote = if (aiRouter.active.isConfigured()) "" else " (not yet configured -- switch to a configured one in AI Provider Settings)"
            "Active AI provider: ${aiRouter.active.displayName}$activeNote. $configuredCount of ${aiRouter.available.size} bound provider(s) configured."
        } else {
            "No AI provider is configured yet -- local capabilities (Trading Intelligence Database, Signals, Analytics, Mission Control, Connected Systems, Diagnostics, Settings) remain fully available regardless."
        }
        return "System status: all local subsystems are online. $aiStatus"
    }

    companion object {
        private val IDENTITY_PHRASES = setOf("who are you", "what are you", "who is jarvis", "what is jarvis")
        private val CAPABILITY_PHRASES = setOf("what can you do", "help", "capabilities", "what are your capabilities", "what do you do")
        private val VERSION_PHRASES = setOf("version", "what version", "what's your version", "whats your version")
        private val STATUS_PHRASES = setOf("status", "system status", "are you working", "are you online")

        private const val IDENTITY_RESPONSE =
            "I'm JARVIS -- your trading operating system, not a chatbot. I run the Trading Intelligence Database, Signals, Analytics, " +
                "Mission Control, Connected Systems, Diagnostics, and Settings directly and locally; an AI provider is only brought in for " +
                "reasoning or knowledge genuinely beyond that."
        private const val CAPABILITY_RESPONSE =
            "Entirely locally, I can: look up instrument/candle/contract data, report active signals, summarize trading performance and " +
                "open trades, show Mission Control and Connected Systems status, run diagnostics, read your settings, and answer built-in " +
                "trading questions (e.g. what is EMA/RSI/ATR/Supertrend). Anything beyond that -- open-ended reasoning or general knowledge " +
                "-- goes through an AI provider once one is configured."
    }
}

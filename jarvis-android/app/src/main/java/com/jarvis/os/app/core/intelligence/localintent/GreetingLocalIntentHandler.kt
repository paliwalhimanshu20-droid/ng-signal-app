package com.jarvis.os.app.core.intelligence.localintent

import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Offline Completion" milestone, requirement 1: plain greetings ("hello", "hi", "good morning")
 * answered locally, with zero AI provider involvement -- these were the exact category of
 * message that used to reach `router.active.sendMessage` immediately and surface "No Claude API
 * key is configured" with nothing local ever having had a chance to answer first.
 *
 * Matched by EXACT normalized phrase, not substring "contains" -- "hey, how many instruments do
 * we have" must NOT be swallowed here just because it starts with "hey"; it needs to keep falling
 * through to [TidbLocalIntentHandler]. Normalizing strips only surrounding whitespace and trailing
 * punctuation, so "hello!", "Hello.", and "  hi  " all still match while a greeting used as part
 * of a longer, substantive message does not.
 */
@Singleton
class GreetingLocalIntentHandler @Inject constructor() : LocalIntentHandler {

    override val domain = LocalServiceDomain.GREETING

    override suspend fun tryHandle(text: String): LocalIntentAnswer? {
        val normalized = text.trim().lowercase().trimEnd('!', '.', '?', ',')
        if (normalized !in GREETINGS) return null
        return LocalIntentAnswer(
            "Hello. JARVIS is online -- Trading Intelligence Database, Signals, Analytics, Mission Control, Connected Systems, " +
                "Diagnostics, and Settings are all available right now, no AI provider required for any of that. How can I help?",
        )
    }

    companion object {
        private val GREETINGS = setOf(
            "hi", "hello", "hey", "hiya", "yo", "hola",
            "good morning", "good afternoon", "good evening", "good night",
            "greetings", "what's up", "whats up", "sup", "namaste",
        )
    }
}

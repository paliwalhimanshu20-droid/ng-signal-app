package com.jarvis.os.app.core.intelligence.localintent

import com.jarvis.os.app.core.intelligence.selfawareness.SelfAwarenessEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 4B Slice 2, Section 4 -- Mission Status. Answers "Where are we?", "What is complete?",
 * "What is missing?", "What is next?", "What is blocking live trading?", "What can you do?",
 * "What can't you do?" -- all routed to real [SelfAwarenessEngine] queries over
 * [com.jarvis.os.app.core.intelligence.selfawareness.CapabilityInventory], never a fabricated
 * summary (Section 1: "Never hallucinate").
 *
 * Deliberately a DIFFERENT domain from [HelpLocalIntentHandler]'s CAPABILITY_PHRASES ("what can
 * you do", "capabilities") -- that handler already claims those exact phrases and is declared
 * earlier in [LocalServiceDomain], so this handler's keyword set is disjoint from it by design
 * (see the phrase lists below) rather than attempting to out-order or replace an existing,
 * already-tested local answer. A future pass that wants the richer, capability-inventory-backed
 * answer to also win on HelpLocalIntentHandler's own phrasing is a deliberate, reviewed decision
 * about which of two real answers should win -- not something this slice makes silently.
 */
@Singleton
class SelfAwarenessLocalIntentHandler @Inject constructor(
    private val selfAwareness: SelfAwarenessEngine,
) : LocalIntentHandler {

    override val domain = LocalServiceDomain.SELF_AWARENESS

    override suspend fun tryHandle(text: String): LocalIntentAnswer? {
        val lower = text.trim().lowercase().trimEnd('!', '.', '?', ',')
        val response = when {
            WHERE_PHRASES.any { it in lower } -> selfAwareness.whereAreWe()
            WHATS_COMPLETE_PHRASES.any { it in lower } -> renderList(
                "Complete", selfAwareness.whatIsComplete().map { it.name },
            )
            WHATS_MISSING_PHRASES.any { it in lower } -> renderList(
                "Missing", selfAwareness.whatIsMissing().map { "${it.name} -- ${it.risk ?: it.verificationState}" },
            )
            WHATS_NEXT_PHRASES.any { it in lower } -> renderList("Next", selfAwareness.whatIsNext())
            BLOCKING_LIVE_TRADING_PHRASES.any { it in lower } -> selfAwareness.whatIsBlockingLiveTrading()
            CANT_DO_PHRASES.any { it in lower } -> renderList("Can't do yet", selfAwareness.whatCantYouDo())
            else -> null
        } ?: return null
        return LocalIntentAnswer(response)
    }

    private fun renderList(label: String, items: List<String>): String =
        if (items.isEmpty()) "$label: nothing to report." else "$label (${items.size}): " + items.joinToString("; ")

    companion object {
        private val WHERE_PHRASES = setOf("where are we", "repository reality report", "self awareness report", "self-awareness report", "mission status", "give me a status report")
        private val WHATS_COMPLETE_PHRASES = setOf("what is complete", "what's complete", "whats complete", "what has been built", "what's done", "what is done")
        // Runtime Integration milestone: broadened from exact multi-word phrases to shorter
        // fragments after "What is still missing before you can recommend trades?" failed to
        // match "what is missing" (the word "still" breaks contiguous-substring matching) -- see
        // this handler's class docstring. Short fragments are deliberately still literal
        // substring checks (this codebase's established, auditable matching style -- see
        // TidbLocalIntentHandler's own DATA_KEYWORDS), just narrow enough not to require an exact
        // canned sentence.
        private val WHATS_MISSING_PHRASES = setOf("is missing", "still missing", "what's missing", "whats missing", "hasn't been built", "has not been built", "not yet built", "not been built")
        private val WHATS_NEXT_PHRASES = setOf("what is next", "what's next", "whats next", "what should we build next", "build next")
        private val BLOCKING_LIVE_TRADING_PHRASES = setOf(
            "blocking live trading", "prevents you from recommending", "prevents a live trade", "what prevents you",
            "stops you from recommending", "before you can recommend", "before recommending a trade",
        )
        private val CANT_DO_PHRASES = setOf("what can't you do", "what cant you do", "what can you not do", "your limitations", "current limitations")
    }
}

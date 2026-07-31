package com.jarvis.os.app.core.intelligence.localintent

import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Offline Completion" milestone, requirement 4: a small, deliberately bundled-with-the-app
 * glossary of trading terms (the instrument JARVIS trades, plus the indicators its signal engine
 * is built on) -- answered from a fixed, reviewed explanation, never an AI provider, so these
 * definitions can never vary between runs or silently go stale-relative-to-training-data the way
 * an LLM's own recollection of "what is RSI" might.
 *
 * Deliberately NOT the same territory as [TidbLocalIntentHandler] -- "what is natural gas"
 * (a concept question) is answered here with a fixed explanation; "tell me about natural gas" or
 * "what's the price of natural gas" (a data question) is answered by TidbLocalIntentHandler with
 * real, current instrument data. [LocalServiceDomain.TIDB] is checked before
 * [LocalServiceDomain.KNOWLEDGE_BASE] specifically so a data-shaped question about the same
 * instrument never falls through to this generic glossary answer.
 *
 * Alias matching uses word boundaries (`\b`), not plain substring "contains" -- short aliases
 * like "ng" or "atr" would otherwise false-positive inside ordinary words ("tradiNG", "smART").
 */
@Singleton
class KnowledgeBaseLocalIntentHandler @Inject constructor() : LocalIntentHandler {

    override val domain = LocalServiceDomain.KNOWLEDGE_BASE

    override suspend fun tryHandle(text: String): LocalIntentAnswer? {
        val lower = text.lowercase()
        if (TRIGGER_PHRASES.none { it in lower }) return null

        val entry = GLOSSARY.firstOrNull { candidate ->
            candidate.aliases.any { alias -> wordBoundaryRegex(alias).containsMatchIn(lower) }
        } ?: return null

        return LocalIntentAnswer(entry.explanation)
    }

    private fun wordBoundaryRegex(alias: String): Regex = Regex("\\b${Regex.escape(alias)}\\b")

    private data class GlossaryEntry(val aliases: Set<String>, val explanation: String)

    companion object {
        private val TRIGGER_PHRASES = setOf("what is", "what's", "whats", "explain", "define", "what does", "meaning of")

        private val GLOSSARY = listOf(
            GlossaryEntry(
                aliases = setOf("natural gas", "naturalgas", "ng"),
                explanation = "Natural Gas is an energy commodity traded on MCX as a monthly futures contract, priced in INR per mmBtu " +
                    "(million British thermal units). Prices are driven mainly by weather, storage/inventory data, and seasonal " +
                    "heating/cooling demand, which is why it's typically far more volatile than most other MCX commodities.",
            ),
            GlossaryEntry(
                aliases = setOf("ema", "exponential moving average"),
                explanation = "EMA (Exponential Moving Average) is a moving average that weights recent prices more heavily than older " +
                    "ones, so it reacts faster to new price action than a Simple Moving Average. Commonly used to read short-term trend " +
                    "direction and as a dynamic support/resistance level.",
            ),
            GlossaryEntry(
                aliases = setOf("rsi", "relative strength index"),
                explanation = "RSI (Relative Strength Index) is a momentum oscillator, scaled 0-100, measuring the speed and size of " +
                    "recent price moves. Readings above 70 are typically read as overbought, below 30 as oversold -- though in a strong " +
                    "trend RSI can stay at extreme levels for a long stretch rather than immediately reversing.",
            ),
            GlossaryEntry(
                aliases = setOf("supertrend"),
                explanation = "Supertrend is a trend-following indicator built from Average True Range (ATR) bands plotted above or below " +
                    "price. It flips from acting as support to resistance (or back) whenever price closes across it, and is commonly used " +
                    "both to signal trend direction and as a trailing stop-loss level.",
            ),
            GlossaryEntry(
                aliases = setOf("atr", "average true range"),
                explanation = "ATR (Average True Range) measures market volatility by averaging the true range -- the greatest of " +
                    "high-low, high-previous close, or low-previous close -- over a set number of periods. It says nothing about " +
                    "direction, only how much an instrument typically moves, which is why it's often used to size stop-losses.",
            ),
        )
    }
}

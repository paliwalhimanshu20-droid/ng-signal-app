package com.jarvis.os.app.core.intelligence.localintent

import com.jarvis.os.app.core.trading.reasoning.TrustScoreCalculator
import com.jarvis.tidb.core.entity.InstrumentEntity
import com.jarvis.tidb.core.repository.InstrumentRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 4B Runtime Integration milestone -- Goal 2/3. Answers "What is your current trust
 * score?" and similar direct trust queries entirely from the REAL, already-built
 * [TrustScoreCalculator] (Phase 4B Slice 1) -- no new scoring logic, per this milestone's "only
 * integrate what already exists" constraint.
 *
 * ROOT CAUSE this handler fixes: before this milestone, no [LocalIntentHandler] and no branch of
 * [com.jarvis.os.app.core.JarvisCore.matchTradingInstrumentSymbol] recognized "trust score"
 * phrasing at all (that private function only matches "natural gas" / "should i buy" / "should i
 * sell" / "trade verdict" / "recommendation for" -- see its own declaration), so a direct trust
 * question fell all the way through to the generic AI-conversational branch with no repository
 * grounding whatsoever, producing exactly the "I remember we discussed..." style response this
 * milestone's brief calls out. This handler closes that specific gap.
 *
 * Instrument resolution mirrors [TidbLocalIntentHandler]'s own data-driven approach (match any
 * seeded instrument's symbol/displayName against the message) with the SAME hardcoded fallback
 * [com.jarvis.os.app.core.JarvisCore.matchTradingInstrumentSymbol] already uses when no
 * instrument is named (`NATURALGAS`) -- not a new convention, the existing one, reused.
 *
 * Phrasing per this milestone's explicit Goal 3 requirement: "No Trust Score has been
 * calculated." (not "we didn't establish one"), and a zero-scoring dimension is labeled
 * NOT_IMPLEMENTED only when the underlying engine genuinely doesn't exist in this repository
 * ([NOT_IMPLEMENTED_DIMENSIONS] -- BACKTESTS and PAPER_TRADING, confirmed by the same "no
 * execution-engine class found" search [com.jarvis.os.app.core.intelligence.selfawareness
 * .CapabilityInventory] itself relies on); every other dimension (HISTORICAL_DATA, INDICATORS,
 * OPTIMIZATION, LEARNING) has a real, working engine that has simply produced no data for this
 * instrument yet, which is labeled NO_DATA instead. An earlier version of this handler
 * cross-referenced [com.jarvis.os.app.core.intelligence.selfawareness.CapabilityInventory]'s
 * capability STATUS for this decision, which was wrong: that inventory reports Optimization as
 * MISSING whenever zero jobs have run (a data-state fact), not because the Optimization Engine
 * class is absent (it is not) -- conflating the two would have mislabeled a real, working engine
 * as NOT_IMPLEMENTED. Caught while writing this handler's own test.
 */
@Singleton
class TrustScoreLocalIntentHandler @Inject constructor(
    private val instruments: InstrumentRepository,
    private val trustScoreCalculator: TrustScoreCalculator,
) : LocalIntentHandler {

    override val domain = LocalServiceDomain.TRUST_SCORE

    override suspend fun tryHandle(text: String): LocalIntentAnswer? {
        val lower = text.trim().lowercase().trimEnd('!', '.', '?', ',')
        if (TRUST_KEYWORDS.none { it in lower }) return null

        val all = instruments.observeAll().first()
        if (all.isEmpty()) {
            return LocalIntentAnswer(
                "No Trust Score has been calculated. No instrument is seeded in the Trading Intelligence Database yet " +
                    "-- Trust Score is computed per-instrument and has nothing to score against.",
            )
        }
        val instrument = all.firstOrNull { inst -> lower.contains(inst.symbol.lowercase()) || lower.contains(inst.displayName.lowercase()) }
            ?: all.firstOrNull { it.symbol == DEFAULT_SYMBOL }
            ?: all.first()

        val assessment = trustScoreCalculator.assess(instrument.instrumentId, DEFAULT_TIMEFRAME)
        return LocalIntentAnswer(render(instrument, assessment))
    }

    private fun render(instrument: InstrumentEntity, assessment: TrustScoreCalculator.TrustAssessment): String {
        val header = "Trust Score for ${instrument.displayName} (${instrument.symbol}): ${"%.2f".format(assessment.overallScore)} " +
            "of ${"%.2f".format(TrustScoreCalculator.MINIMUM_TRUST_SCORE)} minimum -- meets minimum: ${assessment.meetsMinimum}."
        val dims = assessment.dimensions.joinToString(" ") { dimension ->
            val label = when {
                dimension.score > 0.0 -> "%.2f".format(dimension.score)
                dimension.name in NOT_IMPLEMENTED_DIMENSIONS -> "NOT_IMPLEMENTED"
                else -> "NO_DATA"
            }
            "${dimension.name}: $label (${dimension.detail})"
        }
        return "$header $dims"
    }

    companion object {
        private val TRUST_KEYWORDS = setOf(
            "trust score", "current trust", "how confident are you", "your confidence",
            "trust dimension", "trust breakdown", "how trustworthy",
        )
        private const val DEFAULT_SYMBOL = "NATURALGAS"
        private const val DEFAULT_TIMEFRAME = "1D"

        /** The only two [TrustScoreCalculator] dimensions backed by an engine that structurally does not exist in this repository yet -- see this class's own docstring for why every other dimension is NO_DATA, not NOT_IMPLEMENTED, when it scores zero. */
        private val NOT_IMPLEMENTED_DIMENSIONS = setOf(TrustScoreCalculator.BACKTESTS, TrustScoreCalculator.PAPER_TRADING)
    }
}

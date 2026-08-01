package com.jarvis.os.app.core.intelligence.localintent

import com.jarvis.tidb.analytics.repository.BacktestRepository
import com.jarvis.tidb.core.entity.InstrumentEntity
import com.jarvis.tidb.core.repository.InstrumentRepository
import com.jarvis.tidb.historical.indicator.entity.IndicatorType
import com.jarvis.tidb.optimization.entity.OptimizationJobStatus
import com.jarvis.tidb.optimization.repository.OptimizationRepository
import com.jarvis.tidb.optimization.searchspace.IndicatorComponentId
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Phase 3C, Section 1+2 -- Evidence Validation Engine + Hallucination Guard." The ONLY mechanism
 * in this codebase that gates trading-statistic-shaped questions before an AI provider ever sees
 * them. [STAT_KEYWORDS] is deliberately broad -- every term this phase's own Hallucination Guard
 * section says JARVIS must never fabricate (Sharpe, Sortino, Calmar, profit factor, win rate,
 * drawdown, annual return/CAGR, "best strategy", "optimization results", "backtest results",
 * "historical statistics"/rankings) -- because a broad match that sometimes fires on an
 * unrelated question is a far smaller cost than a narrow match that lets one fabrication-risk
 * question slip through to the AI path. ANY match here resolves [LocalIntentOutcome.LOCAL_ONLY]
 * unconditionally -- never NO_MATCH -- which is the actual guard: NO_MATCH is what lets a message
 * fall through to JarvisCore's AI-bound chain, so a statistic-shaped question that fell through
 * would defeat this class's entire purpose. Whether real evidence exists only changes WHICH
 * deterministic response comes back (see [tryHandle]), never WHETHER the AI path gets a turn at
 * this question at all.
 *
 * Queries the real, existing repositories -- [OptimizationRepository] (Phase 3B) and
 * [BacktestRepository] (already-existing analytics schema) -- rather than a parallel "evidence"
 * store, per this phase's "do NOT duplicate... reuse" rule. As of this phase, Module 5's backtest
 * simulator still doesn't exist (see the Phase 3 delivery notes), so in real usage today this
 * handler's honest "what's missing" path is what actually fires for most statistic questions --
 * that is the CORRECT behavior at this stage of the project, not a bug: there genuinely is no
 * evidence yet for most of these questions, and this class's entire job is to say so rather than
 * estimate.
 */
@Singleton
class EvidenceValidationLocalIntentHandler @Inject constructor(
    private val optimizationRepository: OptimizationRepository,
    private val backtestRepository: BacktestRepository,
    private val instruments: InstrumentRepository,
) : LocalIntentHandler {

    override val domain = LocalServiceDomain.EVIDENCE

    override suspend fun tryHandle(text: String): LocalIntentAnswer? {
        val lower = text.trim().lowercase()
        if (STAT_KEYWORDS.none { it in lower }) return null

        val mentionedIndicator = IndicatorType.entries.firstOrNull { type ->
            lower.contains(type.name.lowercase().replace('_', ' ')) || lower.contains(type.value.lowercase())
        }
        val mentionedInstrument = instruments.observeAll().first().firstOrNull { inst ->
            lower.contains(inst.displayName.lowercase()) || lower.contains(inst.symbol.lowercase())
        }

        optimizationEvidence(mentionedIndicator, mentionedInstrument)?.let { return LocalIntentAnswer(it) }
        backtestEvidence(mentionedInstrument)?.let { return LocalIntentAnswer(it) }

        return LocalIntentAnswer(noEvidenceExplanation(mentionedIndicator, mentionedInstrument))
    }

    /**
     * Real ranked optimization evidence, if any exists. Deliberately reports ONLY the winning
     * parameter combination itself -- never a win rate, Sharpe ratio, or any performance number --
     * because a ranked [com.jarvis.tidb.optimization.entity.OptimizationCombinationEntity] with a
     * null `backtestResultRowId` (the current reality for every job, since nothing evaluates
     * combinations yet) has real parameters but no real performance evidence behind it. Reporting
     * parameters as parameters, honestly, is not the same claim as reporting a performance number
     * that doesn't exist -- see this class's own docstring on why that distinction is the entire
     * point of a Hallucination Guard.
     */
    private suspend fun optimizationEvidence(indicator: IndicatorType?, instrument: InstrumentEntity?): String? {
        if (indicator == null) return null
        val componentId = IndicatorComponentId.of(indicator)
        val jobs = optimizationRepository.observeAllJobs().first().filter { job ->
            job.componentId == componentId && (instrument == null || job.instrumentId == instrument.instrumentId)
        }
        val completedJob = jobs.firstOrNull { it.statusValue == OptimizationJobStatus.COMPLETED.name } ?: return null
        val ranked = optimizationRepository.rankedCombinations(completedJob.rowId)
        if (ranked.isEmpty()) return null

        val best = ranked.first()
        return "The top-ranked parameter combination from optimization job ${completedJob.uuid.take(8)} " +
            "(${completedJob.totalCombinations} combinations tested via ${completedJob.algorithmId}) " +
            "for ${indicator.name} is: ${best.parametersJson}. " +
            "I don't have backtest performance numbers (win rate, Sharpe ratio, profit factor) linked to this " +
            "ranking yet -- that requires the backtest execution engine to actually evaluate each combination, " +
            "which isn't built yet. I'm giving you the real ranked parameters, not an invented performance claim."
    }

    /** Real backtest evidence, if any exists -- reports only fields [com.jarvis.tidb.analytics.entity.BacktestResultEntity] actually has stored, verbatim, never rounded/embellished/invented. */
    private suspend fun backtestEvidence(instrument: InstrumentEntity?): String? {
        val backtests = backtestRepository.observeAllBacktests().first()
        for (backtest in backtests) {
            if (instrument != null && !backtest.instrumentIdsCsv.split(",").contains(instrument.instrumentId.toString())) continue
            val results = backtestRepository.observeResultsByBacktest(backtest.rowId).first()
            val result = results.firstOrNull() ?: continue
            return "Backtest '${backtest.name}': win rate ${result.winRate}%, profit factor ${result.profitFactor}, " +
                "Sharpe ratio ${result.sharpeRatio}, max drawdown ${result.maxDrawdownPercent}%. " +
                "This is real, stored evidence from backtest run results -- not an estimate."
        }
        return null
    }

    private fun noEvidenceExplanation(indicator: IndicatorType?, instrument: InstrumentEntity?): String {
        val subject = listOfNotNull(indicator?.name, instrument?.displayName).joinToString(" on ").ifBlank { "that" }
        return "I don't have real backtest or optimization evidence for $subject yet. " +
            "What exists: the Trading Intelligence Database, the 26-indicator warehouse, and the optimization " +
            "search-space + Grid/Random Search engine can generate and rank real parameter combinations. " +
            "What's missing: a backtest execution engine that actually evaluates those combinations against " +
            "historical price data to produce real win rates, Sharpe ratios, and profit factors. " +
            "Until that exists and a job has actually run, I won't estimate a number -- I'd rather tell you " +
            "honestly that the evidence isn't there yet than guess."
    }

    companion object {
        /** Every term Phase 3C Section 2 explicitly says JARVIS must never fabricate, plus the broader question shapes ("best strategy", "X performance") that request one of them without naming it directly. */
        private val STAT_KEYWORDS = setOf(
            "sharpe", "sortino", "calmar", "profit factor", "win rate", "winning rate",
            "drawdown", "annual return", "cagr", "expectancy", "recovery factor",
            "best strategy", "best ema", "best rsi", "best macd", "best indicator",
            "most stable strategy", "optimization result", "optimization results",
            "backtest result", "backtest results", "historical statistic", "historical ranking",
            "historical rankings", "confidence score", "top ranked", "top-ranked",
            "performance of", "performance for",
        )
    }
}

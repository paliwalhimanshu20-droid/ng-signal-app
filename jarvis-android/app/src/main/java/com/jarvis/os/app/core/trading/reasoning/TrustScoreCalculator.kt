package com.jarvis.os.app.core.trading.reasoning

import com.jarvis.tidb.analytics.entity.PositionStatus
import com.jarvis.tidb.analytics.repository.BacktestRepository
import com.jarvis.tidb.analytics.repository.LearningRepository
import com.jarvis.tidb.analytics.repository.PortfolioRepository
import com.jarvis.tidb.historical.indicator.repository.IndicatorDefinitionRepository
import com.jarvis.tidb.historical.indicator.repository.IndicatorValueRepository
import com.jarvis.tidb.historical.quality.repository.QualityReportRepository
import com.jarvis.tidb.optimization.entity.OptimizationJobStatus
import com.jarvis.tidb.optimization.repository.OptimizationRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 4B, Section 1+7+8 — Trust Layer.
 *
 * Composes a Trust Score for one instrument from six dimensions named explicitly by Section 8:
 * Historical Data, Indicators, Optimization, Backtests, Learning, Paper Trading. This is
 * deliberately NOT the same number as the existing per-recommendation confidence score
 * ([com.jarvis.tidb.intelligence.confidence.entity.ConfidenceScoreEntity] scored against
 * [com.jarvis.tidb.intelligence.confidence.entity.ScoredEntityType.DECISION], composed in
 * [DecisionLifecycleRunner.composeConfidence]): confidence asks "how strongly does the evidence
 * this pass collected lean", Trust Score asks "how complete is the six-subsystem evidence base
 * behind this instrument, right now, system-wide". Persisted as a *second*
 * [com.jarvis.tidb.intelligence.confidence.entity.ConfidenceScoreEntity] row against the new
 * [com.jarvis.tidb.intelligence.confidence.entity.ScoredEntityType.TRUST_ASSESSMENT] type — see
 * that enum constant's own docstring for why this reuses the existing polymorphic scoring table
 * rather than adding a new one.
 *
 * EACH DIMENSION IS QUERIED FROM A REAL, EXISTING REPOSITORY — this class computes nothing that
 * isn't already stored somewhere:
 *  - HISTORICAL_DATA: [QualityReportRepository]'s latest candle quality report for the
 *    instrument/timeframe (`qualityScore`, already [0.0, 1.0]).
 *  - INDICATORS: fraction of active [IndicatorDefinitionRepository] definitions that have at
 *    least one stored [IndicatorValueRepository] reading for this instrument/timeframe.
 *  - OPTIMIZATION: best status found across [OptimizationRepository] jobs for this instrument —
 *    a completed job with ranked combinations scores highest, an incomplete job scores partial
 *    credit, no job at all scores zero.
 *  - BACKTESTS: whether any [BacktestRepository] backtest covering this instrument has a
 *    recorded result — same lookup [EvidenceValidationLocalIntentHandler] already uses.
 *  - LEARNING: average confidence of [LearningRepository] observations recorded for this
 *    instrument.
 *  - PAPER_TRADING: closed [PortfolioRepository] positions for this instrument under a
 *    non-live (`isLive = false`) portfolio, scaled toward 1.0 as a real paper track record
 *    accumulates.
 *
 * HONEST DEFAULT STATE, stated plainly rather than left implicit (matching
 * [EvidenceValidationLocalIntentHandler]'s own framing): as of this milestone, the backtest
 * execution engine and any scheduled optimization/paper-trading loop do not exist yet (see
 * Repository Reality Report). Most instruments will therefore score 0.0 on OPTIMIZATION,
 * BACKTESTS, and PAPER_TRADING today, and [assess] will correctly, honestly gate most
 * recommendations below [MINIMUM_TRUST_SCORE] — that is this milestone's fail-closed contract
 * working as designed (Rule 3, No Hallucinations), not a bug to work around by lowering the bar.
 *
 * EXTENSIBILITY NOTE (docs/architecture/JARVIS/JARVIS-005-Trust-Score-v2-Professional-Trading-
 * Trust-Framework.md): this is Trust Score v1. That spec records eight professional-grade
 * upgrades deliberately NOT built here — Calibration, Out-of-Sample Validation, Statistical
 * Significance, Regime Coverage, Contradicting Evidence, Recency Decay, Cost Adjustment, and
 * Portfolio Correlation. [TrustDimension.weight] and [TrustDimension.metadata] exist specifically
 * so that spec's frameworks can be integrated later without redesigning this class — see that
 * document's §11 for the verification this milestone performed against this exact code.
 */
@Singleton
class TrustScoreCalculator @Inject constructor(
    private val optimizationRepository: OptimizationRepository,
    private val backtestRepository: BacktestRepository,
    private val learningRepository: LearningRepository,
    private val portfolioRepository: PortfolioRepository,
    private val qualityReportRepository: QualityReportRepository,
    private val indicatorDefinitionRepository: IndicatorDefinitionRepository,
    private val indicatorValueRepository: IndicatorValueRepository,
) {

    /**
     * One named dimension's contribution to the overall Trust Score — [score] always in
     * [0.0, 1.0], always carrying an honest [detail] of what backs (or doesn't back) that score.
     *
     * [weight] and [metadata] are JARVIS-005 (Trust Score v2) extensibility hooks, added in the
     * same Phase 4B milestone that first built this class so no future redesign is needed:
     *  - [weight] defaults to [EQUAL_DIMENSION_WEIGHT] for all six of today's dimensions --
     *    `overallScore` in [assess] is bit-for-bit identical to a plain six-way average today.
     *    A future non-equal weighting (e.g. discounting Optimization until it's out-of-sample
     *    validated, per JARVIS-005 §4) only needs to pass `weight = ...` at the specific
     *    dimension call site(s) being changed -- every other dimension keeps inheriting the
     *    default untouched.
     *  - [metadata] is unused today (always empty) -- a documented landing spot for the
     *    structured sub-metrics JARVIS-005's frameworks need (sample size, holdout pass/fail,
     *    regime tags, calibration delta) that don't fit in a human-readable [detail] string.
     */
    data class TrustDimension(
        val name: String,
        val score: Double,
        val detail: String,
        val weight: Double = EQUAL_DIMENSION_WEIGHT,
        val metadata: Map<String, Double> = emptyMap(),
    )

    /**
     * The composed result. [meetsMinimum] is what [DecisionLifecycleRunner]'s VALIDATE stage
     * gates on. [explanation] follows Rule 3's exact shape: what evidence exists, what's
     * missing, and — where a concrete follow-up is known — which milestone generates it.
     */
    data class TrustAssessment(
        val instrumentId: Long,
        val overallScore: Double,
        val dimensions: List<TrustDimension>,
        val meetsMinimum: Boolean,
        val explanation: String,
    )

    suspend fun assess(instrumentId: Long, timeframe: String): TrustAssessment {
        val dimensions = listOf(
            historicalDataDimension(instrumentId, timeframe),
            indicatorDimension(instrumentId, timeframe),
            optimizationDimension(instrumentId),
            backtestDimension(instrumentId),
            learningDimension(instrumentId),
            paperTradingDimension(instrumentId),
        )
        // Weighted sum, not a plain average -- see TrustDimension.weight's own doc. Identical
        // result to a plain six-way average today since every dimension above inherits the same
        // EQUAL_DIMENSION_WEIGHT default; this is the seam JARVIS-005 non-equal weighting uses.
        val overall = dimensions.sumOf { it.score * it.weight }.coerceIn(0.0, 1.0)
        val meetsMinimum = overall >= MINIMUM_TRUST_SCORE
        return TrustAssessment(
            instrumentId = instrumentId,
            overallScore = overall,
            dimensions = dimensions,
            meetsMinimum = meetsMinimum,
            explanation = renderExplanation(overall, meetsMinimum, dimensions),
        )
    }

    private suspend fun historicalDataDimension(instrumentId: Long, timeframe: String): TrustDimension {
        val report = qualityReportRepository.getLatest(instrumentId, timeframe)
        return if (report == null) {
            TrustDimension(
                HISTORICAL_DATA,
                0.0,
                "No candle quality report exists yet for this instrument/timeframe — generated by the " +
                    "Historical Market Data Platform's quality engine once ingestion runs for it.",
            )
        } else {
            TrustDimension(
                HISTORICAL_DATA,
                report.qualityScore.coerceIn(0.0, 1.0),
                "Latest candle quality report scores ${"%.2f".format(report.qualityScore)} " +
                    "(${report.actualCandleCount}/${report.expectedCandleCount} candles present).",
            )
        }
    }

    private suspend fun indicatorDimension(instrumentId: Long, timeframe: String): TrustDimension {
        val activeDefinitions = indicatorDefinitionRepository.observeActive().first()
        if (activeDefinitions.isEmpty()) {
            return TrustDimension(INDICATORS, 0.0, "No active indicator definitions exist in the warehouse yet.")
        }
        val withData = activeDefinitions.count { definition ->
            indicatorValueRepository.getLatest(definition.indicatorDefId, instrumentId, timeframe, limit = 1).isNotEmpty()
        }
        val score = withData.toDouble() / activeDefinitions.size
        return TrustDimension(
            INDICATORS,
            score,
            "$withData of ${activeDefinitions.size} active indicator definitions have computed values for this instrument/timeframe.",
        )
    }

    private suspend fun optimizationDimension(instrumentId: Long): TrustDimension {
        val jobs = optimizationRepository.observeAllJobs().first().filter { it.instrumentId == instrumentId }
        if (jobs.isEmpty()) {
            return TrustDimension(
                OPTIMIZATION,
                0.0,
                "No optimization job has ever been created for this instrument — Section 5 (Massive " +
                    "Optimization Engine, this phase's next milestone) will schedule these automatically; " +
                    "today a job must be created manually.",
            )
        }
        val completed = jobs.filter { it.statusValue == OptimizationJobStatus.COMPLETED.name }
        for (job in completed) {
            if (optimizationRepository.rankedCombinations(job.rowId).isNotEmpty()) {
                return TrustDimension(
                    OPTIMIZATION, 1.0,
                    "Completed optimization job ${job.uuid.take(8)} has ${job.totalCombinations} ranked combinations.",
                )
            }
        }
        return if (completed.isNotEmpty()) {
            TrustDimension(OPTIMIZATION, 0.5, "A completed optimization job exists but its combinations aren't ranked yet.")
        } else {
            TrustDimension(
                OPTIMIZATION, 0.2,
                "${jobs.size} optimization job(s) exist for this instrument but none has completed yet.",
            )
        }
    }

    private suspend fun backtestDimension(instrumentId: Long): TrustDimension {
        val backtests = backtestRepository.observeAllBacktests().first()
        for (backtest in backtests) {
            if (!backtest.instrumentIdsCsv.split(",").contains(instrumentId.toString())) continue
            val results = backtestRepository.observeResultsByBacktest(backtest.rowId).first()
            if (results.isNotEmpty()) {
                return TrustDimension(BACKTESTS, 1.0, "Backtest '${backtest.name}' has a recorded result for this instrument.")
            }
        }
        return TrustDimension(
            BACKTESTS, 0.0,
            "No backtest result exists for this instrument yet — the backtest execution engine that " +
                "evaluates strategies against historical data isn't built yet (flagged since Phase 3C).",
        )
    }

    private suspend fun learningDimension(instrumentId: Long): TrustDimension {
        val observations = learningRepository.observeObservationsByInstrument(instrumentId).first()
        if (observations.isEmpty()) {
            return TrustDimension(
                LEARNING, 0.0,
                "No learning observations recorded yet for this instrument — generated by the Learning " +
                    "Framework once trades or backtests produce outcomes to learn from.",
            )
        }
        val avgConfidence = (observations.sumOf { it.confidence } / observations.size).coerceIn(0.0, 1.0)
        return TrustDimension(
            LEARNING, avgConfidence,
            "${observations.size} learning observation(s) recorded, averaging ${"%.2f".format(avgConfidence)} confidence.",
        )
    }

    private suspend fun paperTradingDimension(instrumentId: Long): TrustDimension {
        val nonLivePortfolioIds = portfolioRepository.observePortfolios().first()
            .filter { !it.isLive }
            .map { it.rowId }
            .toSet()
        if (nonLivePortfolioIds.isEmpty()) {
            return TrustDimension(
                PAPER_TRADING, 0.0,
                "No paper (non-live) portfolio exists yet — requires a portfolio with isLive=false and the " +
                    "autonomous paper-trading loop (Section 11, not yet built) to generate a track record.",
            )
        }
        val closedPositions = portfolioRepository.observePositionsByInstrument(instrumentId).first()
            .filter { it.portfolioRowId in nonLivePortfolioIds && it.status == PositionStatus.CLOSED }
        if (closedPositions.isEmpty()) {
            return TrustDimension(
                PAPER_TRADING, 0.0,
                "A paper portfolio exists but has no closed positions for this instrument yet.",
            )
        }
        // Scales toward full credit at 5 closed paper positions -- a deliberately simple first-pass
        // threshold, not a claimed statistical-significance methodology (same honesty framing as
        // DecisionLifecycleRunner.composeConfidence's own plain-average first pass).
        val score = (closedPositions.size / 5.0).coerceIn(0.0, 1.0)
        return TrustDimension(
            PAPER_TRADING, score,
            "${closedPositions.size} closed paper-trading position(s) recorded for this instrument.",
        )
    }

    private fun renderExplanation(overall: Double, meetsMinimum: Boolean, dimensions: List<TrustDimension>): String {
        val header = if (meetsMinimum) {
            "Trust Score ${"%.2f".format(overall)} meets the minimum (${"%.2f".format(MINIMUM_TRUST_SCORE)})."
        } else {
            "Trust Score ${"%.2f".format(overall)} is below the minimum (${"%.2f".format(MINIMUM_TRUST_SCORE)}) — " +
                "I won't issue a recommendation on this evidence base yet."
        }
        val breakdown = dimensions.joinToString("\n") { "- ${it.name}: ${"%.2f".format(it.score)} — ${it.detail}" }
        return "$header\n$breakdown"
    }

    companion object {
        const val HISTORICAL_DATA = "HISTORICAL_DATA"
        const val INDICATORS = "INDICATORS"
        const val OPTIMIZATION = "OPTIMIZATION"
        const val BACKTESTS = "BACKTESTS"
        const val LEARNING = "LEARNING"
        const val PAPER_TRADING = "PAPER_TRADING"

        /**
         * JARVIS-005 (Trust Score v2) extensibility hook — see [TrustDimension.weight]'s own doc.
         * Today, all six dimensions inherit this as [TrustDimension]'s default weight, so
         * [assess]'s weighted sum is bit-for-bit identical to a plain six-way average. Not a
         * `const val` fraction that changes per-dimension today; a future non-equal weighting
         * scheme names `weight = ...` explicitly at the specific dimension call site(s) it wants
         * to change instead of touching this shared default.
         */
        const val EQUAL_DIMENSION_WEIGHT = 1.0 / 6.0

        /**
         * First-pass, deliberately conservative default — six equally-weighted dimensions, so
         * clearing this requires real evidence in more than two of them on average. Documented
         * as tunable, not a claimed methodology, same honesty framing as every other first-pass
         * scoring constant in this codebase (see [DecisionLifecycleRunner.composeConfidence]).
         * Given today's real system state (no backtest execution engine, no scheduled
         * optimization/paper-trading loop — see this class's own doc), most instruments will
         * NOT clear this yet. That is the correct, honest, fail-closed behavior at this stage
         * of the project.
         */
        const val MINIMUM_TRUST_SCORE = 0.35
    }
}

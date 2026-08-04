package com.jarvis.os.app.core.intelligence.selfawareness

import com.jarvis.os.app.BuildConfig
import com.jarvis.os.app.core.trading.reasoning.TrustScoreCalculator
import com.jarvis.os.app.data.model.CapabilityStatus
import com.jarvis.os.app.data.model.ExecutiveReport
import com.jarvis.os.app.data.model.SystemCapabilityRecord
import com.jarvis.tidb.analytics.entity.BacktestResultEntity
import com.jarvis.tidb.analytics.repository.BacktestRepository
import com.jarvis.tidb.core.repository.InstrumentRepository
import com.jarvis.tidb.database.TradingIntelligenceDatabase
import com.jarvis.tidb.optimization.entity.OptimizationJobStatus
import com.jarvis.tidb.optimization.repository.OptimizationRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 4B Slice 2, Section 3 -- Executive Report Engine.
 *
 * "Replace generic AI summaries. Generate reports only from Capability Inventory, ... Repository
 * state." This class calls no [com.jarvis.os.app.core.chat.ChatProvider] and performs no model
 * completion of any kind -- every field of [ExecutiveReport] is deterministically assembled from
 * [SelfAwarenessEngine] / [CapabilityInventory], the exact same real state that engine's own
 * Mission Status answers read, so a report and a chat answer about the same subsystem can never
 * disagree with each other.
 *
 * Runtime Integration milestone, Goal 3: the report must show the real "Current Trust Score" and
 * "Recommendation status," not just the static Trust Layer capability row -- added here via the
 * same [TrustScoreCalculator] (Phase 4B Slice 1) [com.jarvis.os.app.core.intelligence.localintent
 * .TrustScoreLocalIntentHandler] already reuses, for the same default instrument
 * (`NATURALGAS`, falling back to the first seeded instrument -- see that handler's own docstring
 * for why this default, not a new one, is reused here).
 *
 * Deliberately distinct from [com.jarvis.os.app.core.intelligence.ExecutiveBriefingEngine]: that
 * class answers "what should the owner know this morning" from personal/owner-facing state
 * (ProjectOS, Watch Tower, notifications, approvals). This class answers "what state is the
 * JARVIS build itself in" from engineering/architecture state (capability completion, repository
 * health, Trust Layer). Both are deterministic-composition Engines in the same house style; they
 * are not a redesign of each other and this slice does not touch [ExecutiveBriefingEngine].
 */
@Singleton
class ExecutiveReportEngine @Inject constructor(
    private val selfAwareness: SelfAwarenessEngine,
    private val instruments: InstrumentRepository,
    private val trustScoreCalculator: TrustScoreCalculator,
    private val backtestRepository: BacktestRepository,
    private val optimizationRepository: OptimizationRepository,
) {
    suspend fun generate(): ExecutiveReport {
        val capabilities = selfAwareness.capabilities()
        val complete = capabilities.filter { it.status == CapabilityStatus.COMPLETE }
        val partial = capabilities.filter { it.status == CapabilityStatus.PARTIAL }
        val missing = capabilities.filter { it.status == CapabilityStatus.MISSING }

        val risks = capabilities.mapNotNull { record -> record.risk?.let { "${record.name}: $it" } }
        val recommendations = buildRecommendations(partial, missing)
        val nextMilestone = capabilities.firstOrNull { it.name == "Live Trading" }?.nextMilestone
            ?: "No next milestone recorded."

        return ExecutiveReport(
            generatedAtEpochMillis = Instant.now().toEpochMilli(),
            currentBuild = "JARVIS OS ${BuildConfig.VERSION_NAME} -- Phase 4B Slice 3",
            currentMilestone = "Backtest & Optimization Intelligence Engine (this slice)",
            completedWork = complete.map { it.name },
            partialWork = partial.map { "${it.name} (${it.completionPercent}%)" },
            missingWork = missing.map { it.name },
            currentRisks = risks,
            recommendations = recommendations,
            nextMilestone = nextMilestone,
            repositoryHealth = selfAwareness.repositoryStatus(),
            trustLayerSummary = trustLayerSummary(capabilities),
            backtestOptimizationSummary = backtestOptimizationSummary(),
        )
    }

    /** Goal 3: "Current Trust Score" + "Recommendation status" -- the real number, not just the static capability row's text. */
    private suspend fun trustLayerSummary(capabilities: List<SystemCapabilityRecord>): String {
        val capabilityRow = capabilities.firstOrNull { it.name.startsWith("Trust Layer") }
            ?.let { "${it.status}, ${it.completionPercent}% built -- ${it.verificationState}" }
            ?: "Trust Layer capability not found in inventory."

        val all = instruments.observeAll().first()
        if (all.isEmpty()) {
            return "$capabilityRow Current Trust Score: No Trust Score has been calculated -- no instrument is seeded yet."
        }
        val instrument = all.firstOrNull { it.symbol == DEFAULT_SYMBOL } ?: all.first()
        val assessment = trustScoreCalculator.assess(instrument.instrumentId, DEFAULT_TIMEFRAME)
        val recommendationStatus = if (assessment.meetsMinimum) {
            "clears the minimum -- eligible for a recommendation pass"
        } else {
            "below the minimum -- recommendations are blocked (fail-closed, by design)"
        }
        return "$capabilityRow Current Trust Score for ${instrument.displayName} (${instrument.symbol}): " +
            "${"%.2f".format(assessment.overallScore)} of ${"%.2f".format(TrustScoreCalculator.MINIMUM_TRUST_SCORE)} minimum. " +
            "Recommendation status: $recommendationStatus."
    }

    /**
     * Phase 4B Slice 3, Step 6: "Executive Reports should now include: Completed Backtests,
     * Optimization Jobs, Winning Strategy, Best Metrics, Evidence Summary, Updated Trust Score."
     * Trust Score is already covered by [trustLayerSummary]; every other clause here reads the
     * same [BacktestRepository]/[OptimizationRepository] state
     * [CapabilityInventory]'s own `optimizationEngine()`/`backtestExecutionEngine()` rows already
     * trust -- composed into one string, matching this class's existing single-string
     * [trustLayerSummary]/[SelfAwarenessEngine.repositoryStatus] shape, rather than five new
     * typed fields [render] would need bespoke handling for.
     *
     * "Winning Strategy"/"Best Metrics" come from the single best-ranked combination (rank 1, by
     * [com.jarvis.os.app.core.trading.optimization.CombinationRankingEngine]'s own definition)
     * found across every job that has at least one ranked combination, compared by Sharpe ratio --
     * never a generic aggregate across unranked or unrelated runs that could misrepresent which
     * specific run actually produced the number.
     */
    private suspend fun backtestOptimizationSummary(): String {
        val backtests = backtestRepository.observeAllBacktests().first()
        val completedBacktests = backtests.count { backtestRepository.observeResultsByBacktest(it.rowId).first().isNotEmpty() }

        val jobs = optimizationRepository.observeAllJobs().first()
        val completedJobs = jobs.count { it.statusValue == OptimizationJobStatus.COMPLETED.name }

        val best = bestRankedRun(jobs)
        val winningStrategyLine = if (best != null) {
            "Winning strategy: ${best.strategyId} -- Sharpe ${"%.2f".format(best.result.sharpeRatio ?: 0.0)}, " +
                "win rate ${"%.0f".format(best.result.winRate * 100)}%, net profit ${"%.2f".format(best.result.netProfit)}, " +
                "max drawdown ${"%.2f".format(best.result.maxDrawdownPercent)}%."
        } else {
            "Winning strategy: none yet -- no optimization job has both completed and been ranked " +
                "(see CombinationRankingEngine; ranking does not run automatically)."
        }

        return "Completed backtests: $completedBacktests of ${backtests.size} total. " +
            "Optimization jobs: $completedJobs of ${jobs.size} completed. $winningStrategyLine"
    }

    private data class BestRankedRun(val strategyId: String, val result: BacktestResultEntity)

    private suspend fun bestRankedRun(jobs: List<com.jarvis.tidb.optimization.entity.OptimizationJobEntity>): BestRankedRun? {
        var best: BestRankedRun? = null
        for (job in jobs) {
            val ranked = optimizationRepository.rankedCombinations(job.rowId)
            val top = ranked.firstOrNull { it.rank == 1 } ?: continue
            val runRowId = top.backtestRunRowId ?: continue
            val result = backtestRepository.getResultForRun(runRowId) ?: continue
            val candidateSharpe = result.sharpeRatio ?: Double.NEGATIVE_INFINITY
            val currentBestSharpe = best?.result?.sharpeRatio ?: Double.NEGATIVE_INFINITY
            if (best == null || candidateSharpe > currentBestSharpe) {
                best = BestRankedRun(job.componentId, result)
            }
        }
        return best
    }

    /**
     * Section 3: "Recommendations." One line per non-COMPLETE capability that has a real
     * [com.jarvis.os.app.data.model.SystemCapabilityRecord.nextMilestone] recorded -- never a
     * generic "keep working on it," always the specific next concrete step that capability's own
     * row already names, in the same dependency order [CapabilityInventory.snapshot] declares.
     */
    private fun buildRecommendations(
        partial: List<SystemCapabilityRecord>,
        missing: List<SystemCapabilityRecord>,
    ): List<String> = (missing + partial)
        .mapNotNull { record -> record.nextMilestone?.let { "${record.name}: $it" } }
        .distinct()

    /** Fixed-template rendering per Section 3/8 ("No generic AI text") -- plain text, chat-ready. */
    fun render(report: ExecutiveReport): String = buildString {
        appendLine("EXECUTIVE REPORT -- ${report.currentBuild}")
        appendLine("Current Milestone: ${report.currentMilestone}")
        appendLine()
        appendLine("Repository Health: ${report.repositoryHealth}")
        appendLine("Trust Layer: ${report.trustLayerSummary}")
        appendLine("Backtest & Optimization: ${report.backtestOptimizationSummary}")
        appendLine()
        appendLine("Completed (${report.completedWork.size}): ${report.completedWork.joinToString(", ").ifEmpty { "none" }}")
        appendLine("Partial (${report.partialWork.size}): ${report.partialWork.joinToString(", ").ifEmpty { "none" }}")
        appendLine("Missing (${report.missingWork.size}): ${report.missingWork.joinToString(", ").ifEmpty { "none" }}")
        appendLine()
        if (report.currentRisks.isNotEmpty()) {
            appendLine("Current Risks:")
            report.currentRisks.forEach { appendLine("- $it") }
            appendLine()
        }
        if (report.recommendations.isNotEmpty()) {
            appendLine("Recommendations:")
            report.recommendations.forEach { appendLine("- $it") }
            appendLine()
        }
        appendLine("Next Milestone: ${report.nextMilestone}")
    }.trim()

    companion object {
        private const val DEFAULT_SYMBOL = "NATURALGAS"
        private const val DEFAULT_TIMEFRAME = "1D"
    }
}

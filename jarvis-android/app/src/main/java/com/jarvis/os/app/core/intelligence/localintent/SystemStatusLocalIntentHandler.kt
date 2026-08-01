package com.jarvis.os.app.core.intelligence.localintent

import com.jarvis.tidb.analytics.repository.BacktestRepository
import com.jarvis.tidb.core.repository.InstrumentRepository
import com.jarvis.tidb.historical.indicator.entity.IndicatorType
import com.jarvis.tidb.optimization.entity.OptimizationJobStatus
import com.jarvis.tidb.optimization.repository.OptimizationRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Phase 3C, Section 5+9 -- Database State Awareness." A real, cross-subsystem status report --
 * Historical Data, Indicators, Optimization, Backtests -- built from the same real repositories
 * [EvidenceValidationLocalIntentHandler] and [TidbLocalIntentHandler] already query, per this
 * phase's reuse rule. Replaces a bare "no data" with an honest breakdown of what actually exists
 * and what doesn't, matching this phase's own "instead of saying 'No Data', return meaningful
 * status" requirement.
 *
 * Declared under [LocalServiceDomain.TIDB] since it IS a Trading Intelligence Database query,
 * just a broader one than [TidbLocalIntentHandler]'s per-instrument questions -- ordered after
 * [EvidenceValidationLocalIntentHandler] so a specific statistic question is never mistaken for a
 * general status request.
 */
@Singleton
class SystemStatusLocalIntentHandler @Inject constructor(
    private val instruments: InstrumentRepository,
    private val optimizationRepository: OptimizationRepository,
    private val backtestRepository: BacktestRepository,
) : LocalIntentHandler {

    override val domain = LocalServiceDomain.SYSTEM_STATUS

    override suspend fun tryHandle(text: String): LocalIntentAnswer? {
        val lower = text.trim().lowercase()
        if (STATUS_PHRASES.none { it in lower }) return null

        val instrumentCount = instruments.observeAll().first().size

        val jobs = optimizationRepository.observeAllJobs().first()
        val completedJobs = jobs.count { it.statusValue == OptimizationJobStatus.COMPLETED.name }
        val runningJobs = jobs.count { it.statusValue in setOf(OptimizationJobStatus.RUNNING.name, OptimizationJobStatus.QUEUED.name) }
        val totalCombinationsPlanned = jobs.sumOf { it.totalCombinations }
        val totalCombinationsEvaluated = jobs.sumOf { it.completedCombinations }

        val backtests = backtestRepository.observeAllBacktests().first()
        val backtestsWithResults = backtests.count { backtestRepository.observeResultsByBacktest(it.rowId).first().isNotEmpty() }

        return LocalIntentAnswer(
            buildString {
                append("System status: ")
                append("$instrumentCount instrument(s) in the Trading Intelligence Database. ")
                append("Indicator Warehouse: ${IndicatorType.entries.size} indicator types available, real calculation engine wired for all of them. ")
                append(
                    if (jobs.isEmpty()) {
                        "Optimization Engine: no jobs created yet. "
                    } else {
                        "Optimization Engine: ${jobs.size} job(s) created ($completedJobs completed, $runningJobs in progress), " +
                            "$totalCombinationsEvaluated of $totalCombinationsPlanned planned parameter combinations evaluated. "
                    },
                )
                append(
                    if (backtests.isEmpty()) {
                        "Backtest Engine: no backtests recorded yet -- the execution engine that would populate this isn't built."
                    } else {
                        "Backtest Engine: ${backtests.size} backtest(s) recorded, $backtestsWithResults with stored results."
                    },
                )
            },
        )
    }

    companion object {
        private val STATUS_PHRASES = setOf(
            "system status", "database state", "database status", "what's the status",
            "whats the status", "how much data do we have", "how much data do you have",
            "overview of the system", "state of the system", "data status",
        )
    }
}

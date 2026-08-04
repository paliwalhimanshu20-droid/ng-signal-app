package com.jarvis.os.app.core.trading.optimization

import com.jarvis.tidb.analytics.entity.BacktestResultEntity
import com.jarvis.tidb.analytics.repository.BacktestRepository
import com.jarvis.tidb.optimization.entity.OptimizationCombinationEntity
import com.jarvis.tidb.optimization.repository.OptimizationRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 4B Slice 3, Step 4 -- Ranking Engine.
 *
 * "Rank strategies using configurable metrics ... Ranking logic must remain extensible." One
 * exhaustive `when` over [RankingMetric] is the entire extension surface -- a new metric is one
 * enum entry plus one branch, matching this codebase's established "exhaustive `when` blocks"
 * discipline for `CoreEvent`-shaped extension points elsewhere in the app. Reuses
 * [OptimizationRepository.rankCombinations] (already built, Phase 3B) as the only write path --
 * this class computes an ordering, it never persists rank itself.
 */
enum class RankingMetric {
    SHARPE_RATIO,
    SORTINO_RATIO,
    PROFIT_FACTOR,
    CAGR,
    WIN_RATE,
    /** Lower is better for drawdown -- [metricScore] negates it so every metric shares one "higher score wins" ordering rule. */
    MAX_DRAWDOWN_PERCENT,
}

interface CombinationRankingEngine {
    suspend fun rankJob(jobRowId: Long, metric: RankingMetric = RankingMetric.SHARPE_RATIO): Int
}

@Singleton
class DefaultCombinationRankingEngine @Inject constructor(
    private val optimizationRepository: OptimizationRepository,
    private val backtestRepository: BacktestRepository,
) : CombinationRankingEngine {

    /** Returns the number of combinations actually ranked -- 0 if the job has no completed, result-bearing combinations yet (honest, not an error). */
    override suspend fun rankJob(jobRowId: Long, metric: RankingMetric): Int {
        val completed = optimizationRepository.completedCombinations(jobRowId)
        val scored = completed.mapNotNull { combination -> scoreOf(combination, metric)?.let { combination.rowId to it } }
        if (scored.isEmpty()) return 0

        val rankedRowIdsBestFirst = scored.sortedByDescending { it.second }.map { it.first }
        optimizationRepository.rankCombinations(jobRowId, rankedRowIdsBestFirst)
        return rankedRowIdsBestFirst.size
    }

    private suspend fun scoreOf(combination: OptimizationCombinationEntity, metric: RankingMetric): Double? {
        val runRowId = combination.backtestRunRowId ?: return null
        val result = backtestRepository.getResultForRun(runRowId) ?: return null
        return metricScore(result, metric)
    }

    /** Exhaustive by design -- see class doc. Every branch returns null (not a fabricated 0.0 or negative-infinity that would silently outrank a real, if poor, result) when the underlying [BacktestResultEntity] field is itself null, i.e. genuinely not computed for that run. */
    private fun metricScore(result: BacktestResultEntity, metric: RankingMetric): Double? = when (metric) {
        RankingMetric.SHARPE_RATIO -> result.sharpeRatio
        RankingMetric.SORTINO_RATIO -> result.sortinoRatio
        RankingMetric.PROFIT_FACTOR -> result.profitFactor
        RankingMetric.CAGR -> result.cagr
        RankingMetric.WIN_RATE -> result.winRate
        RankingMetric.MAX_DRAWDOWN_PERCENT -> -result.maxDrawdownPercent
    }
}

package com.jarvis.os.app.core.trading.optimization

import com.jarvis.os.app.core.trading.backtest.BacktestExecutionEngine
import com.jarvis.os.app.core.trading.backtest.BacktestExecutionResult
import com.jarvis.os.app.core.trading.backtest.BacktestRunRequest
import com.jarvis.tidb.core.entity.Timeframe
import com.jarvis.tidb.optimization.entity.OptimizationCombinationEntity
import com.jarvis.tidb.optimization.repository.OptimizationRepository
import com.jarvis.tidb.strategy.StrategyRegistry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 4B Slice 3, Step 3 -- Optimization Execution Engine.
 *
 * "Run optimization combinations. Execute each combination through the Backtest Engine. Persist
 * results. Call existing markCombinationEvaluated()/markCombinationFailed(). Do NOT duplicate
 * optimization persistence." This class adds no new table and no new DAO -- it is purely the
 * missing link between [OptimizationRepository.pendingCombinations] (already built, Phase 3B) and
 * [BacktestExecutionEngine] (this same slice's Step 2): for each pending combination it resolves
 * a [com.jarvis.tidb.strategy.StrategyDefinition] via [StrategyRegistry], runs it through
 * [BacktestExecutionEngine], and reports the outcome back through the exact same
 * `markCombinationEvaluated`/`markCombinationFailed` write path
 * [com.jarvis.tidb.optimization.repository.OptimizationRepositoryImpl] already exposed for
 * precisely this purpose.
 *
 * [OptimizationJobEntity.componentId][com.jarvis.tidb.optimization.entity.OptimizationJobEntity
 * .componentId] doubles as the strategy lookup key -- see [com.jarvis.tidb.strategy
 * .StrategyComponentId]'s own doc for why one namespaced string ("STRATEGY:EMA_CROSSOVER") is
 * both the [com.jarvis.tidb.optimization.searchspace.SearchSpaceRegistry] key and the
 * [StrategyRegistry] key, with no separate mapping table needed. A job whose componentId names
 * an indicator rather than a strategy (e.g. "INDICATOR:EMA") simply has no matching
 * [com.jarvis.tidb.strategy.StrategyProvider] -- every combination in that job is marked FAILED
 * with an honest reason rather than silently skipped, per this codebase's no-fake-success rule.
 */
interface OptimizationExecutionEngine {
    suspend fun runJob(jobRowId: Long): OptimizationExecutionSummary
}

data class OptimizationExecutionSummary(
    val jobRowId: Long,
    val evaluated: Int,
    val failed: Int,
)

@Singleton
class DefaultOptimizationExecutionEngine @Inject constructor(
    private val optimizationRepository: OptimizationRepository,
    private val backtestExecutionEngine: BacktestExecutionEngine,
    private val strategyRegistry: StrategyRegistry,
) : OptimizationExecutionEngine {

    override suspend fun runJob(jobRowId: Long): OptimizationExecutionSummary {
        val job = optimizationRepository.getJob(jobRowId)
            ?: throw IllegalStateException("No optimization job found for rowId $jobRowId.")

        optimizationRepository.markJobRunning(jobRowId)

        // Shared BacktestEntity across every combination in this job -- see
        // OptimizationCombinationEntity's own class doc for why this reuses BacktestRunEntity/
        // BacktestResultEntity by linking rather than duplicating. Populated from the FIRST
        // successful run and linked back onto the job row via OptimizationRepository.linkBacktest
        // (a minimal, honest extension of that interface -- see its own doc) so every later
        // combination in this job groups under the same BacktestEntity definition.
        var sharedBacktestRowId = job.backtestRowId

        var evaluated = 0
        var failed = 0

        val pending = optimizationRepository.pendingCombinations(jobRowId)
        for (combination in pending) {
            val strategy = strategyRegistry.forStrategy(job.componentId, parseParams(combination.parametersJson))
            if (strategy == null) {
                optimizationRepository.markCombinationFailed(
                    combination.rowId,
                    "No StrategyProvider registered for componentId '${job.componentId}' -- this optimization " +
                        "engine only evaluates strategy-shaped jobs, see this class's own doc.",
                )
                failed++
                continue
            }

            val request = BacktestRunRequest(
                name = "Optimization ${job.uuid.take(8)} -- combination #${combination.combinationIndex}",
                instrumentId = job.instrumentId,
                timeframe = Timeframe.from(job.timeframeValue),
                periodStart = job.periodStart,
                periodEnd = job.periodEnd,
                strategy = strategy,
                existingBacktestRowId = sharedBacktestRowId,
            )

            when (val result = backtestExecutionEngine.run(request)) {
                is BacktestExecutionResult.Success -> {
                    if (sharedBacktestRowId == null) {
                        sharedBacktestRowId = result.backtestRowId
                        optimizationRepository.linkBacktest(jobRowId, result.backtestRowId)
                    }
                    optimizationRepository.markCombinationEvaluated(combination.rowId, result.runRowId, result.resultRowId)
                    evaluated++
                }
                is BacktestExecutionResult.Failure -> {
                    optimizationRepository.markCombinationFailed(combination.rowId, "${result.stage}: ${result.reason}")
                    failed++
                }
            }
        }

        return OptimizationExecutionSummary(jobRowId, evaluated, failed)
    }

    /**
     * Inverse of [com.jarvis.tidb.optimization.repository.OptimizationRepositoryImpl.toJson] --
     * that method hand-builds `{"key":value,...}` rather than using `org.json.JSONObject` (see
     * its own doc for why); this is the matching hand-built parser, same reasoning, same
     * Android-platform-stub avoidance. [OptimizationCombinationEntity.parametersJson] is always
     * this exact simple, flat, non-nested shape -- produced by that one method -- so a small
     * dedicated parser is correct and sufficient; a general JSON library is not needed here any
     * more than it was needed to write the value.
     */
    private fun parseParams(json: String): Map<String, Double> {
        val body = json.trim().removePrefix("{").removeSuffix("}")
        if (body.isBlank()) return emptyMap()
        return body.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size != 2) return@mapNotNull null
            val key = parts[0].trim().trim('"')
            val value = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
            key to value
        }.toMap()
    }
}

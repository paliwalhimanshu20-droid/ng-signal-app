package com.jarvis.os.app.core.trading.backtest

import com.jarvis.os.app.core.workflow.WorkflowEngine
import com.jarvis.os.app.data.model.WorkflowDefinition
import com.jarvis.os.app.data.model.WorkflowStep
import com.jarvis.os.app.data.model.WorkflowStepStatus
import com.jarvis.tidb.analytics.entity.BacktestConfigurationEntity
import com.jarvis.tidb.analytics.entity.BacktestEntity
import com.jarvis.tidb.analytics.entity.BacktestResultEntity
import com.jarvis.tidb.analytics.entity.BacktestRunEntity
import com.jarvis.tidb.analytics.entity.BacktestStatus
import com.jarvis.tidb.analytics.entity.BacktestTradeEntity
import com.jarvis.tidb.analytics.entity.TradeCloseReason
import com.jarvis.tidb.analytics.entity.TradeDirection
import com.jarvis.tidb.analytics.repository.BacktestRepository
import com.jarvis.tidb.core.entity.HistoricalCandleEntity
import com.jarvis.tidb.core.entity.Timeframe
import com.jarvis.tidb.core.repository.HistoricalCandleRepository
import com.jarvis.tidb.core.repository.InstrumentRepository
import com.jarvis.tidb.historical.indicator.calc.IndicatorCalculatorRegistry
import com.jarvis.tidb.historical.indicator.calc.IndicatorPoint
import com.jarvis.tidb.strategy.BarContext
import com.jarvis.tidb.strategy.StrategyDefinition
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Phase 4B Slice 3, Step 2 -- Backtest Execution Engine.
 *
 * "Read historical candles -> Load required indicators -> Execute strategy -> Generate simulated
 * trades -> Calculate performance -> Persist" -- the exact five-stage responsibility list the
 * build prompt names, implemented as one [WorkflowEngine] run (same reuse the Repository Reality
 * Report flagged as a candidate, and the same idiom [com.jarvis.os.app.core.trading.reasoning
 * .DecisionLifecycleRunner] already established for a different pipeline).
 *
 * REUSE, NOT DUPLICATION -- every persisted row is written through the repository that already
 * owned its table before this class existed: [BacktestRepository] for
 * [BacktestEntity]/[BacktestConfigurationEntity]/[BacktestRunEntity]/[BacktestTradeEntity]/
 * [BacktestResultEntity], [HistoricalCandleRepository] for reading [HistoricalCandleEntity] rows,
 * [IndicatorCalculatorRegistry] for indicator math. This class adds zero new tables and zero new
 * DAOs -- it is purely the orchestration the Repository Reality Report found missing.
 *
 * AGNOSTIC BY CONSTRUCTION -- this class never references a specific instrument, indicator,
 * strategy, timeframe, or market: [BacktestRunRequest.instrumentId]/[BacktestRunRequest
 * .timeframe] are caller-supplied, and [BacktestRunRequest.strategy] is an already-resolved
 * [StrategyDefinition] the caller obtained from `StrategyRegistry` -- this engine only knows how
 * to execute whatever [StrategyDefinition] it's given against whatever candles exist for the
 * requested instrument/timeframe/period. A new instrument, indicator, or strategy never requires
 * a change here.
 *
 * NO FAKE SUCCESS -- any stage that cannot complete (no candles found, an indicator throws, a
 * persistence write fails) marks the [BacktestRunEntity] FAILED with a real `failureReason` and
 * returns [BacktestExecutionResult.Failure]; nothing partial is ever reported as a success.
 *
 * FIRST-PASS METHODOLOGY NOTE, stated plainly rather than left implicit (matching this
 * codebase's established honesty convention -- see [com.jarvis.os.app.core.trading.reasoning
 * .DecisionLifecycleRunner.composeConfidence]'s own doc): entries/exits fill at the signal bar's
 * close (not the next bar's open), stop/target checks use the same bar's high/low (a standard,
 * if optimistic, backtesting simplification), and no commission/slippage model is applied yet --
 * [BacktestConfigurationEntity.commissionModelJson]/`slippageModelJson` stay null, an honest
 * "not modeled yet" rather than a fabricated fixed cost. Sharpe/Sortino below are plain
 * mean-over-stddev of per-trade [BacktestTradeEntity.pnlPercent] returns, not annualized -- a
 * documented first pass, not a claimed professional risk methodology, same framing as every
 * other first-pass constant in this codebase.
 */
interface BacktestExecutionEngine {
    suspend fun run(request: BacktestRunRequest): BacktestExecutionResult
}

/**
 * [existingBacktestRowId], when supplied, reuses an already-created [BacktestEntity] (e.g. the
 * one an optimization job's combinations all share) instead of creating a new definition row per
 * run -- matching [com.jarvis.tidb.optimization.entity.OptimizationJobEntity.backtestRowId]'s own
 * documented reuse point.
 */
data class BacktestRunRequest(
    val name: String,
    val instrumentId: Long,
    val timeframe: Timeframe,
    val periodStart: Long,
    val periodEnd: Long,
    val strategy: StrategyDefinition,
    val initialCapital: Double = DEFAULT_INITIAL_CAPITAL,
    val existingBacktestRowId: Long? = null,
) {
    companion object {
        const val DEFAULT_INITIAL_CAPITAL = 100_000.0
    }
}

sealed class BacktestExecutionResult {
    data class Success(
        val backtestRowId: Long,
        val runRowId: Long,
        val resultRowId: Long,
        val result: BacktestResultEntity,
        val tradeCount: Int,
    ) : BacktestExecutionResult()

    /** [runRowId] is non-null once a [BacktestRunEntity] row exists to carry the failure -- null only if failure happened before that row could even be created (e.g. the instrument doesn't exist). */
    data class Failure(
        val backtestRowId: Long?,
        val runRowId: Long?,
        val stage: String,
        val reason: String,
    ) : BacktestExecutionResult()
}

@Singleton
class DefaultBacktestExecutionEngine @Inject constructor(
    private val workflowEngine: WorkflowEngine,
    private val backtestRepository: BacktestRepository,
    private val candleRepository: HistoricalCandleRepository,
    private val instrumentRepository: InstrumentRepository,
    private val indicatorCalculatorRegistry: IndicatorCalculatorRegistry,
) : BacktestExecutionEngine {

    override suspend fun run(request: BacktestRunRequest): BacktestExecutionResult {
        if (!instrumentRepository.exists(request.instrumentId)) {
            return BacktestExecutionResult.Failure(
                backtestRowId = null, runRowId = null, stage = "SETUP",
                reason = "Instrument ${request.instrumentId} does not exist.",
            )
        }

        val backtestRowId = request.existingBacktestRowId
            ?: backtestRepository.createBacktest(
                BacktestEntity(
                    name = request.name,
                    strategyId = request.strategy.strategyId,
                    periodStart = request.periodStart,
                    periodEnd = request.periodEnd,
                    instrumentIdsCsv = request.instrumentId.toString(),
                ),
            )

        backtestRepository.addConfiguration(
            BacktestConfigurationEntity(
                backtestRowId = backtestRowId,
                parametersJson = serializeStrategy(request.strategy),
                initialCapital = request.initialCapital,
                riskPerTradePercent = request.strategy.riskManagement.stopLossPercent,
            ),
        )

        val runRowId = backtestRepository.startRun(
            BacktestRunEntity(
                backtestRowId = backtestRowId,
                configurationRowId = null,
                status = BacktestStatus.RUNNING,
                startedAt = System.currentTimeMillis(),
                engineVersion = ENGINE_VERSION,
            ),
        )

        var candles: List<HistoricalCandleEntity> = emptyList()
        var seriesByAlias: Map<String, Map<Long, IndicatorPoint>> = emptyMap()
        var trades: List<BacktestTradeEntity> = emptyList()
        var result: BacktestResultEntity? = null
        var resultRowId: Long? = null

        val definition = WorkflowDefinition(
            workflowId = "backtest-run-$runRowId-${UUID.randomUUID()}",
            name = "Backtest Execution -- ${request.name}",
            steps = listOf(
                WorkflowStep(stepId = "load_candles", name = "READ HISTORICAL CANDLES", maxRetries = 1),
                WorkflowStep(stepId = "compute_indicators", name = "LOAD INDICATORS", dependsOn = setOf("load_candles")),
                WorkflowStep(stepId = "execute_strategy", name = "EXECUTE STRATEGY", dependsOn = setOf("compute_indicators")),
                WorkflowStep(stepId = "persist_results", name = "CALCULATE + PERSIST PERFORMANCE", dependsOn = setOf("execute_strategy")),
            ),
        )

        var failureDetail: String? = null

        val runRecord = workflowEngine.run(definition) { step ->
            when (step.stepId) {
                "load_candles" -> {
                    candles = candleRepository.observeRange(
                        request.instrumentId, request.timeframe, request.periodStart, request.periodEnd,
                    ).first()
                    if (candles.isEmpty()) {
                        failureDetail = "No historical candles found for instrument ${request.instrumentId}, " +
                            "timeframe ${request.timeframe}, period ${request.periodStart}..${request.periodEnd}."
                    }
                    candles.isNotEmpty()
                }
                "compute_indicators" -> {
                    seriesByAlias = request.strategy.indicators.associate { ref ->
                        val points = indicatorCalculatorRegistry.compute(ref.type, candles, ref.params)
                        ref.alias to points.associateBy { it.timestamp }
                    }
                    true
                }
                "execute_strategy" -> {
                    trades = simulate(runRowId, request, candles, seriesByAlias)
                    true
                }
                "persist_results" -> {
                    if (trades.isNotEmpty()) backtestRepository.recordGeneratedTrades(trades)
                    val computed = computeResult(runRowId, trades, request.initialCapital, request.periodStart, request.periodEnd)
                    resultRowId = backtestRepository.recordResult(computed)
                    result = computed
                    true
                }
                else -> false
            }
        }

        val persistedRun = backtestRepository.getRunWithDetails(runRowId)?.run

        return if (runRecord.succeeded && result != null && resultRowId != null) {
            if (persistedRun != null) {
                backtestRepository.updateRun(persistedRun.copy(status = BacktestStatus.COMPLETED, completedAt = System.currentTimeMillis()))
            }
            BacktestExecutionResult.Success(backtestRowId, runRowId, resultRowId!!, result!!, trades.size)
        } else {
            val failedStep = runRecord.history.lastOrNull { it.status == WorkflowStepStatus.FAILED }
            val reason = failureDetail ?: failedStep?.detail ?: "Backtest execution failed for an unknown reason."
            val stage = failedStep?.stepId ?: "UNKNOWN"
            if (persistedRun != null) {
                backtestRepository.updateRun(persistedRun.copy(status = BacktestStatus.FAILED, failureReason = reason))
            }
            BacktestExecutionResult.Failure(backtestRowId, runRowId, stage, reason)
        }
    }

    // ---- SIMULATION -------------------------------------------------------------------------

    private data class OpenPosition(
        val entryPrice: Double,
        val quantity: Double,
        val entryTimestamp: Long,
        val stopPrice: Double?,
        val targetPrice: Double?,
    )

    private fun simulate(
        runRowId: Long,
        request: BacktestRunRequest,
        candles: List<HistoricalCandleEntity>,
        seriesByAlias: Map<String, Map<Long, IndicatorPoint>>,
    ): List<BacktestTradeEntity> {
        val strategy = request.strategy
        val direction = strategy.direction
        val trades = mutableListOf<BacktestTradeEntity>()
        var availableCapital = request.initialCapital
        var openPosition: OpenPosition? = null
        var previousTimestamp: Long? = null

        for (candle in candles) {
            val context = BarContext(candle.timestamp, previousTimestamp, seriesByAlias)
            if (!context.isWarmedUp()) {
                previousTimestamp = candle.timestamp
                continue
            }

            val position = openPosition
            if (position == null) {
                if (strategy.entryRule.evaluate(context)) {
                    val entryPrice = candle.close
                    val quantity = strategy.positionSizing.quantityFor(availableCapital, entryPrice)
                    if (quantity > 0.0) {
                        val stopPrice = strategy.riskManagement.stopLossPercent?.let {
                            if (direction == TradeDirection.LONG) entryPrice * (1 - it / 100.0) else entryPrice * (1 + it / 100.0)
                        }
                        val targetPrice = strategy.riskManagement.targetPercent?.let {
                            if (direction == TradeDirection.LONG) entryPrice * (1 + it / 100.0) else entryPrice * (1 - it / 100.0)
                        }
                        openPosition = OpenPosition(entryPrice, quantity, candle.timestamp, stopPrice, targetPrice)
                    }
                }
            } else {
                val stopHit = position.stopPrice?.let { sp ->
                    if (direction == TradeDirection.LONG) candle.low <= sp else candle.high >= sp
                } ?: false
                val targetHit = position.targetPrice?.let { tp ->
                    if (direction == TradeDirection.LONG) candle.high >= tp else candle.low <= tp
                } ?: false

                val (exitPrice, closeReason) = when {
                    stopHit -> position.stopPrice!! to TradeCloseReason.STOP_LOSS_HIT
                    targetHit -> position.targetPrice!! to TradeCloseReason.TARGET_HIT
                    // MANUAL_EXIT is the closest existing TradeCloseReason to "the strategy's own
                    // exit rule fired" -- no dedicated enum value exists for a rule-driven exit,
                    // and adding one is a schema change out of this engine's scope; documented
                    // here so a future reader isn't misled into thinking a human intervened.
                    strategy.exitRule.evaluate(context) -> candle.close to TradeCloseReason.MANUAL_EXIT
                    else -> null to null
                }

                if (exitPrice != null && closeReason != null) {
                    trades += closeTrade(runRowId, request.instrumentId, direction, position, candle.timestamp, exitPrice, closeReason)
                    availableCapital += trades.last().netPnl
                    openPosition = null
                }
            }
            previousTimestamp = candle.timestamp
        }

        // Force-close a still-open position at the last candle -- an honest TIME_EXIT, not a
        // fabricated stop/target/rule exit that never actually happened.
        val lastCandle = candles.lastOrNull()
        val finalPosition = openPosition
        if (finalPosition != null && lastCandle != null) {
            trades += closeTrade(runRowId, request.instrumentId, direction, finalPosition, lastCandle.timestamp, lastCandle.close, TradeCloseReason.TIME_EXIT)
        }

        return trades
    }

    private fun closeTrade(
        runRowId: Long,
        instrumentId: Long,
        direction: TradeDirection,
        position: OpenPosition,
        exitTimestamp: Long,
        exitPrice: Double,
        closeReason: TradeCloseReason,
    ): BacktestTradeEntity {
        val grossPnl = if (direction == TradeDirection.LONG) {
            (exitPrice - position.entryPrice) * position.quantity
        } else {
            (position.entryPrice - exitPrice) * position.quantity
        }
        // No commission/slippage model yet (see class doc) -- net equals gross honestly, not a fabricated deduction.
        val netPnl = grossPnl
        val notional = position.entryPrice * position.quantity
        val pnlPercent = if (notional != 0.0) (netPnl / notional) * 100.0 else 0.0

        return BacktestTradeEntity(
            runRowId = runRowId,
            instrumentId = instrumentId,
            direction = direction,
            entryPrice = position.entryPrice,
            exitPrice = exitPrice,
            quantity = position.quantity,
            entryTimestamp = position.entryTimestamp,
            exitTimestamp = exitTimestamp,
            closeReason = closeReason,
            grossPnl = grossPnl,
            netPnl = netPnl,
            pnlPercent = pnlPercent,
        )
    }

    // ---- PERFORMANCE --------------------------------------------------------------------------

    private fun computeResult(
        runRowId: Long,
        trades: List<BacktestTradeEntity>,
        startingCapital: Double,
        periodStart: Long,
        periodEnd: Long,
    ): BacktestResultEntity {
        val totalTrades = trades.size
        val winners = trades.filter { it.netPnl > 0.0 }
        val losers = trades.filter { it.netPnl < 0.0 }
        val netProfit = trades.sumOf { it.netPnl }
        val endingCapital = startingCapital + netProfit
        val winRate = if (totalTrades > 0) winners.size.toDouble() / totalTrades else 0.0

        val grossWins = winners.sumOf { it.netPnl }
        val grossLossesAbs = losers.sumOf { -it.netPnl }
        val profitFactor = if (grossLossesAbs > 0.0) grossWins / grossLossesAbs else null
        val averageWin = if (winners.isNotEmpty()) winners.map { it.netPnl }.average() else null
        val averageLoss = if (losers.isNotEmpty()) losers.map { it.netPnl }.average() else null
        val largestWin = winners.maxOfOrNull { it.netPnl }
        val largestLoss = losers.minOfOrNull { it.netPnl }
        val expectancy = if (totalTrades > 0) netProfit / totalTrades else null

        val (maxConsecutiveWins, maxConsecutiveLosses) = consecutiveStreaks(trades)
        val (maxDrawdown, maxDrawdownPercent) = drawdown(trades, startingCapital)
        val returns = trades.map { it.pnlPercent }
        val sharpe = sharpeRatio(returns)
        val sortino = sortinoRatio(returns)
        val cagrValue = cagr(startingCapital, endingCapital, periodStart, periodEnd)

        return BacktestResultEntity(
            runRowId = runRowId,
            totalTrades = totalTrades,
            winningTrades = winners.size,
            losingTrades = losers.size,
            netProfit = netProfit,
            winRate = winRate,
            maxDrawdown = maxDrawdown,
            maxDrawdownPercent = maxDrawdownPercent,
            sharpeRatio = sharpe,
            sortinoRatio = sortino,
            profitFactor = profitFactor,
            expectancy = expectancy,
            averageWin = averageWin,
            averageLoss = averageLoss,
            largestWin = largestWin,
            largestLoss = largestLoss,
            maxConsecutiveWins = maxConsecutiveWins,
            maxConsecutiveLosses = maxConsecutiveLosses,
            startingCapital = startingCapital,
            endingCapital = endingCapital,
            cagr = cagrValue,
        )
    }

    private fun consecutiveStreaks(trades: List<BacktestTradeEntity>): Pair<Int, Int> {
        var maxWins = 0; var maxLosses = 0
        var currentWins = 0; var currentLosses = 0
        for (trade in trades) {
            if (trade.netPnl > 0.0) {
                currentWins++; currentLosses = 0
            } else if (trade.netPnl < 0.0) {
                currentLosses++; currentWins = 0
            } else {
                currentWins = 0; currentLosses = 0
            }
            maxWins = maxOf(maxWins, currentWins)
            maxLosses = maxOf(maxLosses, currentLosses)
        }
        return maxWins to maxLosses
    }

    private fun drawdown(trades: List<BacktestTradeEntity>, startingCapital: Double): Pair<Double, Double> {
        var equity = startingCapital
        var peak = startingCapital
        var maxDrawdown = 0.0
        var maxDrawdownPercent = 0.0
        for (trade in trades) {
            equity += trade.netPnl
            if (equity > peak) peak = equity
            val dd = peak - equity
            if (dd > maxDrawdown) maxDrawdown = dd
            if (peak > 0.0) {
                val ddPercent = (dd / peak) * 100.0
                if (ddPercent > maxDrawdownPercent) maxDrawdownPercent = ddPercent
            }
        }
        return maxDrawdown to maxDrawdownPercent
    }

    /** Plain mean-over-stddev of per-trade percent returns -- not annualized. See class doc's "FIRST-PASS METHODOLOGY NOTE". Null (honest, not fabricated) below two trades or when returns have zero variance. */
    private fun sharpeRatio(returns: List<Double>): Double? {
        if (returns.size < 2) return null
        val mean = returns.average()
        val variance = returns.sumOf { (it - mean).pow(2) } / returns.size
        val stdDev = sqrt(variance)
        return if (stdDev == 0.0) null else mean / stdDev
    }

    /** Same first-pass framing as [sharpeRatio], but the denominator is downside deviation (negative returns only). Null when there are no negative returns to measure against, rather than a fabricated zero. */
    private fun sortinoRatio(returns: List<Double>): Double? {
        if (returns.size < 2) return null
        val mean = returns.average()
        val downside = returns.filter { it < 0.0 }
        if (downside.isEmpty()) return null
        val downsideVariance = downside.sumOf { it.pow(2) } / downside.size
        val downsideDeviation = sqrt(downsideVariance)
        return if (downsideDeviation == 0.0) null else mean / downsideDeviation
    }

    private fun cagr(startingCapital: Double, endingCapital: Double, periodStart: Long, periodEnd: Long): Double? {
        val days = (periodEnd - periodStart) / MILLIS_PER_DAY
        if (days <= 0.0 || startingCapital <= 0.0 || endingCapital <= 0.0) return null
        return (endingCapital / startingCapital).pow(365.0 / days) - 1.0
    }

    /**
     * Hand-built, not `org.json.JSONObject` -- same reasoning as [com.jarvis.tidb.optimization
     * .repository.OptimizationRepositoryImpl.toJson]'s own doc: `JSONObject` is an
     * Android-platform stub in this project's plain-JVM unit test environment and silently
     * returns null under `isReturnDefaultValues = true`, which a non-null `String` return type
     * then turns into an NPE at the call site rather than inside any real business logic. This
     * strategy shape has no nesting/escaping/array cases `JSONObject` would actually be needed
     * for, so a small hand-built serializer is correct in both environments, not just on-device.
     */
    private fun serializeStrategy(strategy: StrategyDefinition): String {
        val indicatorsJson = strategy.indicators.joinToString(prefix = "[", postfix = "]") { ref ->
            val paramsJson = ref.params.entries.joinToString(prefix = "{", postfix = "}") { (k, v) -> "\"$k\":$v" }
            "{\"alias\":\"${ref.alias}\",\"type\":\"${ref.type.value}\",\"params\":$paramsJson}"
        }
        val stopLoss = strategy.riskManagement.stopLossPercent?.toString() ?: "null"
        val target = strategy.riskManagement.targetPercent?.toString() ?: "null"
        return "{\"strategyId\":\"${strategy.strategyId}\",\"direction\":\"${strategy.direction}\"," +
            "\"indicators\":$indicatorsJson,\"stopLossPercent\":$stopLoss,\"targetPercent\":$target}"
    }

    companion object {
        private const val ENGINE_VERSION = "jarvis-backtest-engine-1.0"
        private const val MILLIS_PER_DAY = 86_400_000.0
    }
}

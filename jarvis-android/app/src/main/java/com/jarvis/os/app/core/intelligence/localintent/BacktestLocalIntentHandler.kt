package com.jarvis.os.app.core.intelligence.localintent

import com.jarvis.os.app.core.trading.backtest.BacktestExecutionEngine
import com.jarvis.os.app.core.trading.backtest.BacktestExecutionResult
import com.jarvis.os.app.core.trading.backtest.BacktestRunRequest
import com.jarvis.tidb.core.entity.InstrumentEntity
import com.jarvis.tidb.core.entity.Timeframe
import com.jarvis.tidb.core.repository.InstrumentRepository
import com.jarvis.tidb.strategy.StrategyComponentId
import com.jarvis.tidb.strategy.StrategyRegistry
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 4B Slice 3 -- "Manual trigger for first validation." Chat phrasing like "run a backtest"
 * or "backtest naturalgas" runs [EmaCrossoverStrategyProvider][com.jarvis.tidb.strategy
 * .EmaCrossoverStrategyProvider] through [BacktestExecutionEngine] directly against the named (or
 * default) instrument's last year of stored `historical_candles` on the daily timeframe -- the
 * smallest concrete way to prove Steps 1+2 end to end from the same chat surface every other
 * local capability already answers from, without needing a scheduler, a UI screen, or a
 * Deployment Center action, none of which this milestone builds.
 *
 * Instrument resolution mirrors [TrustScoreLocalIntentHandler]'s own data-driven approach (match
 * any seeded instrument's symbol/displayName against the message, falling back to the same
 * hardcoded `NATURALGAS` default [com.jarvis.os.app.core.JarvisCore.matchTradingInstrumentSymbol]
 * already uses) -- not a new convention, the existing one, reused a third time.
 *
 * This handler calls the real engine and reports its real result, success or failure, verbatim --
 * per this codebase's no-fake-success rule, a FAILED run is rendered as a failure, never
 * reinterpreted as a softer "still working on it."
 */
@Singleton
class BacktestLocalIntentHandler @Inject constructor(
    private val instruments: InstrumentRepository,
    private val backtestExecutionEngine: BacktestExecutionEngine,
    private val strategyRegistry: StrategyRegistry,
) : LocalIntentHandler {

    override val domain = LocalServiceDomain.BACKTEST

    override suspend fun tryHandle(text: String): LocalIntentAnswer? {
        val lower = text.trim().lowercase().trimEnd('!', '.', '?', ',')
        if (KEYWORDS.none { it in lower }) return null

        val all = instruments.observeAll().first()
        if (all.isEmpty()) {
            return LocalIntentAnswer(
                "Can't run a backtest -- no instrument is seeded in the Trading Intelligence Database yet.",
            )
        }
        val instrument = all.firstOrNull { inst -> lower.contains(inst.symbol.lowercase()) || lower.contains(inst.displayName.lowercase()) }
            ?: all.firstOrNull { it.symbol == DEFAULT_SYMBOL }
            ?: all.first()

        val strategy = strategyRegistry.forStrategy(VALIDATION_STRATEGY_ID)
            ?: return LocalIntentAnswer(
                "Can't run a backtest -- no StrategyProvider is registered for '$VALIDATION_STRATEGY_ID'. " +
                    "This should never happen if StrategyModule is wired correctly.",
            )

        val now = System.currentTimeMillis()
        val request = BacktestRunRequest(
            name = "Manual validation backtest -- ${instrument.symbol}",
            instrumentId = instrument.instrumentId,
            timeframe = DEFAULT_TIMEFRAME,
            periodStart = now - DEFAULT_LOOKBACK_MILLIS,
            periodEnd = now,
            strategy = strategy,
        )

        return when (val result = backtestExecutionEngine.run(request)) {
            is BacktestExecutionResult.Success -> LocalIntentAnswer(render(instrument, result))
            is BacktestExecutionResult.Failure -> LocalIntentAnswer(
                "Backtest for ${instrument.displayName} (${instrument.symbol}) failed at stage ${result.stage}: ${result.reason}",
            )
        }
    }

    private fun render(instrument: InstrumentEntity, success: BacktestExecutionResult.Success): String {
        val r = success.result
        val sharpe = r.sharpeRatio?.let { ", Sharpe ${"%.2f".format(it)}" } ?: ", Sharpe: not enough trades to compute"
        return "Backtest complete for ${instrument.displayName} (${instrument.symbol}), strategy $VALIDATION_STRATEGY_ID: " +
            "${success.tradeCount} trade(s), net profit ${"%.2f".format(r.netProfit)}, " +
            "win rate ${"%.0f".format(r.winRate * 100)}%, max drawdown ${"%.2f".format(r.maxDrawdownPercent)}%$sharpe. " +
            "(backtestRowId=${success.backtestRowId}, runRowId=${success.runRowId}, resultRowId=${success.resultRowId})"
    }

    companion object {
        private val KEYWORDS = setOf(
            "run a backtest", "run backtest", "start a backtest", "execute a backtest", "execute backtest",
        )
        private const val DEFAULT_SYMBOL = "NATURALGAS"
        private val DEFAULT_TIMEFRAME = Timeframe.D1
        private val VALIDATION_STRATEGY_ID = StrategyComponentId.of("EMA_CROSSOVER")
        private const val DEFAULT_LOOKBACK_MILLIS = 365L * 24 * 60 * 60 * 1000
    }
}

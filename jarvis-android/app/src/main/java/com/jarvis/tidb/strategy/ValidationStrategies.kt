package com.jarvis.tidb.strategy

import com.jarvis.tidb.analytics.entity.TradeDirection
import com.jarvis.tidb.historical.indicator.entity.IndicatorType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 4B Slice 3, Step 1: "the first implementation may execute a simple EMA crossover
 * strategy ONLY as a validation strategy." This class exists to prove the [StrategyProvider] /
 * `BacktestExecutionEngine` / `OptimizationExecutionEngine` pipeline end to end for one concrete
 * case -- it is explicitly NOT the reason [StrategyModel.kt][StrategyDefinition] exists, and
 * adding a second strategy never touches this class or that framework, only adds a sibling
 * implementing [StrategyProvider] the same way this one does.
 *
 * Long-only ([TradeDirection.LONG]): a short-side variant is a trivial future sibling
 * ([StrategyDefinition.direction] already exists for exactly that) -- deliberately not added
 * here since nothing in this milestone needs it yet, the same "don't register a dimension
 * nothing reads" caution [com.jarvis.tidb.optimization.searchspace.IndicatorSearchSpaces]'s own
 * class doc already applies to indicator params, applied here to strategy variants instead.
 */
@Singleton
class EmaCrossoverStrategyProvider @Inject constructor() : StrategyProvider {

    override val strategyId: String = StrategyComponentId.of("EMA_CROSSOVER")

    override fun define(params: Map<String, Double>): StrategyDefinition {
        // Defensive ordering, not fabrication: GridSearchAlgorithm/RandomSearchAlgorithm (Phase 3)
        // sweep fastPeriod/slowPeriod independently -- this framework has no cross-parameter
        // constraint mechanism yet (see EmaCrossoverSearchSpaceProvider's own doc for why). If a
        // generated combination puts the "fast" period at or above the "slow" period, this swaps
        // them so the strategy still runs a real, meaningful EMA pair rather than a degenerate
        // one. The combination's own persisted parametersJson is untouched; only the resolved
        // StrategyDefinition used for THIS run reflects the swap.
        val rawFast = params[FAST_PERIOD_KEY] ?: DEFAULT_FAST_PERIOD
        val rawSlow = params[SLOW_PERIOD_KEY] ?: DEFAULT_SLOW_PERIOD
        val fastPeriod = minOf(rawFast, rawSlow)
        val slowPeriod = maxOf(rawFast, rawSlow).let { if (it == fastPeriod) it + 1.0 else it }

        return StrategyDefinition(
            strategyId = strategyId,
            direction = TradeDirection.LONG,
            indicators = listOf(
                IndicatorReference(alias = FAST_EMA_ALIAS, type = IndicatorType.EMA, params = mapOf("period" to fastPeriod)),
                IndicatorReference(alias = SLOW_EMA_ALIAS, type = IndicatorType.EMA, params = mapOf("period" to slowPeriod)),
            ),
            entryRule = RuleCondition.CrossesAbove(fastAlias = FAST_EMA_ALIAS, slowAlias = SLOW_EMA_ALIAS),
            exitRule = RuleCondition.CrossesBelow(fastAlias = FAST_EMA_ALIAS, slowAlias = SLOW_EMA_ALIAS),
            positionSizing = PositionSizing.FixedFractionalCapital(fraction = DEFAULT_CAPITAL_FRACTION),
            riskManagement = RiskManagement(stopLossPercent = DEFAULT_STOP_LOSS_PERCENT, targetPercent = DEFAULT_TARGET_PERCENT),
        )
    }

    companion object {
        const val FAST_PERIOD_KEY = "fastPeriod"
        const val SLOW_PERIOD_KEY = "slowPeriod"
        private const val FAST_EMA_ALIAS = "fastEma"
        private const val SLOW_EMA_ALIAS = "slowEma"
        private const val DEFAULT_FAST_PERIOD = 10.0
        private const val DEFAULT_SLOW_PERIOD = 30.0

        /**
         * First-pass, deliberately simple defaults -- same honesty framing as every other
         * first-pass constant in this codebase (see [com.jarvis.os.app.core.trading.reasoning
         * .TrustScoreCalculator]'s `MINIMUM_TRUST_SCORE` doc). Not a claimed risk methodology.
         */
        private const val DEFAULT_CAPITAL_FRACTION = 0.1
        private const val DEFAULT_STOP_LOSS_PERCENT = 2.0
        private const val DEFAULT_TARGET_PERCENT = 4.0
    }
}

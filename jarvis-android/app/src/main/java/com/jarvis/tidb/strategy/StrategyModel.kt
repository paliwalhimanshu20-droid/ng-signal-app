package com.jarvis.tidb.strategy

import com.jarvis.tidb.analytics.entity.TradeDirection
import com.jarvis.tidb.historical.indicator.calc.IndicatorPoint
import com.jarvis.tidb.historical.indicator.entity.IndicatorType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 4B Slice 3, Step 1 -- Universal Strategy Model.
 *
 * Per the build prompt's non-negotiable architecture rules ("Instrument Agnostic, Strategy
 * Agnostic, Indicator Agnostic, Timeframe Agnostic, Market Agnostic"), nothing in this file names
 * an instrument, a specific indicator, or a specific strategy -- [StrategyDefinition] is a pure
 * data shape any concrete strategy assembles itself from. This is the same "framework + swap-point
 * registry" pattern already established twice in this codebase --
 * [com.jarvis.tidb.optimization.searchspace.SearchSpace]/[com.jarvis.tidb.optimization.searchspace
 * .SearchSpaceRegistry] and [com.jarvis.tidb.historical.indicator.calc.IndicatorCalculatorRegistry]
 * -- reused a third time here rather than reinvented. Adding a new strategy is (1) implement
 * [StrategyProvider], (2) add one `@Binds @IntoSet` line in [com.jarvis.tidb.di.StrategyModule] --
 * this file never needs to change.
 */

/**
 * One indicator a strategy reads, computed once per backtest run and referenced by [alias] from
 * every [RuleCondition] below -- not the raw [IndicatorType] name, so two references to the same
 * indicator type with different periods (e.g. a fast and a slow EMA) don't collide.
 */
data class IndicatorReference(
    val alias: String,
    val type: IndicatorType,
    val params: Map<String, Double> = emptyMap(),
)

/**
 * The read-only per-bar view [RuleCondition.evaluate] sees. Indicator series are keyed by candle
 * timestamp rather than list position -- warmup-requiring indicators (see
 * [com.jarvis.tidb.historical.indicator.calc.IndicatorCalculator]'s own docstring) produce a
 * shorter series than the candle list, so timestamp lookup is the only correct way to line up
 * "this candle's indicator value" without every rule re-deriving a warmup offset itself.
 */
class BarContext(
    private val currentTimestamp: Long,
    private val previousTimestamp: Long?,
    private val seriesByAlias: Map<String, Map<Long, IndicatorPoint>>,
) {
    fun valueNow(alias: String, component: Int = 1): Double? =
        seriesByAlias[alias]?.get(currentTimestamp)?.let { pointComponent(it, component) }

    fun valuePrevious(alias: String, component: Int = 1): Double? =
        previousTimestamp?.let { ts -> seriesByAlias[alias]?.get(ts)?.let { pointComponent(it, component) } }

    /** True only once every alias this context was built with has a real value for this bar (and the previous bar, when one exists) -- the honest "warmup complete" gate [BacktestExecutionEngine] checks before evaluating any rule against a bar. */
    fun isWarmedUp(): Boolean = seriesByAlias.keys.all { alias ->
        valueNow(alias) != null && (previousTimestamp == null || valuePrevious(alias) != null)
    }

    private fun pointComponent(point: IndicatorPoint, index: Int): Double? = when (index) {
        1 -> point.value1
        2 -> point.value2
        3 -> point.value3
        4 -> point.value4
        else -> null
    }
}

/**
 * Entry/exit logic. Deliberately the same "sealed class of composable primitives" shape as
 * [com.jarvis.tidb.optimization.searchspace.ParameterSpec] -- [CrossesAbove]/[CrossesBelow]/
 * [Above]/[Below] are the four this milestone's validation strategy needs; [All]/[Any] compose
 * them into arbitrarily complex conditions without this sealed class itself ever changing.
 * Momentum-, volume-, or pattern-based conditions are future variants of this exact same type.
 */
sealed class RuleCondition {
    abstract fun evaluate(context: BarContext): Boolean

    data class CrossesAbove(val fastAlias: String, val slowAlias: String) : RuleCondition() {
        override fun evaluate(context: BarContext): Boolean {
            val fastNow = context.valueNow(fastAlias) ?: return false
            val slowNow = context.valueNow(slowAlias) ?: return false
            val fastPrev = context.valuePrevious(fastAlias) ?: return false
            val slowPrev = context.valuePrevious(slowAlias) ?: return false
            return fastPrev <= slowPrev && fastNow > slowNow
        }
    }

    data class CrossesBelow(val fastAlias: String, val slowAlias: String) : RuleCondition() {
        override fun evaluate(context: BarContext): Boolean {
            val fastNow = context.valueNow(fastAlias) ?: return false
            val slowNow = context.valueNow(slowAlias) ?: return false
            val fastPrev = context.valuePrevious(fastAlias) ?: return false
            val slowPrev = context.valuePrevious(slowAlias) ?: return false
            return fastPrev >= slowPrev && fastNow < slowNow
        }
    }

    data class Above(val alias: String, val threshold: Double) : RuleCondition() {
        override fun evaluate(context: BarContext): Boolean {
            val value = context.valueNow(alias) ?: return false
            return value > threshold
        }
    }

    data class Below(val alias: String, val threshold: Double) : RuleCondition() {
        override fun evaluate(context: BarContext): Boolean {
            val value = context.valueNow(alias) ?: return false
            return value < threshold
        }
    }

    data class All(val conditions: List<RuleCondition>) : RuleCondition() {
        override fun evaluate(context: BarContext): Boolean = conditions.all { it.evaluate(context) }
    }

    data class Any(val conditions: List<RuleCondition>) : RuleCondition() {
        override fun evaluate(context: BarContext): Boolean = conditions.any { it.evaluate(context) }
    }
}

/**
 * How many units to buy/sell for one entry. Same sealed-primitive shape as [RuleCondition] -- a
 * Kelly-fraction or volatility-scaled variant is a future implementer of this same type, never a
 * reason to change [StrategyDefinition] or [BacktestExecutionEngine].
 */
sealed class PositionSizing {
    abstract fun quantityFor(availableCapital: Double, entryPrice: Double): Double

    data class FixedQuantity(val quantity: Double) : PositionSizing() {
        override fun quantityFor(availableCapital: Double, entryPrice: Double): Double = quantity
    }

    data class FixedFractionalCapital(val fraction: Double) : PositionSizing() {
        init {
            require(fraction > 0.0 && fraction <= 1.0) { "FixedFractionalCapital fraction must be in (0, 1], was $fraction." }
        }
        override fun quantityFor(availableCapital: Double, entryPrice: Double): Double =
            if (entryPrice <= 0.0) 0.0 else (availableCapital * fraction) / entryPrice
    }
}

/**
 * Percent-of-entry-price stop loss / target, direction-applied by [BacktestExecutionEngine]
 * (below entry for a LONG stop, above entry for a SHORT stop, and vice versa for target). Both
 * nullable -- a strategy may define neither, either, or both; the engine only forces an exit on
 * the ones actually set, never a fabricated default.
 */
data class RiskManagement(
    val stopLossPercent: Double? = null,
    val targetPercent: Double? = null,
)

/**
 * One complete, executable strategy -- instrument-, timeframe-, and market-agnostic by
 * construction: nothing here references any of those, they're supplied separately by whatever
 * request runs this definition (see `BacktestRunRequest`).
 *
 * [strategyId] is a namespaced identifier -- see [StrategyComponentId] -- matching
 * [com.jarvis.tidb.optimization.entity.OptimizationJobEntity.componentId]'s own convention
 * exactly, so an optimization job's componentId doubles as the strategy lookup key with no
 * separate mapping table needed.
 */
data class StrategyDefinition(
    val strategyId: String,
    val direction: TradeDirection,
    val indicators: List<IndicatorReference>,
    val entryRule: RuleCondition,
    val exitRule: RuleCondition,
    val positionSizing: PositionSizing,
    val riskManagement: RiskManagement = RiskManagement(),
)

/**
 * Namespaced strategy identifier convention -- "STRATEGY:EMA_CROSSOVER", matching
 * [com.jarvis.tidb.optimization.searchspace.IndicatorComponentId]'s "INDICATOR:EMA" shape -- so a
 * strategy id never collides with an indicator componentId or another strategy's id in the same
 * [com.jarvis.tidb.optimization.searchspace.SearchSpaceRegistry].
 *
 * FUTURE MILESTONE EXTENSION POINT (Intelligence Validation Framework, historical -- not built
 * here, per that milestone's own "do not implement yet" instruction): a future proprietary
 * JARVIS Intelligence Score is, structurally, just another [StrategyProvider] -- it reads
 * [HistoricalCandleEntity][com.jarvis.tidb.core.entity.HistoricalCandleEntity] the same way
 * [EmaCrossoverStrategyProvider] does, runs through the exact same [BacktestExecutionEngine]
 * unchanged, and its [BacktestResultEntity][com.jarvis.tidb.analytics.entity.BacktestResultEntity]
 * is directly comparable to a traditional indicator's own strategy-wrapped result via
 * [CombinationRankingEngine]'s existing [RankingMetric] machinery -- "benchmarked against
 * traditional indicators" falls out of running both through this same pipeline and comparing
 * their persisted results, not a new comparison engine. Nothing in this file, [StrategyRegistry],
 * [BacktestExecutionEngine], or [CombinationRankingEngine] needs to change for that milestone to
 * plug in; it only needs its own [StrategyProvider] implementation(s) and DI bindings, the same
 * one-class-plus-one-line pattern every other extension in this codebase already follows.
 */
object StrategyComponentId {
    fun of(name: String): String = "STRATEGY:$name"
}

/**
 * One implementation per strategy. [define] accepts an optional parameter override map -- the
 * same shape [com.jarvis.tidb.optimization.entity.OptimizationCombinationEntity.parametersJson]
 * already produces -- so an optimization execution engine can parameterize a strategy per
 * combination without this interface or [StrategyRegistry] needing any awareness of optimization.
 */
interface StrategyProvider {
    val strategyId: String
    fun define(params: Map<String, Double> = emptyMap()): StrategyDefinition
}

/**
 * Discovers every bound [StrategyProvider] by [StrategyProvider.strategyId] -- identical
 * `@Binds @IntoSet` multibinding shape as [com.jarvis.tidb.optimization.searchspace
 * .SearchSpaceRegistry] and [com.jarvis.tidb.historical.indicator.calc.IndicatorCalculatorRegistry].
 * A new strategy is (1) implement [StrategyProvider], (2) add one `@Binds @IntoSet` line in
 * [com.jarvis.tidb.di.StrategyModule] -- this class never needs to change.
 */
@Singleton
class StrategyRegistry @Inject constructor(
    providers: Set<@JvmSuppressWildcards StrategyProvider>,
) {
    private val byId: Map<String, StrategyProvider> = providers.associateBy { it.strategyId }

    fun forStrategy(strategyId: String, params: Map<String, Double> = emptyMap()): StrategyDefinition? =
        byId[strategyId]?.define(params)

    val registeredStrategyIds: Set<String> get() = byId.keys
}

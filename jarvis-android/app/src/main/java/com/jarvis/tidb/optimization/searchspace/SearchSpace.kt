package com.jarvis.tidb.optimization.searchspace

import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Universal Parameter Optimization Engine" (Module 3). One dimension of a [SearchSpace].
 * Deliberately two variants only, both numeric -- every real parameter [com.jarvis.tidb.historical.
 * indicator.calc.IndicatorCalculator] implementations actually read is a number (a period, a
 * multiplier, a threshold), so this framework's first extension ([IndicatorSearchSpaces]) has
 * something genuine to wire into. A categorical variant (e.g. "moving average type") is a natural
 * future addition, but only once a real consumer reads a categorical param -- adding one now,
 * with nothing behind it, would be exactly the kind of unused, decorative parameter this project
 * has been explicit about not wanting.
 */
sealed class ParameterSpec {
    abstract val key: String

    /** A bounded numeric range, swept in steps of [step] (inclusive of [max] where it lands exactly on a step). E.g. EMA's period: min=2, max=300, step=1. */
    data class ContinuousRange(override val key: String, val min: Double, val max: Double, val step: Double) : ParameterSpec()

    /** A fixed, explicit set of numeric values -- for parameters that don't vary smoothly (e.g. Parabolic SAR's acceleration factor is conventionally tuned over a small explicit set, not a fine-grained sweep). */
    data class DiscreteChoices(override val key: String, val choices: List<Double>) : ParameterSpec()
}

/** One optimizable component's full set of tunable dimensions. [componentId] is a stable, namespaced identifier (see [IndicatorComponentId]) -- not the raw indicator/rule name -- so Module 4's future non-indicator components (entry rules, exit rules, filters) can share this same registry without colliding on names. */
data class SearchSpace(val componentId: String, val parameters: List<ParameterSpec>)

/**
 * One implementation per optimizable component. This is the literal mechanism behind Module 3's
 * "every indicator must define a parameter search space" and Module 10's "all optimization
 * variables must register through the same generic search-space framework" -- a component
 * (an indicator today; an entry rule, exit rule, filter, or position-sizing method once Module 4
 * gives them somewhere to attach) contributes its own [SearchSpace] and this interface, the
 * registry below, and every [com.jarvis.tidb.optimization.algorithm.OptimizationAlgorithm] never
 * need to change to support it.
 */
interface SearchSpaceProvider {
    val componentId: String
    fun searchSpace(): SearchSpace
}

/**
 * Discovers every bound [SearchSpaceProvider] by [SearchSpaceProvider.componentId] -- same
 * `@Binds @IntoSet` swap-point pattern as
 * [com.jarvis.tidb.historical.indicator.calc.IndicatorCalculatorRegistry],
 * [com.jarvis.os.app.core.chat.ChatProvider]/AiRouter, and
 * [com.jarvis.os.app.core.intelligence.localintent.LocalIntentRouter] elsewhere in this codebase.
 * A new optimizable component is (1) implement [SearchSpaceProvider], (2) add one
 * `@Binds @IntoSet` line -- this class never needs to change.
 */
@Singleton
class SearchSpaceRegistry @Inject constructor(
    providers: Set<@JvmSuppressWildcards SearchSpaceProvider>,
) {
    private val byComponent: Map<String, SearchSpaceProvider> = providers.associateBy { it.componentId }

    fun forComponent(componentId: String): SearchSpace? = byComponent[componentId]?.searchSpace()

    val registeredComponentIds: Set<String> get() = byComponent.keys
}

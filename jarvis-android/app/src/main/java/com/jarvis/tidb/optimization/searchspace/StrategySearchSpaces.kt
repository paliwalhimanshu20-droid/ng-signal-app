package com.jarvis.tidb.optimization.searchspace

import com.jarvis.tidb.strategy.EmaCrossoverStrategyProvider
import com.jarvis.tidb.strategy.StrategyComponentId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 4B Slice 3, Step 3: the search space for [EmaCrossoverStrategyProvider], letting
 * [com.jarvis.tidb.optimization.repository.OptimizationRepository.createJob] sweep its two
 * periods the exact same way [IndicatorSearchSpaces] already sweeps every indicator's own
 * parameters -- registered under the same componentId ([StrategyComponentId.of] "EMA_CROSSOVER")
 * [EmaCrossoverStrategyProvider.strategyId] uses, so one string is both the strategy lookup key
 * and the optimization componentId, with no separate mapping table needed.
 *
 * KNOWN LIMITATION, stated honestly rather than hidden: [com.jarvis.tidb.optimization.algorithm
 * .GridSearchAlgorithm] / [com.jarvis.tidb.optimization.algorithm.RandomSearchAlgorithm] sweep
 * every [ParameterSpec] independently -- this framework has no cross-parameter constraint
 * mechanism (e.g. "fastPeriod must be less than slowPeriod") as of Phase 3. Some generated
 * combinations will therefore ask for a fast period at or above the slow period;
 * [EmaCrossoverStrategyProvider.define] defensively reorders those two values per-run rather than
 * either crashing or silently producing a meaningless backtest. A real constraint mechanism is a
 * future Phase 3 extension, not something this single strategy's search space should invent on
 * its own.
 */
@Singleton
class EmaCrossoverSearchSpaceProvider @Inject constructor() : SearchSpaceProvider {
    override val componentId: String = StrategyComponentId.of("EMA_CROSSOVER")

    override fun searchSpace(): SearchSpace = SearchSpace(
        componentId,
        listOf(
            ParameterSpec.ContinuousRange(EmaCrossoverStrategyProvider.FAST_PERIOD_KEY, 2.0, 50.0, 1.0),
            ParameterSpec.ContinuousRange(EmaCrossoverStrategyProvider.SLOW_PERIOD_KEY, 10.0, 200.0, 5.0),
        ),
    )
}

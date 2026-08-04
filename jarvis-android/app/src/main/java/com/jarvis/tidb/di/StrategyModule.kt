package com.jarvis.tidb.di

import com.jarvis.tidb.strategy.EmaCrossoverStrategyProvider
import com.jarvis.tidb.strategy.StrategyProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Phase 4B Slice 3, Step 1: the swap point [com.jarvis.tidb.strategy.StrategyRegistry] promises --
 * same `@Binds @IntoSet` shape as [OptimizationModule] / [IndicatorCalculatorModule]. A new
 * strategy is one implementation class plus one line here; this module never needs a redesign.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class StrategyModule {

    @Binds
    @IntoSet
    abstract fun bindEmaCrossoverStrategyProvider(impl: EmaCrossoverStrategyProvider): StrategyProvider
}

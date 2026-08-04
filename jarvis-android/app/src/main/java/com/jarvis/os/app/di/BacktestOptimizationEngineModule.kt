package com.jarvis.os.app.di

import com.jarvis.os.app.core.trading.backtest.BacktestExecutionEngine
import com.jarvis.os.app.core.trading.backtest.DefaultBacktestExecutionEngine
import com.jarvis.os.app.core.trading.optimization.CombinationRankingEngine
import com.jarvis.os.app.core.trading.optimization.DefaultCombinationRankingEngine
import com.jarvis.os.app.core.trading.optimization.DefaultOptimizationExecutionEngine
import com.jarvis.os.app.core.trading.optimization.OptimizationExecutionEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Phase 4B Slice 3 -- binds [BacktestExecutionEngine], [OptimizationExecutionEngine], and
 * [CombinationRankingEngine] to their one real implementation each, matching
 * [TradingIntelligenceModule]'s existing `@Binds` pattern for [com.jarvis.os.app.core.trading
 * .TradingIntelligenceOrchestrator] exactly -- kept as its own module for the same reason that
 * one is: a `core.trading.*` concern, not a `data.repository.*` one, and small enough not to
 * belong inside [TradingIntelligenceModule] itself (Step 2/3/4 engines, not Step 3's Layer 3
 * orchestrator).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BacktestOptimizationEngineModule {

    @Binds
    @Singleton
    abstract fun bindBacktestExecutionEngine(impl: DefaultBacktestExecutionEngine): BacktestExecutionEngine

    @Binds
    @Singleton
    abstract fun bindOptimizationExecutionEngine(impl: DefaultOptimizationExecutionEngine): OptimizationExecutionEngine

    @Binds
    @Singleton
    abstract fun bindCombinationRankingEngine(impl: DefaultCombinationRankingEngine): CombinationRankingEngine
}

package com.jarvis.os.app.di

import com.jarvis.os.app.core.trading.DefaultTradingIntelligenceOrchestrator
import com.jarvis.os.app.core.trading.TradingIntelligenceOrchestrator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * JARVIS-002 Layer 3: binds [TradingIntelligenceOrchestrator] to its one real implementation,
 * matching [RepositoryModule]'s existing `@Binds` pattern for every other interface/impl pair
 * in this app -- kept as its own small module rather than added to `RepositoryModule` since it's
 * a `core.trading.*` concern, not a `data.repository.*` one.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TradingIntelligenceModule {

    @Binds
    @Singleton
    abstract fun bindTradingIntelligenceOrchestrator(impl: DefaultTradingIntelligenceOrchestrator): TradingIntelligenceOrchestrator
}

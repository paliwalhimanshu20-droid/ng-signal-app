package com.jarvis.tidb.di

import android.content.Context
import com.jarvis.tidb.database.TradingIntelligenceDatabase
import com.jarvis.tidb.optimization.dao.OptimizationCombinationDao
import com.jarvis.tidb.optimization.dao.OptimizationJobDao
import com.jarvis.tidb.optimization.repository.OptimizationRepository
import com.jarvis.tidb.optimization.repository.OptimizationRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * "Phase 3B, Section 1 -- Optimization Persistence." Deliberately a NEW, separate module rather
 * than an addition to `TidbModule.kt` -- that file hand-rolls its own `@Volatile`-cached
 * singleton lifecycle for every existing TIDB repository, and is both the largest and highest
 * blast-radius file in this package. [TradingIntelligenceDatabase.getInstance] is already a
 * thread-safe, double-checked-locking singleton factory (see its own companion object) --
 * calling it a second time from here is safe by construction and returns the exact same instance
 * `TidbModule.kt` uses, so this module can provide this phase's two new DAOs and its repository
 * through Hilt's standard `@Provides`/`@Binds` without editing that file at all.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class OptimizationPersistenceModule {

    @Binds
    abstract fun bindOptimizationRepository(impl: OptimizationRepositoryImpl): OptimizationRepository

    companion object {
        @Provides
        @Singleton
        fun provideTradingIntelligenceDatabase(@ApplicationContext context: Context): TradingIntelligenceDatabase =
            TradingIntelligenceDatabase.getInstance(context)

        @Provides
        fun provideOptimizationJobDao(db: TradingIntelligenceDatabase): OptimizationJobDao = db.optimizationJobDao()

        @Provides
        fun provideOptimizationCombinationDao(db: TradingIntelligenceDatabase): OptimizationCombinationDao = db.optimizationCombinationDao()
    }
}

package com.jarvis.tidb.analytics

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jarvis.tidb.analytics.dao.BacktestConfigurationDao
import com.jarvis.tidb.analytics.dao.BacktestDao
import com.jarvis.tidb.analytics.dao.BacktestResultDao
import com.jarvis.tidb.analytics.dao.BacktestRunDao
import com.jarvis.tidb.analytics.dao.BacktestTradeDao
import com.jarvis.tidb.analytics.dao.CapitalMovementDao
import com.jarvis.tidb.analytics.dao.DecisionExplanationDao
import com.jarvis.tidb.analytics.dao.DecisionRecordDao
import com.jarvis.tidb.analytics.dao.FailureAnalysisDao
import com.jarvis.tidb.analytics.dao.InstrumentPerformanceDao
import com.jarvis.tidb.analytics.dao.LearningInsightDao
import com.jarvis.tidb.analytics.dao.LearningObservationDao
import com.jarvis.tidb.analytics.dao.LessonLearnedDao
import com.jarvis.tidb.analytics.dao.MonthlyPerformanceDao
import com.jarvis.tidb.analytics.dao.OptimizationSuggestionDao
import com.jarvis.tidb.analytics.dao.PatternDiscoveryDao
import com.jarvis.tidb.analytics.dao.PerformanceMetricDao
import com.jarvis.tidb.analytics.dao.PerformanceSnapshotDao
import com.jarvis.tidb.analytics.dao.PortfolioAllocationDao
import com.jarvis.tidb.analytics.dao.PortfolioDao
import com.jarvis.tidb.analytics.dao.PortfolioPositionDao
import com.jarvis.tidb.analytics.dao.PortfolioRiskDao
import com.jarvis.tidb.analytics.dao.StrategyPerformanceDao
import com.jarvis.tidb.analytics.dao.TradeDao
import com.jarvis.tidb.analytics.dao.TradeExecutionDao
import com.jarvis.tidb.analytics.dao.TradeExitDao
import com.jarvis.tidb.analytics.dao.TradeFeesDao
import com.jarvis.tidb.analytics.dao.TradeJournalDao
import com.jarvis.tidb.analytics.dao.TradingTimelineEventDao
import com.jarvis.tidb.analytics.entity.BacktestConfigurationEntity
import com.jarvis.tidb.analytics.entity.BacktestEntity
import com.jarvis.tidb.analytics.entity.BacktestResultEntity
import com.jarvis.tidb.analytics.entity.BacktestRunEntity
import com.jarvis.tidb.analytics.entity.BacktestTradeEntity
import com.jarvis.tidb.analytics.entity.CapitalMovementEntity
import com.jarvis.tidb.analytics.entity.DecisionExplanationEntity
import com.jarvis.tidb.analytics.entity.DecisionRecordEntity
import com.jarvis.tidb.analytics.entity.FailureAnalysisEntity
import com.jarvis.tidb.analytics.entity.InstrumentPerformanceEntity
import com.jarvis.tidb.analytics.entity.LearningInsightEntity
import com.jarvis.tidb.analytics.entity.LearningObservationEntity
import com.jarvis.tidb.analytics.entity.LessonLearnedEntity
import com.jarvis.tidb.analytics.entity.MonthlyPerformanceEntity
import com.jarvis.tidb.analytics.entity.OptimizationSuggestionEntity
import com.jarvis.tidb.analytics.entity.PatternDiscoveryEntity
import com.jarvis.tidb.analytics.entity.PerformanceMetricEntity
import com.jarvis.tidb.analytics.entity.PerformanceSnapshotEntity
import com.jarvis.tidb.analytics.entity.PortfolioAllocationEntity
import com.jarvis.tidb.analytics.entity.PortfolioEntity
import com.jarvis.tidb.analytics.entity.PortfolioPositionEntity
import com.jarvis.tidb.analytics.entity.PortfolioRiskEntity
import com.jarvis.tidb.analytics.entity.StrategyPerformanceEntity
import com.jarvis.tidb.analytics.entity.TradeEntity
import com.jarvis.tidb.analytics.entity.TradeExecutionEntity
import com.jarvis.tidb.analytics.entity.TradeExitEntity
import com.jarvis.tidb.analytics.entity.TradeFeesEntity
import com.jarvis.tidb.analytics.entity.TradeJournalEntity
import com.jarvis.tidb.analytics.entity.TradingTimelineEventEntity
import com.jarvis.tidb.core.common.Converters

/**
 * JARVIS Trading Intelligence Database — Module 3: Trading Analytics & Learning.
 *
 * A third, independent Room database (alongside Module 1's `TidbDatabase` and Module 2's
 * `SignalDatabase`), for the same reason Module 2 chose independence from Module 1: this
 * module only ever consumes Module 1/2 through their repository interfaces
 * (`InstrumentRepository`, `SignalRepository`), never through a shared Room instance or
 * cross-database `@ForeignKey`. `signalId` and `instrumentId` columns throughout this module
 * are logical-only foreign keys, validated in the repository layer (see `TradeRepositoryImpl`).
 *
 * Destructive fallback is disabled, matching Module 1 and Module 2 — trade history, backtests,
 * AI learning records, the executive timeline, and portfolio history must never be silently
 * wiped by a schema change. Every future structural change ships an explicit
 * [androidx.room.migration.Migration].
 *
 * Reuses Module 1's `Converters` (`com.jarvis.tidb.core.common.Converters`) for enum-as-string
 * persistence rather than duplicating a converter class, since all three modules follow the
 * same enum-as-string convention.
 */
@Database(
    entities = [
        // Section 1 — Trade Lifecycle
        TradeEntity::class,
        TradeExecutionEntity::class,
        TradeExitEntity::class,
        TradeFeesEntity::class,
        TradeJournalEntity::class,
        // Section 2 — Backtesting
        BacktestEntity::class,
        BacktestConfigurationEntity::class,
        BacktestRunEntity::class,
        BacktestTradeEntity::class,
        BacktestResultEntity::class,
        // Section 3 — Performance Analytics
        PerformanceSnapshotEntity::class,
        PerformanceMetricEntity::class,
        StrategyPerformanceEntity::class,
        InstrumentPerformanceEntity::class,
        MonthlyPerformanceEntity::class,
        // Section 4 — AI Learning
        LearningObservationEntity::class,
        LearningInsightEntity::class,
        OptimizationSuggestionEntity::class,
        PatternDiscoveryEntity::class,
        FailureAnalysisEntity::class,
        // Section 5 — Executive Trading Memory
        TradingTimelineEventEntity::class,
        DecisionRecordEntity::class,
        DecisionExplanationEntity::class,
        LessonLearnedEntity::class,
        // Section 6 — Portfolio Intelligence
        PortfolioEntity::class,
        PortfolioPositionEntity::class,
        PortfolioAllocationEntity::class,
        PortfolioRiskEntity::class,
        CapitalMovementEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AnalyticsDatabase : RoomDatabase() {

    // Section 1
    abstract fun tradeDao(): TradeDao
    abstract fun tradeExecutionDao(): TradeExecutionDao
    abstract fun tradeExitDao(): TradeExitDao
    abstract fun tradeFeesDao(): TradeFeesDao
    abstract fun tradeJournalDao(): TradeJournalDao

    // Section 2
    abstract fun backtestDao(): BacktestDao
    abstract fun backtestConfigurationDao(): BacktestConfigurationDao
    abstract fun backtestRunDao(): BacktestRunDao
    abstract fun backtestTradeDao(): BacktestTradeDao
    abstract fun backtestResultDao(): BacktestResultDao

    // Section 3
    abstract fun performanceSnapshotDao(): PerformanceSnapshotDao
    abstract fun performanceMetricDao(): PerformanceMetricDao
    abstract fun strategyPerformanceDao(): StrategyPerformanceDao
    abstract fun instrumentPerformanceDao(): InstrumentPerformanceDao
    abstract fun monthlyPerformanceDao(): MonthlyPerformanceDao

    // Section 4
    abstract fun learningObservationDao(): LearningObservationDao
    abstract fun learningInsightDao(): LearningInsightDao
    abstract fun optimizationSuggestionDao(): OptimizationSuggestionDao
    abstract fun patternDiscoveryDao(): PatternDiscoveryDao
    abstract fun failureAnalysisDao(): FailureAnalysisDao

    // Section 5
    abstract fun tradingTimelineEventDao(): TradingTimelineEventDao
    abstract fun decisionRecordDao(): DecisionRecordDao
    abstract fun decisionExplanationDao(): DecisionExplanationDao
    abstract fun lessonLearnedDao(): LessonLearnedDao

    // Section 6
    abstract fun portfolioDao(): PortfolioDao
    abstract fun portfolioPositionDao(): PortfolioPositionDao
    abstract fun portfolioAllocationDao(): PortfolioAllocationDao
    abstract fun portfolioRiskDao(): PortfolioRiskDao
    abstract fun capitalMovementDao(): CapitalMovementDao

    companion object {
        private const val DATABASE_NAME = "jarvis_tidb_analytics.db"

        @Volatile
        private var INSTANCE: AnalyticsDatabase? = null

        fun getInstance(context: Context): AnalyticsDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AnalyticsDatabase::class.java,
                    DATABASE_NAME
                )
                    // No fallbackToDestructiveMigration() — see class doc. Every schema change
                    // ships a real Migration, same convention as Module 1 / Module 2.
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

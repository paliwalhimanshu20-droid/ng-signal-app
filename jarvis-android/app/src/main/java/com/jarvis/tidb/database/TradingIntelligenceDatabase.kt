package com.jarvis.tidb.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jarvis.tidb.core.Converters
import com.jarvis.tidb.core.dao.ContractDao
import com.jarvis.tidb.core.dao.ExchangeDao
import com.jarvis.tidb.core.dao.HistoricalCandleDao
import com.jarvis.tidb.core.dao.InstrumentDao
import com.jarvis.tidb.core.dao.LiveMarketSnapshotDao
import com.jarvis.tidb.core.dao.MarketEventDao
import com.jarvis.tidb.core.dao.MarketSessionDao
import com.jarvis.tidb.core.entity.ContractEntity
import com.jarvis.tidb.core.entity.ExchangeEntity
import com.jarvis.tidb.core.entity.HistoricalCandleEntity
import com.jarvis.tidb.core.entity.InstrumentEntity
import com.jarvis.tidb.core.entity.LiveMarketSnapshotEntity
import com.jarvis.tidb.core.entity.MarketEventEntity
import com.jarvis.tidb.core.entity.MarketSessionEntity
import com.jarvis.tidb.signals.dao.SignalDao
import com.jarvis.tidb.signals.dao.SignalLifecycleDao
import com.jarvis.tidb.signals.dao.SignalNoteDao
import com.jarvis.tidb.signals.dao.SignalReasonDao
import com.jarvis.tidb.signals.dao.SignalSnapshotDao
import com.jarvis.tidb.signals.dao.SignalTagDao
import com.jarvis.tidb.signals.entity.SignalEntity
import com.jarvis.tidb.signals.entity.SignalLifecycleEntity
import com.jarvis.tidb.signals.entity.SignalNoteEntity
import com.jarvis.tidb.signals.entity.SignalReasonEntity
import com.jarvis.tidb.signals.entity.SignalSnapshotEntity
import com.jarvis.tidb.signals.entity.SignalTagEntity
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
import com.jarvis.tidb.analytics.dao.LearningEvidenceLinkDao
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
import com.jarvis.tidb.analytics.dao.PortfolioSnapshotDao
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
import com.jarvis.tidb.analytics.entity.LearningEvidenceLinkEntity
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
import com.jarvis.tidb.analytics.entity.PortfolioSnapshotEntity
import com.jarvis.tidb.analytics.entity.StrategyPerformanceEntity
import com.jarvis.tidb.analytics.entity.TradeEntity
import com.jarvis.tidb.analytics.entity.TradeExecutionEntity
import com.jarvis.tidb.analytics.entity.TradeExitEntity
import com.jarvis.tidb.analytics.entity.TradeFeesEntity
import com.jarvis.tidb.analytics.entity.TradeJournalEntity
import com.jarvis.tidb.analytics.entity.TradingTimelineEventEntity
import com.jarvis.tidb.database.migration.TidbMigrations

/**
 * TRADING INTELLIGENCE DATABASE v1.0 — the single, unified Room database for JARVIS.
 *
 * Supersedes the three physically separate Room databases used through Module 3
 * (`TidbDatabase`, `SignalDatabase`, `AnalyticsDatabase`). Module boundaries are now expressed
 * purely by Kotlin package (`core`, `signals`, `analytics`), repository interfaces, and
 * documentation — never by a separate physical `.db` file. See
 * `docs/database/TRADING-INTELLIGENCE-DATABASE-v1.md` §1–2 for the full rationale and the
 * cross-module `@ForeignKey` upgrades this merge enabled.
 *
 * Schema version **4**: version 1–3 map to the pre-merge per-module schema generations
 * (Module 1 reached v2 internally; this database's version counter restarts the *unified*
 * schema at 4 to leave 1–3 unambiguously reserved for "one of the three legacy per-module
 * databases", so a version number alone is never ambiguous about which physical schema it
 * describes). See [TidbMigrations] and the migration-strategy section of the architecture doc
 * for the one-time legacy-data consolidation path (`LegacyDatabaseConsolidator`) that runs
 * before this database is first opened on an upgrading install.
 *
 * No destructive fallback — this is the same non-negotiable convention every module has
 * followed since Module 1 Revision 1: every structural change ships an explicit
 * [androidx.room.migration.Migration], never a schema wipe.
 */
@Database(
    entities = [
        // ---- core (Module 1) ----
        ExchangeEntity::class,
        MarketSessionEntity::class,
        InstrumentEntity::class,
        ContractEntity::class,
        HistoricalCandleEntity::class,
        LiveMarketSnapshotEntity::class,
        MarketEventEntity::class,
        // ---- signals (Module 2) ----
        SignalEntity::class,
        SignalReasonEntity::class,
        SignalSnapshotEntity::class,
        SignalLifecycleEntity::class,
        SignalTagEntity::class,
        SignalNoteEntity::class,
        // ---- analytics (Module 3) — Section 1: Trade Lifecycle ----
        TradeEntity::class,
        TradeExecutionEntity::class,
        TradeExitEntity::class,
        TradeFeesEntity::class,
        TradeJournalEntity::class,
        // ---- analytics — Section 2: Backtesting ----
        BacktestEntity::class,
        BacktestConfigurationEntity::class,
        BacktestRunEntity::class,
        BacktestTradeEntity::class,
        BacktestResultEntity::class,
        // ---- analytics — Section 3: Performance ----
        PerformanceSnapshotEntity::class,
        PerformanceMetricEntity::class,
        StrategyPerformanceEntity::class,
        InstrumentPerformanceEntity::class,
        MonthlyPerformanceEntity::class,
        // ---- analytics — Section 4: AI Learning ----
        LearningObservationEntity::class,
        LearningInsightEntity::class,
        OptimizationSuggestionEntity::class,
        PatternDiscoveryEntity::class,
        FailureAnalysisEntity::class,
        LearningEvidenceLinkEntity::class,
        // ---- analytics — Section 5: Executive Trading Memory ----
        TradingTimelineEventEntity::class,
        DecisionRecordEntity::class,
        DecisionExplanationEntity::class,
        LessonLearnedEntity::class,
        // ---- analytics — Section 6: Portfolio Intelligence ----
        PortfolioEntity::class,
        PortfolioPositionEntity::class,
        PortfolioAllocationEntity::class,
        PortfolioRiskEntity::class,
        CapitalMovementEntity::class,
        PortfolioSnapshotEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class TradingIntelligenceDatabase : RoomDatabase() {

    // ---- core ----
    abstract fun exchangeDao(): ExchangeDao
    abstract fun marketSessionDao(): MarketSessionDao
    abstract fun instrumentDao(): InstrumentDao
    abstract fun contractDao(): ContractDao
    abstract fun historicalCandleDao(): HistoricalCandleDao
    abstract fun liveMarketSnapshotDao(): LiveMarketSnapshotDao
    abstract fun marketEventDao(): MarketEventDao

    // ---- signals ----
    abstract fun signalDao(): SignalDao
    abstract fun signalReasonDao(): SignalReasonDao
    abstract fun signalSnapshotDao(): SignalSnapshotDao
    abstract fun signalLifecycleDao(): SignalLifecycleDao
    abstract fun signalTagDao(): SignalTagDao
    abstract fun signalNoteDao(): SignalNoteDao

    // ---- analytics: trade lifecycle ----
    abstract fun tradeDao(): TradeDao
    abstract fun tradeExecutionDao(): TradeExecutionDao
    abstract fun tradeExitDao(): TradeExitDao
    abstract fun tradeFeesDao(): TradeFeesDao
    abstract fun tradeJournalDao(): TradeJournalDao

    // ---- analytics: backtesting ----
    abstract fun backtestDao(): BacktestDao
    abstract fun backtestConfigurationDao(): BacktestConfigurationDao
    abstract fun backtestRunDao(): BacktestRunDao
    abstract fun backtestTradeDao(): BacktestTradeDao
    abstract fun backtestResultDao(): BacktestResultDao

    // ---- analytics: performance ----
    abstract fun performanceSnapshotDao(): PerformanceSnapshotDao
    abstract fun performanceMetricDao(): PerformanceMetricDao
    abstract fun strategyPerformanceDao(): StrategyPerformanceDao
    abstract fun instrumentPerformanceDao(): InstrumentPerformanceDao
    abstract fun monthlyPerformanceDao(): MonthlyPerformanceDao

    // ---- analytics: AI learning ----
    abstract fun learningObservationDao(): LearningObservationDao
    abstract fun learningInsightDao(): LearningInsightDao
    abstract fun optimizationSuggestionDao(): OptimizationSuggestionDao
    abstract fun patternDiscoveryDao(): PatternDiscoveryDao
    abstract fun failureAnalysisDao(): FailureAnalysisDao
    abstract fun learningEvidenceLinkDao(): LearningEvidenceLinkDao

    // ---- analytics: executive trading memory ----
    abstract fun tradingTimelineEventDao(): TradingTimelineEventDao
    abstract fun decisionRecordDao(): DecisionRecordDao
    abstract fun decisionExplanationDao(): DecisionExplanationDao
    abstract fun lessonLearnedDao(): LessonLearnedDao

    // ---- analytics: portfolio intelligence ----
    abstract fun portfolioDao(): PortfolioDao
    abstract fun portfolioPositionDao(): PortfolioPositionDao
    abstract fun portfolioAllocationDao(): PortfolioAllocationDao
    abstract fun portfolioRiskDao(): PortfolioRiskDao
    abstract fun capitalMovementDao(): CapitalMovementDao
    abstract fun portfolioSnapshotDao(): PortfolioSnapshotDao

    companion object {
        const val DATABASE_NAME = "jarvis_trading_intelligence.db"

        @Volatile
        private var INSTANCE: TradingIntelligenceDatabase? = null

        fun getInstance(context: Context): TradingIntelligenceDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }

        private fun build(context: Context): TradingIntelligenceDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                TradingIntelligenceDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(*TidbMigrations.ALL)
                // No fallbackToDestructiveMigration() — see class doc.
                .build()
    }
}

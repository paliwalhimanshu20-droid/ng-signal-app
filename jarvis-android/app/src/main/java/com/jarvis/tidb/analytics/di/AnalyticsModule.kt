package com.jarvis.tidb.analytics.di

import android.content.Context
import com.jarvis.tidb.analytics.AnalyticsDatabase
import com.jarvis.tidb.analytics.repository.BacktestRepository
import com.jarvis.tidb.analytics.repository.LearningRepository
import com.jarvis.tidb.analytics.repository.PerformanceRepository
import com.jarvis.tidb.analytics.repository.PortfolioRepository
import com.jarvis.tidb.analytics.repository.TimelineRepository
import com.jarvis.tidb.analytics.repository.TradeRepository
import com.jarvis.tidb.analytics.repository.impl.BacktestRepositoryImpl
import com.jarvis.tidb.analytics.repository.impl.LearningRepositoryImpl
import com.jarvis.tidb.analytics.repository.impl.PerformanceRepositoryImpl
import com.jarvis.tidb.analytics.repository.impl.PortfolioRepositoryImpl
import com.jarvis.tidb.analytics.repository.impl.TimelineRepositoryImpl
import com.jarvis.tidb.analytics.repository.impl.TradeRepositoryImpl
import com.jarvis.tidb.core.di.TidbModule
import com.jarvis.tidb.signals.di.SignalModule

/**
 * Framework-agnostic manual DI provider for Module 3, mirroring Module 1's `TidbModule` and
 * Module 2's `SignalModule` pattern rather than pulling in Hilt/Koin. Call [initialize] once
 * (e.g. from `Application.onCreate`) after [TidbModule] and [SignalModule] have been
 * initialized, since [TradeRepositoryImpl] depends on both of those modules' repositories for
 * cross-module id validation.
 */
object AnalyticsModule {

    @Volatile
    private var database: AnalyticsDatabase? = null

    @Volatile
    private var tradeRepository: TradeRepository? = null

    @Volatile
    private var backtestRepository: BacktestRepository? = null

    @Volatile
    private var performanceRepository: PerformanceRepository? = null

    @Volatile
    private var learningRepository: LearningRepository? = null

    @Volatile
    private var timelineRepository: TimelineRepository? = null

    @Volatile
    private var portfolioRepository: PortfolioRepository? = null

    fun initialize(context: Context) {
        if (database != null) return
        synchronized(this) {
            if (database != null) return

            val db = AnalyticsDatabase.getInstance(context)
            database = db

            tradeRepository = TradeRepositoryImpl(
                tradeDao = db.tradeDao(),
                executionDao = db.tradeExecutionDao(),
                exitDao = db.tradeExitDao(),
                feesDao = db.tradeFeesDao(),
                journalDao = db.tradeJournalDao(),
                signalRepository = SignalModule.signalRepository(),
                instrumentRepository = TidbModule.instrumentRepository()
            )

            backtestRepository = BacktestRepositoryImpl(
                backtestDao = db.backtestDao(),
                configurationDao = db.backtestConfigurationDao(),
                runDao = db.backtestRunDao(),
                tradeDao = db.backtestTradeDao(),
                resultDao = db.backtestResultDao()
            )

            performanceRepository = PerformanceRepositoryImpl(
                snapshotDao = db.performanceSnapshotDao(),
                metricDao = db.performanceMetricDao(),
                strategyDao = db.strategyPerformanceDao(),
                instrumentDao = db.instrumentPerformanceDao(),
                monthlyDao = db.monthlyPerformanceDao()
            )

            learningRepository = LearningRepositoryImpl(
                observationDao = db.learningObservationDao(),
                insightDao = db.learningInsightDao(),
                suggestionDao = db.optimizationSuggestionDao(),
                patternDao = db.patternDiscoveryDao(),
                failureDao = db.failureAnalysisDao()
            )

            timelineRepository = TimelineRepositoryImpl(
                eventDao = db.tradingTimelineEventDao(),
                decisionDao = db.decisionRecordDao(),
                explanationDao = db.decisionExplanationDao(),
                lessonDao = db.lessonLearnedDao()
            )

            portfolioRepository = PortfolioRepositoryImpl(
                portfolioDao = db.portfolioDao(),
                positionDao = db.portfolioPositionDao(),
                allocationDao = db.portfolioAllocationDao(),
                riskDao = db.portfolioRiskDao(),
                capitalMovementDao = db.capitalMovementDao()
            )
        }
    }

    fun tradeRepository(): TradeRepository =
        tradeRepository ?: error("AnalyticsModule.initialize(context) must be called before use")

    fun backtestRepository(): BacktestRepository =
        backtestRepository ?: error("AnalyticsModule.initialize(context) must be called before use")

    fun performanceRepository(): PerformanceRepository =
        performanceRepository ?: error("AnalyticsModule.initialize(context) must be called before use")

    fun learningRepository(): LearningRepository =
        learningRepository ?: error("AnalyticsModule.initialize(context) must be called before use")

    fun timelineRepository(): TimelineRepository =
        timelineRepository ?: error("AnalyticsModule.initialize(context) must be called before use")

    fun portfolioRepository(): PortfolioRepository =
        portfolioRepository ?: error("AnalyticsModule.initialize(context) must be called before use")
}

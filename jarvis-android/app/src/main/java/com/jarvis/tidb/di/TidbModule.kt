package com.jarvis.tidb.di

import android.content.Context
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
import com.jarvis.tidb.core.repository.ContractRepository
import com.jarvis.tidb.core.repository.ExchangeRepository
import com.jarvis.tidb.core.repository.HistoricalCandleRepository
import com.jarvis.tidb.core.repository.InstrumentRepository
import com.jarvis.tidb.core.repository.LiveMarketSnapshotRepository
import com.jarvis.tidb.core.repository.MarketEventRepository
import com.jarvis.tidb.core.repository.MarketSessionRepository
import com.jarvis.tidb.core.repository.impl.ContractRepositoryImpl
import com.jarvis.tidb.core.repository.impl.ExchangeRepositoryImpl
import com.jarvis.tidb.core.repository.impl.HistoricalCandleRepositoryImpl
import com.jarvis.tidb.core.repository.impl.InstrumentRepositoryImpl
import com.jarvis.tidb.core.repository.impl.LiveMarketSnapshotRepositoryImpl
import com.jarvis.tidb.core.repository.impl.MarketEventRepositoryImpl
import com.jarvis.tidb.core.repository.impl.MarketSessionRepositoryImpl
import com.jarvis.tidb.database.TradingIntelligenceDatabase
import com.jarvis.tidb.database.migration.LegacyDatabaseConsolidator
import com.jarvis.tidb.signals.repository.SignalRepository
import com.jarvis.tidb.signals.repository.impl.SignalRepositoryImpl

/**
 * The single, unified, framework-agnostic manual DI provider for the entire Trading
 * Intelligence Database v1.0 — replacing `core.di.TidbModule`, `signals.di.SignalModule`, and
 * `analytics.di.AnalyticsModule` from the pre-merge, three-database architecture.
 *
 * Call [initialize] exactly once, e.g. from `Application.onCreate`. It runs
 * [LegacyDatabaseConsolidator.runIfNeeded] first (a no-op after the first successful run, or on
 * a fresh install with no legacy files), then opens [TradingIntelligenceDatabase] and wires
 * every repository. Because everything is one database now, there is no more "initialize
 * Module 1's DI before Module 2's, before Module 3's" ordering requirement that the old
 * per-module `di` objects had — this single call replaces all three.
 */
object TidbModule {

    @Volatile
    private var database: TradingIntelligenceDatabase? = null

    // ---- core ----
    @Volatile private var exchangeRepository: ExchangeRepository? = null
    @Volatile private var marketSessionRepository: MarketSessionRepository? = null
    @Volatile private var instrumentRepository: InstrumentRepository? = null
    @Volatile private var contractRepository: ContractRepository? = null
    @Volatile private var historicalCandleRepository: HistoricalCandleRepository? = null
    @Volatile private var liveMarketSnapshotRepository: LiveMarketSnapshotRepository? = null
    @Volatile private var marketEventRepository: MarketEventRepository? = null

    // ---- signals ----
    @Volatile private var signalRepository: SignalRepository? = null

    // ---- analytics ----
    @Volatile private var tradeRepository: TradeRepository? = null
    @Volatile private var backtestRepository: BacktestRepository? = null
    @Volatile private var performanceRepository: PerformanceRepository? = null
    @Volatile private var learningRepository: LearningRepository? = null
    @Volatile private var timelineRepository: TimelineRepository? = null
    @Volatile private var portfolioRepository: PortfolioRepository? = null

    fun initialize(context: Context) {
        if (database != null) return
        synchronized(this) {
            if (database != null) return

            LegacyDatabaseConsolidator.runIfNeeded(context)

            val db = TradingIntelligenceDatabase.getInstance(context)
            database = db

            // ---- core ----
            exchangeRepository = ExchangeRepositoryImpl(db.exchangeDao())
            marketSessionRepository = MarketSessionRepositoryImpl(db.marketSessionDao())
            instrumentRepository = InstrumentRepositoryImpl(db.instrumentDao())
            contractRepository = ContractRepositoryImpl(db.contractDao())
            historicalCandleRepository = HistoricalCandleRepositoryImpl(db.historicalCandleDao())
            liveMarketSnapshotRepository = LiveMarketSnapshotRepositoryImpl(db.liveMarketSnapshotDao())
            marketEventRepository = MarketEventRepositoryImpl(db.marketEventDao())

            // ---- signals ----
            signalRepository = SignalRepositoryImpl(
                signalDao = db.signalDao(),
                reasonDao = db.signalReasonDao(),
                snapshotDao = db.signalSnapshotDao(),
                lifecycleDao = db.signalLifecycleDao(),
                tagDao = db.signalTagDao(),
                noteDao = db.signalNoteDao()
            )

            // ---- analytics ----
            tradeRepository = TradeRepositoryImpl(
                tradeDao = db.tradeDao(),
                executionDao = db.tradeExecutionDao(),
                exitDao = db.tradeExitDao(),
                feesDao = db.tradeFeesDao(),
                journalDao = db.tradeJournalDao(),
                signalRepository = signalRepository!!,
                instrumentRepository = instrumentRepository!!
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
                failureDao = db.failureAnalysisDao(),
                evidenceLinkDao = db.learningEvidenceLinkDao()
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
                capitalMovementDao = db.capitalMovementDao(),
                snapshotDao = db.portfolioSnapshotDao()
            )
        }
    }

    private fun <T> require(value: T?): T = value ?: error("TidbModule.initialize(context) must be called before use")

    // ---- core ----
    fun exchangeRepository(): ExchangeRepository = require(exchangeRepository)
    fun marketSessionRepository(): MarketSessionRepository = require(marketSessionRepository)
    fun instrumentRepository(): InstrumentRepository = require(instrumentRepository)
    fun contractRepository(): ContractRepository = require(contractRepository)
    fun historicalCandleRepository(): HistoricalCandleRepository = require(historicalCandleRepository)
    fun liveMarketSnapshotRepository(): LiveMarketSnapshotRepository = require(liveMarketSnapshotRepository)
    fun marketEventRepository(): MarketEventRepository = require(marketEventRepository)

    // ---- signals ----
    fun signalRepository(): SignalRepository = require(signalRepository)

    // ---- analytics ----
    fun tradeRepository(): TradeRepository = require(tradeRepository)
    fun backtestRepository(): BacktestRepository = require(backtestRepository)
    fun performanceRepository(): PerformanceRepository = require(performanceRepository)
    fun learningRepository(): LearningRepository = require(learningRepository)
    fun timelineRepository(): TimelineRepository = require(timelineRepository)
    fun portfolioRepository(): PortfolioRepository = require(portfolioRepository)
}

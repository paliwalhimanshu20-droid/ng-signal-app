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
// ---- Historical Market Data Platform (schema v5) ----
import com.jarvis.tidb.historical.ingestion.repository.DataProviderRepository
import com.jarvis.tidb.historical.ingestion.repository.IngestionCheckpointRepository
import com.jarvis.tidb.historical.ingestion.repository.IngestionJobRepository
import com.jarvis.tidb.historical.ingestion.repository.impl.DataProviderRepositoryImpl
import com.jarvis.tidb.historical.ingestion.repository.impl.IngestionCheckpointRepositoryImpl
import com.jarvis.tidb.historical.ingestion.repository.impl.IngestionJobRepositoryImpl
import com.jarvis.tidb.historical.candle.repository.CandleGapRepository
import com.jarvis.tidb.historical.candle.repository.CandleVersionRepository
import com.jarvis.tidb.historical.candle.repository.impl.CandleGapRepositoryImpl
import com.jarvis.tidb.historical.candle.repository.impl.CandleVersionRepositoryImpl
import com.jarvis.tidb.historical.quality.repository.CorporateActionRepository
import com.jarvis.tidb.historical.quality.repository.QualityReportRepository
import com.jarvis.tidb.historical.quality.repository.impl.CorporateActionRepositoryImpl
import com.jarvis.tidb.historical.quality.repository.impl.QualityReportRepositoryImpl
import com.jarvis.tidb.historical.indicator.repository.IndicatorComputationRunRepository
import com.jarvis.tidb.historical.indicator.repository.IndicatorDefinitionRepository
import com.jarvis.tidb.historical.indicator.repository.IndicatorValueRepository
import com.jarvis.tidb.historical.indicator.repository.impl.IndicatorComputationRunRepositoryImpl
import com.jarvis.tidb.historical.indicator.repository.impl.IndicatorDefinitionRepositoryImpl
import com.jarvis.tidb.historical.indicator.repository.impl.IndicatorValueRepositoryImpl
import com.jarvis.tidb.historical.dna.repository.GapBehaviorProfileRepository
import com.jarvis.tidb.historical.dna.repository.IndicatorBehaviorProfileRepository
import com.jarvis.tidb.historical.dna.repository.LiquidityProfileRepository
import com.jarvis.tidb.historical.dna.repository.SeasonalTendencyRepository
import com.jarvis.tidb.historical.dna.repository.SessionBehaviorProfileRepository
import com.jarvis.tidb.historical.dna.repository.StatisticalCharacteristicsRepository
import com.jarvis.tidb.historical.dna.repository.TrendPersistenceProfileRepository
import com.jarvis.tidb.historical.dna.repository.VolatilityProfileRepository
import com.jarvis.tidb.historical.dna.repository.impl.GapBehaviorProfileRepositoryImpl
import com.jarvis.tidb.historical.dna.repository.impl.IndicatorBehaviorProfileRepositoryImpl
import com.jarvis.tidb.historical.dna.repository.impl.LiquidityProfileRepositoryImpl
import com.jarvis.tidb.historical.dna.repository.impl.SeasonalTendencyRepositoryImpl
import com.jarvis.tidb.historical.dna.repository.impl.SessionBehaviorProfileRepositoryImpl
import com.jarvis.tidb.historical.dna.repository.impl.StatisticalCharacteristicsRepositoryImpl
import com.jarvis.tidb.historical.dna.repository.impl.TrendPersistenceProfileRepositoryImpl
import com.jarvis.tidb.historical.dna.repository.impl.VolatilityProfileRepositoryImpl
import com.jarvis.tidb.historical.evidence.repository.EvidenceRepository
import com.jarvis.tidb.historical.evidence.repository.impl.EvidenceRepositoryImpl
// ---- TRADING-006 (schema v6): Module 5 — Trading Intelligence & Evidence Engine ----
import com.jarvis.tidb.intelligence.evidence.repository.IntelligenceEvidenceRepository
import com.jarvis.tidb.intelligence.evidence.repository.impl.IntelligenceEvidenceRepositoryImpl
import com.jarvis.tidb.intelligence.pattern.repository.PatternRepository
import com.jarvis.tidb.intelligence.pattern.repository.impl.PatternRepositoryImpl
import com.jarvis.tidb.intelligence.regime.repository.RegimeRepository
import com.jarvis.tidb.intelligence.regime.repository.impl.RegimeRepositoryImpl
import com.jarvis.tidb.intelligence.confidence.repository.ConfidenceRepository
import com.jarvis.tidb.intelligence.confidence.repository.impl.ConfidenceRepositoryImpl
import com.jarvis.tidb.intelligence.research.repository.ResearchRepository
import com.jarvis.tidb.intelligence.research.repository.impl.ResearchRepositoryImpl
import com.jarvis.tidb.intelligence.graph.repository.GraphRepository
import com.jarvis.tidb.intelligence.graph.repository.impl.GraphRepositoryImpl
// ---- TRADING-007A.1 (schema v7): News & Sentiment Intelligence Platform ----
import com.jarvis.tidb.news.repository.NewsRepository
import com.jarvis.tidb.news.repository.impl.NewsRepositoryImpl
// ---- TRADING-007A.2 (schema v8): Market Context Intelligence Platform ----
import com.jarvis.tidb.context.repository.MarketContextIntelligenceRepository
import com.jarvis.tidb.context.repository.impl.MarketContextIntelligenceRepositoryImpl
// ---- TRADING-007B (schema v9): Decision Intelligence Engine ----
import com.jarvis.tidb.decision.repository.DecisionIntelligenceRepository
import com.jarvis.tidb.decision.repository.impl.DecisionIntelligenceRepositoryImpl

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

    // ---- historical: ingestion ----
    @Volatile private var dataProviderRepository: DataProviderRepository? = null
    @Volatile private var ingestionJobRepository: IngestionJobRepository? = null
    @Volatile private var ingestionCheckpointRepository: IngestionCheckpointRepository? = null

    // ---- historical: candle extensions ----
    @Volatile private var candleVersionRepository: CandleVersionRepository? = null
    @Volatile private var candleGapRepository: CandleGapRepository? = null

    // ---- historical: quality engine ----
    @Volatile private var qualityReportRepository: QualityReportRepository? = null
    @Volatile private var corporateActionRepository: CorporateActionRepository? = null

    // ---- historical: indicator warehouse ----
    @Volatile private var indicatorDefinitionRepository: IndicatorDefinitionRepository? = null
    @Volatile private var indicatorValueRepository: IndicatorValueRepository? = null
    @Volatile private var indicatorComputationRunRepository: IndicatorComputationRunRepository? = null

    // ---- historical: instrument DNA foundation ----
    @Volatile private var volatilityProfileRepository: VolatilityProfileRepository? = null
    @Volatile private var sessionBehaviorProfileRepository: SessionBehaviorProfileRepository? = null
    @Volatile private var trendPersistenceProfileRepository: TrendPersistenceProfileRepository? = null
    @Volatile private var liquidityProfileRepository: LiquidityProfileRepository? = null
    @Volatile private var gapBehaviorProfileRepository: GapBehaviorProfileRepository? = null
    @Volatile private var seasonalTendencyRepository: SeasonalTendencyRepository? = null
    @Volatile private var indicatorBehaviorProfileRepository: IndicatorBehaviorProfileRepository? = null
    @Volatile private var statisticalCharacteristicsRepository: StatisticalCharacteristicsRepository? = null

    // ---- historical: evidence foundation ----
    @Volatile private var evidenceRepository: EvidenceRepository? = null

    // ---- TRADING-006 (schema v6): Module 5 — Trading Intelligence & Evidence Engine ----
    @Volatile private var intelligenceEvidenceRepository: IntelligenceEvidenceRepository? = null
    @Volatile private var patternRepository: PatternRepository? = null
    @Volatile private var regimeRepository: RegimeRepository? = null
    @Volatile private var confidenceRepository: ConfidenceRepository? = null
    @Volatile private var researchRepository: ResearchRepository? = null
    @Volatile private var graphRepository: GraphRepository? = null

    // ---- TRADING-007A.1 (schema v7): News & Sentiment Intelligence Platform ----
    @Volatile private var newsRepository: NewsRepository? = null

    // ---- TRADING-007A.2 (schema v8): Market Context Intelligence Platform ----
    @Volatile private var marketContextIntelligenceRepository: MarketContextIntelligenceRepository? = null

    // ---- TRADING-007B (schema v9): Decision Intelligence Engine ----
    @Volatile private var decisionIntelligenceRepository: DecisionIntelligenceRepository? = null

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

            // ---- historical: ingestion ----
            dataProviderRepository = DataProviderRepositoryImpl(db.dataProviderDao())
            ingestionJobRepository = IngestionJobRepositoryImpl(db.ingestionJobDao(), db.ingestionJobLogDao())
            ingestionCheckpointRepository = IngestionCheckpointRepositoryImpl(db.ingestionCheckpointDao())

            // ---- historical: candle extensions ----
            candleVersionRepository = CandleVersionRepositoryImpl(db.candleVersionDao())
            candleGapRepository = CandleGapRepositoryImpl(db.candleGapDao())

            // ---- historical: quality engine ----
            qualityReportRepository = QualityReportRepositoryImpl(db.candleQualityReportDao(), db.qualityIssueDao())
            corporateActionRepository = CorporateActionRepositoryImpl(db.corporateActionDao())

            // ---- historical: indicator warehouse ----
            indicatorDefinitionRepository = IndicatorDefinitionRepositoryImpl(db.indicatorDefinitionDao())
            indicatorValueRepository = IndicatorValueRepositoryImpl(db.indicatorValueDao())
            indicatorComputationRunRepository = IndicatorComputationRunRepositoryImpl(db.indicatorComputationRunDao())

            // ---- historical: instrument DNA foundation ----
            volatilityProfileRepository = VolatilityProfileRepositoryImpl(db.volatilityProfileDao())
            sessionBehaviorProfileRepository = SessionBehaviorProfileRepositoryImpl(db.sessionBehaviorProfileDao())
            trendPersistenceProfileRepository = TrendPersistenceProfileRepositoryImpl(db.trendPersistenceProfileDao())
            liquidityProfileRepository = LiquidityProfileRepositoryImpl(db.liquidityProfileDao())
            gapBehaviorProfileRepository = GapBehaviorProfileRepositoryImpl(db.gapBehaviorProfileDao())
            seasonalTendencyRepository = SeasonalTendencyRepositoryImpl(db.seasonalTendencyDao())
            indicatorBehaviorProfileRepository = IndicatorBehaviorProfileRepositoryImpl(db.indicatorBehaviorProfileDao())
            statisticalCharacteristicsRepository = StatisticalCharacteristicsRepositoryImpl(db.statisticalCharacteristicsDao())

            // ---- historical: evidence foundation ----
            evidenceRepository = EvidenceRepositoryImpl(
                observationDao = db.marketObservationDao(),
                evidenceDao = db.evidenceRecordDao(),
                patternDao = db.patternOccurrenceDao(),
                supportingIndicatorDao = db.supportingIndicatorDao(),
                confidenceComponentDao = db.confidenceComponentDao(),
                sourceReferenceDao = db.sourceReferenceDao()
            )

            // ---- TRADING-006 (schema v6): Module 5 — Trading Intelligence & Evidence Engine ----
            intelligenceEvidenceRepository = IntelligenceEvidenceRepositoryImpl(
                categoryDao = db.evidenceCategoryDao(),
                sourceDao = db.evidenceSourceDao(),
                linkDao = db.evidenceLinkDao(),
                outcomeDao = db.evidenceOutcomeDao()
            )
            patternRepository = PatternRepositoryImpl(db.patternDao())
            regimeRepository = RegimeRepositoryImpl(
                regimeDao = db.marketRegimeDao(),
                observationDao = db.regimeObservationDao()
            )
            confidenceRepository = ConfidenceRepositoryImpl(
                modelDao = db.confidenceModelDao(),
                scoreDao = db.confidenceScoreDao()
            )
            researchRepository = ResearchRepositoryImpl(
                hypothesisDao = db.hypothesisDao(),
                experimentDao = db.experimentDao(),
                runDao = db.experimentRunDao(),
                resultDao = db.experimentResultDao()
            )
            graphRepository = GraphRepositoryImpl(
                relationshipDao = db.entityRelationshipDao(),
                contextDao = db.marketContextDao(),
                causalDao = db.causalObservationDao(),
                correlationDao = db.correlationDao()
            )

            // ---- TRADING-007A.1 (schema v7): News & Sentiment Intelligence Platform ----
            newsRepository = NewsRepositoryImpl(
                sourceDao = db.newsSourceDao(),
                articleDao = db.newsArticleDao(),
                categoryDao = db.newsCategoryDao(),
                categoryLinkDao = db.newsCategoryLinkDao(),
                instrumentLinkDao = db.newsInstrumentLinkDao(),
                sentimentScoreDao = db.sentimentScoreDao(),
                duplicateDao = db.newsDuplicateDao()
            )

            // ---- TRADING-007A.2 (schema v8): Market Context Intelligence Platform ----
            marketContextIntelligenceRepository = MarketContextIntelligenceRepositoryImpl(
                eventDao = db.economicEventDao(),
                categoryDao = db.economicEventCategoryDao(),
                categoryLinkDao = db.economicEventCategoryLinkDao(),
                instrumentLinkDao = db.economicEventInstrumentLinkDao(),
                outcomeDao = db.economicEventOutcomeDao(),
                driftMetricDao = db.driftMetricDao(),
                calibrationMetricDao = db.calibrationMetricDao()
            )

            // ---- TRADING-007B (schema v9): Decision Intelligence Engine ----
            decisionIntelligenceRepository = DecisionIntelligenceRepositoryImpl(
                recommendationDao = db.recommendationDao(),
                riskAssessmentDao = db.recommendationRiskAssessmentDao(),
                alternativeDao = db.recommendationAlternativeDao(),
                outcomeDao = db.recommendationOutcomeDao(),
                reviewDao = db.decisionReviewDao()
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

    // ---- historical: ingestion ----
    fun dataProviderRepository(): DataProviderRepository = require(dataProviderRepository)
    fun ingestionJobRepository(): IngestionJobRepository = require(ingestionJobRepository)
    fun ingestionCheckpointRepository(): IngestionCheckpointRepository = require(ingestionCheckpointRepository)

    // ---- historical: candle extensions ----
    fun candleVersionRepository(): CandleVersionRepository = require(candleVersionRepository)
    fun candleGapRepository(): CandleGapRepository = require(candleGapRepository)

    // ---- historical: quality engine ----
    fun qualityReportRepository(): QualityReportRepository = require(qualityReportRepository)
    fun corporateActionRepository(): CorporateActionRepository = require(corporateActionRepository)

    // ---- historical: indicator warehouse ----
    fun indicatorDefinitionRepository(): IndicatorDefinitionRepository = require(indicatorDefinitionRepository)
    fun indicatorValueRepository(): IndicatorValueRepository = require(indicatorValueRepository)
    fun indicatorComputationRunRepository(): IndicatorComputationRunRepository = require(indicatorComputationRunRepository)

    // ---- historical: instrument DNA foundation ----
    fun volatilityProfileRepository(): VolatilityProfileRepository = require(volatilityProfileRepository)
    fun sessionBehaviorProfileRepository(): SessionBehaviorProfileRepository = require(sessionBehaviorProfileRepository)
    fun trendPersistenceProfileRepository(): TrendPersistenceProfileRepository = require(trendPersistenceProfileRepository)
    fun liquidityProfileRepository(): LiquidityProfileRepository = require(liquidityProfileRepository)
    fun gapBehaviorProfileRepository(): GapBehaviorProfileRepository = require(gapBehaviorProfileRepository)
    fun seasonalTendencyRepository(): SeasonalTendencyRepository = require(seasonalTendencyRepository)
    fun indicatorBehaviorProfileRepository(): IndicatorBehaviorProfileRepository = require(indicatorBehaviorProfileRepository)
    fun statisticalCharacteristicsRepository(): StatisticalCharacteristicsRepository = require(statisticalCharacteristicsRepository)

    // ---- historical: evidence foundation ----
    fun evidenceRepository(): EvidenceRepository = require(evidenceRepository)

    // ---- TRADING-006 (schema v6): Module 5 — Trading Intelligence & Evidence Engine ----
    fun intelligenceEvidenceRepository(): IntelligenceEvidenceRepository = require(intelligenceEvidenceRepository)
    fun patternRepository(): PatternRepository = require(patternRepository)
    fun regimeRepository(): RegimeRepository = require(regimeRepository)
    fun confidenceRepository(): ConfidenceRepository = require(confidenceRepository)
    fun researchRepository(): ResearchRepository = require(researchRepository)
    fun graphRepository(): GraphRepository = require(graphRepository)

    // ---- TRADING-007A.1 (schema v7): News & Sentiment Intelligence Platform ----
    fun newsRepository(): NewsRepository = require(newsRepository)

    // ---- TRADING-007A.2 (schema v8): Market Context Intelligence Platform ----
    fun marketContextIntelligenceRepository(): MarketContextIntelligenceRepository = require(marketContextIntelligenceRepository)

    // ---- TRADING-007B (schema v9): Decision Intelligence Engine ----
    fun decisionIntelligenceRepository(): DecisionIntelligenceRepository = require(decisionIntelligenceRepository)
}

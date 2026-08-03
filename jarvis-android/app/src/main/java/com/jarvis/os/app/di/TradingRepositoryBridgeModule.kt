package com.jarvis.os.app.di

import com.jarvis.tidb.analytics.repository.BacktestRepository
import com.jarvis.tidb.analytics.repository.PerformanceRepository
import com.jarvis.tidb.analytics.repository.PortfolioRepository
import com.jarvis.tidb.analytics.repository.TimelineRepository
import com.jarvis.tidb.analytics.repository.TradeRepository
import com.jarvis.tidb.context.repository.MarketContextIntelligenceRepository
import com.jarvis.tidb.core.repository.ContractRepository
import com.jarvis.tidb.core.repository.HistoricalCandleRepository
import com.jarvis.tidb.core.repository.InstrumentRepository
import com.jarvis.tidb.core.repository.LiveMarketSnapshotRepository
import com.jarvis.tidb.decision.repository.DecisionIntelligenceRepository
import com.jarvis.tidb.di.TidbModule
import com.jarvis.tidb.historical.evidence.repository.EvidenceRepository
import com.jarvis.tidb.historical.indicator.repository.IndicatorComputationRunRepository
import com.jarvis.tidb.historical.indicator.repository.IndicatorDefinitionRepository
import com.jarvis.tidb.historical.indicator.repository.IndicatorValueRepository
import com.jarvis.tidb.historical.ingestion.repository.IngestionCheckpointRepository
import com.jarvis.tidb.historical.ingestion.repository.IngestionJobRepository
import com.jarvis.tidb.historical.quality.repository.QualityReportRepository
import com.jarvis.tidb.analytics.repository.LearningRepository
import com.jarvis.tidb.intelligence.confidence.repository.ConfidenceRepository
import com.jarvis.tidb.intelligence.evidence.repository.IntelligenceEvidenceRepository
import com.jarvis.tidb.intelligence.graph.repository.GraphRepository
import com.jarvis.tidb.intelligence.pattern.repository.PatternRepository
import com.jarvis.tidb.intelligence.regime.repository.RegimeRepository
import com.jarvis.tidb.intelligence.research.repository.ResearchRepository
import com.jarvis.tidb.news.repository.NewsRepository
import com.jarvis.tidb.signals.repository.SignalRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * JARVIS-002 Layer 1 (Dependency Injection Bridge).
 *
 * [TidbModule] is a deliberately framework-agnostic, hand-rolled singleton provider -- see its
 * own class doc for why it predates this app's Hilt-based DI and is not being converted to Hilt
 * here (that would be exactly the "redesign existing architecture" the JARVIS-002 brief forbids).
 * This module does the opposite: it wraps each [TidbModule] accessor this integration's
 * September scope actually needs in a one-line `@Provides` function, so `core.trading.*`
 * components can `@Inject` a TIDB repository the same way they `@Inject` everything else in this
 * app's Hilt graph, without `TidbModule` gaining any Hilt awareness of its own.
 *
 * Every function below is a pure pass-through -- `TidbModule` remains the single owner of TIDB
 * repository construction and lifetime; this module duplicates no singleton logic, matching the
 * "do not duplicate repositories... do not duplicate singleton logic" instruction. Scoped
 * `@Singleton` here as well purely so Hilt's dependency graph is self-consistent (a
 * `@Singleton`-scoped consumer may depend on this binding); the underlying instance is still the
 * exact same one `TidbModule` already holds -- this annotation does not create a second instance.
 *
 * Repository selection is intentionally limited to what the 13-stage Decision Lifecycle
 * (`DecisionLifecycleRunner`) and `TradingBriefingEngine` touch for the September Natural Gas
 * milestone, per the JARVIS-002 implementation plan's Layer 1 scope -- not the full ~30-interface
 * TIDB surface. Extending this list for a later milestone is a one-line addition per repository,
 * following the same pattern.
 *
 * PRECONDITION: every function below calls a [TidbModule] accessor, each of which throws
 * `IllegalStateException` if [TidbModule.initialize] has not yet run (see that class's
 * `require` helper). [com.jarvis.os.app.JarvisApplication.onCreate] calls `initialize` before
 * any Activity starts, so this precondition holds for every realistic Hilt injection point in
 * this app -- documented here rather than defensively re-checked, so a genuine ordering bug
 * fails loudly instead of being silently swallowed.
 */
@Module
@InstallIn(SingletonComponent::class)
object TradingRepositoryBridgeModule {

    @Provides
    @Singleton
    fun provideInstrumentRepository(): InstrumentRepository =
        TidbModule.instrumentRepository()

    /**
     * Local Intent Router addition: [TidbLocalIntentHandler][com.jarvis.os.app.core.intelligence.localintent.TidbLocalIntentHandler]
     * is this module's first consumer outside the JARVIS-002 September Decision Lifecycle scope
     * this class's own docstring describes -- it needs raw contract/candle/snapshot data, not
     * decision-lifecycle evidence, so these three bindings extend the list rather than reuse an
     * existing one. Same one-line-per-repository pattern as everything else here; [TidbModule]
     * remains the sole owner of construction/lifetime.
     */
    @Provides
    @Singleton
    fun provideContractRepository(): ContractRepository =
        TidbModule.contractRepository()

    @Provides
    @Singleton
    fun provideHistoricalCandleRepository(): HistoricalCandleRepository =
        TidbModule.historicalCandleRepository()

    @Provides
    @Singleton
    fun provideLiveMarketSnapshotRepository(): LiveMarketSnapshotRepository =
        TidbModule.liveMarketSnapshotRepository()

    @Provides
    @Singleton
    fun provideDecisionIntelligenceRepository(): DecisionIntelligenceRepository =
        TidbModule.decisionIntelligenceRepository()

    @Provides
    @Singleton
    fun provideConfidenceRepository(): ConfidenceRepository =
        TidbModule.confidenceRepository()

    @Provides
    @Singleton
    fun provideIntelligenceEvidenceRepository(): IntelligenceEvidenceRepository =
        TidbModule.intelligenceEvidenceRepository()

    @Provides
    @Singleton
    fun provideEvidenceRepository(): EvidenceRepository =
        TidbModule.evidenceRepository()

    @Provides
    @Singleton
    fun provideSignalRepository(): SignalRepository =
        TidbModule.signalRepository()

    @Provides
    @Singleton
    fun providePatternRepository(): PatternRepository =
        TidbModule.patternRepository()

    @Provides
    @Singleton
    fun provideRegimeRepository(): RegimeRepository =
        TidbModule.regimeRepository()

    @Provides
    @Singleton
    fun provideResearchRepository(): ResearchRepository =
        TidbModule.researchRepository()

    @Provides
    @Singleton
    fun provideGraphRepository(): GraphRepository =
        TidbModule.graphRepository()

    @Provides
    @Singleton
    fun provideMarketContextIntelligenceRepository(): MarketContextIntelligenceRepository =
        TidbModule.marketContextIntelligenceRepository()

    @Provides
    @Singleton
    fun provideNewsRepository(): NewsRepository =
        TidbModule.newsRepository()

    @Provides
    @Singleton
    fun provideTimelineRepository(): TimelineRepository =
        TidbModule.timelineRepository()

    @Provides
    @Singleton
    fun provideTradeRepository(): TradeRepository =
        TidbModule.tradeRepository()

    @Provides
    @Singleton
    fun providePortfolioRepository(): PortfolioRepository =
        TidbModule.portfolioRepository()

    /**
     * "Phase 3C, Section 1+2 -- Evidence Validation Engine + Hallucination Guard" addition:
     * [com.jarvis.os.app.core.intelligence.localintent.EvidenceValidationLocalIntentHandler] and
     * [com.jarvis.os.app.core.intelligence.localintent.SystemStatusLocalIntentHandler] are this
     * binding's first Hilt-injected consumers -- [TidbLocalIntentHandler][com.jarvis.os.app.core.
     * intelligence.localintent.TidbLocalIntentHandler] above needed raw market data, not backtest
     * results, so this repository was never added to this module's curated list until now. Same
     * one-line-per-repository pattern as everything else here; [TidbModule] remains the sole
     * owner of construction/lifetime.
     */
    @Provides
    @Singleton
    fun provideBacktestRepository(): BacktestRepository =
        TidbModule.backtestRepository()

    @Provides
    @Singleton
    fun providePerformanceRepository(): PerformanceRepository =
        TidbModule.performanceRepository()

    /**
     * "Phase 4A, Section 2+5+6+8 -- Data Import Pipeline / Historical Candle Storage / Indicator
     * Population / Pipeline Orchestration": [com.jarvis.tidb.historical.ingestion.pipeline.
     * HistoricalDataImportPipeline] is this batch of six bindings' first Hilt-injected consumer.
     * Same one-line-per-repository pattern as everything above; [TidbModule] remains the sole
     * owner of construction/lifetime for all six, exactly as for every other binding in this file.
     */
    @Provides
    @Singleton
    fun provideIndicatorDefinitionRepository(): IndicatorDefinitionRepository =
        TidbModule.indicatorDefinitionRepository()

    @Provides
    @Singleton
    fun provideIndicatorValueRepository(): IndicatorValueRepository =
        TidbModule.indicatorValueRepository()

    @Provides
    @Singleton
    fun provideIndicatorComputationRunRepository(): IndicatorComputationRunRepository =
        TidbModule.indicatorComputationRunRepository()

    @Provides
    @Singleton
    fun provideQualityReportRepository(): QualityReportRepository =
        TidbModule.qualityReportRepository()

    @Provides
    @Singleton
    fun provideIngestionJobRepository(): IngestionJobRepository =
        TidbModule.ingestionJobRepository()

    @Provides
    @Singleton
    fun provideIngestionCheckpointRepository(): IngestionCheckpointRepository =
        TidbModule.ingestionCheckpointRepository()

    /**
     * "Phase 4B, Section 1+7+8 -- Trust Layer": [com.jarvis.os.app.core.trading.reasoning.
     * TrustScoreCalculator] is this binding's first Hilt-injected consumer (its Learning
     * dimension) -- every other repository the six-dimension Trust Score needs
     * (Optimization, Backtest, Portfolio, Quality, Indicator) was already bridged above for an
     * earlier consumer; this was the one gap. Same one-line-per-repository pattern as
     * everything else in this file; [TidbModule] remains the sole owner of construction/lifetime.
     */
    @Provides
    @Singleton
    fun provideLearningRepository(): LearningRepository =
        TidbModule.learningRepository()
}

package com.jarvis.tidb.core

import androidx.room.TypeConverter
import com.jarvis.tidb.core.entity.AssetClass
import com.jarvis.tidb.core.entity.CandleSource
import com.jarvis.tidb.core.entity.ContractTradingStatus
import com.jarvis.tidb.core.entity.EventSeverity
import com.jarvis.tidb.core.entity.InstrumentType
import com.jarvis.tidb.core.entity.MarketEventType
import com.jarvis.tidb.core.entity.MarketStatus
import com.jarvis.tidb.core.entity.RecordStatus
import com.jarvis.tidb.core.entity.Timeframe
import com.jarvis.tidb.signals.entity.MarketTrend
import com.jarvis.tidb.signals.entity.SignalStatus
import com.jarvis.tidb.signals.entity.SignalType

/**
 * The single set of Room `TypeConverters` for the unified `TradingIntelligenceDatabase`,
 * merging what used to be three separate `Converters` classes (`core.Converters`,
 * `signals.Converters`, and the analytics module's implicit reliance on `core.common.Converters`
 * — Module 3 had assumed it would reuse Module 1's converter class once merged; that assumption
 * is now correct). All enums persist as their String `value` rather than ordinal, so reordering
 * a declaration never corrupts existing rows.
 */
class Converters {

    // ---- Module 1 (core) enums --------------------------------------------------------

    @TypeConverter
    fun fromRecordStatus(status: RecordStatus): String = status.value
    @TypeConverter
    fun toRecordStatus(value: String): RecordStatus = RecordStatus.from(value)

    @TypeConverter
    fun fromAssetClass(assetClass: AssetClass): String = assetClass.value
    @TypeConverter
    fun toAssetClass(value: String): AssetClass = AssetClass.from(value)

    @TypeConverter
    fun fromInstrumentType(type: InstrumentType): String = type.value
    @TypeConverter
    fun toInstrumentType(value: String): InstrumentType = InstrumentType.from(value)

    @TypeConverter
    fun fromContractTradingStatus(status: ContractTradingStatus): String = status.value
    @TypeConverter
    fun toContractTradingStatus(value: String): ContractTradingStatus = ContractTradingStatus.from(value)

    @TypeConverter
    fun fromTimeframe(timeframe: Timeframe): String = timeframe.value
    @TypeConverter
    fun toTimeframe(value: String): Timeframe = Timeframe.from(value)

    @TypeConverter
    fun fromCandleSource(source: CandleSource): String = source.value
    @TypeConverter
    fun toCandleSource(value: String): CandleSource = CandleSource.from(value)

    @TypeConverter
    fun fromMarketStatus(status: MarketStatus): String = status.value
    @TypeConverter
    fun toMarketStatus(value: String): MarketStatus = MarketStatus.from(value)

    @TypeConverter
    fun fromMarketEventType(type: MarketEventType): String = type.value
    @TypeConverter
    fun toMarketEventType(value: String): MarketEventType = MarketEventType.from(value)

    @TypeConverter
    fun fromEventSeverity(severity: EventSeverity): String = severity.value
    @TypeConverter
    fun toEventSeverity(value: String): EventSeverity = EventSeverity.from(value)

    // ---- Module 2 (signals) enums ------------------------------------------------------

    @TypeConverter
    fun fromSignalType(value: SignalType): String = value.value
    @TypeConverter
    fun toSignalType(value: String): SignalType = SignalType.from(value)

    @TypeConverter
    fun fromSignalStatus(value: SignalStatus): String = value.value
    @TypeConverter
    fun toSignalStatus(value: String): SignalStatus = SignalStatus.from(value)

    @TypeConverter
    fun fromMarketTrend(value: MarketTrend): String = value.value
    @TypeConverter
    fun toMarketTrend(value: String): MarketTrend = MarketTrend.from(value)

    // ---- Module 3 (analytics) enums are all persisted via Room's built-in enum-as-name
    // handling (Room 2.6+ auto-converts simple enums to/from their `.name`), so no explicit
    // converters are required for TradeStatus, BacktestStatus, InsightCategory, etc. This
    // mirrors how they were already declared across Module 3's entity files.

    // ---- Historical Market Data Platform (schema v5) enums, all persisted as String `value` ----

    @TypeConverter
    fun fromProviderType(value: com.jarvis.tidb.historical.ingestion.entity.ProviderType): String = value.value
    @TypeConverter
    fun toProviderType(value: String): com.jarvis.tidb.historical.ingestion.entity.ProviderType =
        com.jarvis.tidb.historical.ingestion.entity.ProviderType.from(value)

    @TypeConverter
    fun fromIngestionJobType(value: com.jarvis.tidb.historical.ingestion.entity.IngestionJobType): String = value.value
    @TypeConverter
    fun toIngestionJobType(value: String): com.jarvis.tidb.historical.ingestion.entity.IngestionJobType =
        com.jarvis.tidb.historical.ingestion.entity.IngestionJobType.from(value)

    @TypeConverter
    fun fromIngestionJobStatus(value: com.jarvis.tidb.historical.ingestion.entity.IngestionJobStatus): String = value.value
    @TypeConverter
    fun toIngestionJobStatus(value: String): com.jarvis.tidb.historical.ingestion.entity.IngestionJobStatus =
        com.jarvis.tidb.historical.ingestion.entity.IngestionJobStatus.from(value)

    @TypeConverter
    fun fromIngestionEventType(value: com.jarvis.tidb.historical.ingestion.entity.IngestionEventType): String = value.value
    @TypeConverter
    fun toIngestionEventType(value: String): com.jarvis.tidb.historical.ingestion.entity.IngestionEventType =
        com.jarvis.tidb.historical.ingestion.entity.IngestionEventType.from(value)

    @TypeConverter
    fun fromGapStatus(value: com.jarvis.tidb.historical.candle.entity.GapStatus): String = value.value
    @TypeConverter
    fun toGapStatus(value: String): com.jarvis.tidb.historical.candle.entity.GapStatus =
        com.jarvis.tidb.historical.candle.entity.GapStatus.from(value)

    @TypeConverter
    fun fromGapReason(value: com.jarvis.tidb.historical.candle.entity.GapReason): String = value.value
    @TypeConverter
    fun toGapReason(value: String): com.jarvis.tidb.historical.candle.entity.GapReason =
        com.jarvis.tidb.historical.candle.entity.GapReason.from(value)

    @TypeConverter
    fun fromQualityIssueType(value: com.jarvis.tidb.historical.quality.entity.QualityIssueType): String = value.value
    @TypeConverter
    fun toQualityIssueType(value: String): com.jarvis.tidb.historical.quality.entity.QualityIssueType =
        com.jarvis.tidb.historical.quality.entity.QualityIssueType.from(value)

    @TypeConverter
    fun fromIssueSeverity(value: com.jarvis.tidb.historical.quality.entity.IssueSeverity): String = value.value
    @TypeConverter
    fun toIssueSeverity(value: String): com.jarvis.tidb.historical.quality.entity.IssueSeverity =
        com.jarvis.tidb.historical.quality.entity.IssueSeverity.from(value)

    @TypeConverter
    fun fromCorporateActionType(value: com.jarvis.tidb.historical.quality.entity.CorporateActionType): String = value.value
    @TypeConverter
    fun toCorporateActionType(value: String): com.jarvis.tidb.historical.quality.entity.CorporateActionType =
        com.jarvis.tidb.historical.quality.entity.CorporateActionType.from(value)

    @TypeConverter
    fun fromIndicatorType(value: com.jarvis.tidb.historical.indicator.entity.IndicatorType): String = value.value
    @TypeConverter
    fun toIndicatorType(value: String): com.jarvis.tidb.historical.indicator.entity.IndicatorType =
        com.jarvis.tidb.historical.indicator.entity.IndicatorType.from(value)

    @TypeConverter
    fun fromComputationStatus(value: com.jarvis.tidb.historical.indicator.entity.ComputationStatus): String = value.value
    @TypeConverter
    fun toComputationStatus(value: String): com.jarvis.tidb.historical.indicator.entity.ComputationStatus =
        com.jarvis.tidb.historical.indicator.entity.ComputationStatus.from(value)

    @TypeConverter
    fun fromObservationType(value: com.jarvis.tidb.historical.evidence.entity.ObservationType): String = value.value
    @TypeConverter
    fun toObservationType(value: String): com.jarvis.tidb.historical.evidence.entity.ObservationType =
        com.jarvis.tidb.historical.evidence.entity.ObservationType.from(value)

    @TypeConverter
    fun fromEvidenceType(value: com.jarvis.tidb.historical.evidence.entity.EvidenceType): String = value.value
    @TypeConverter
    fun toEvidenceType(value: String): com.jarvis.tidb.historical.evidence.entity.EvidenceType =
        com.jarvis.tidb.historical.evidence.entity.EvidenceType.from(value)

    @TypeConverter
    fun fromPatternOutcome(value: com.jarvis.tidb.historical.evidence.entity.PatternOutcome): String = value.value
    @TypeConverter
    fun toPatternOutcome(value: String): com.jarvis.tidb.historical.evidence.entity.PatternOutcome =
        com.jarvis.tidb.historical.evidence.entity.PatternOutcome.from(value)

    // ---- TRADING-006 (schema v6) — Module 5: Trading Intelligence & Evidence Engine ----
    // All persisted as String `value`, matching the Historical Market Data Platform convention above.

    @TypeConverter
    fun fromEvidenceCategoryLevel(value: com.jarvis.tidb.intelligence.evidence.entity.EvidenceCategoryLevel): String = value.value
    @TypeConverter
    fun toEvidenceCategoryLevel(value: String): com.jarvis.tidb.intelligence.evidence.entity.EvidenceCategoryLevel =
        com.jarvis.tidb.intelligence.evidence.entity.EvidenceCategoryLevel.from(value)

    @TypeConverter
    fun fromEvidenceSourceKind(value: com.jarvis.tidb.intelligence.evidence.entity.EvidenceSourceKind): String = value.value
    @TypeConverter
    fun toEvidenceSourceKind(value: String): com.jarvis.tidb.intelligence.evidence.entity.EvidenceSourceKind =
        com.jarvis.tidb.intelligence.evidence.entity.EvidenceSourceKind.from(value)

    @TypeConverter
    fun fromLinkedEntityType(value: com.jarvis.tidb.intelligence.evidence.entity.LinkedEntityType): String = value.value
    @TypeConverter
    fun toLinkedEntityType(value: String): com.jarvis.tidb.intelligence.evidence.entity.LinkedEntityType =
        com.jarvis.tidb.intelligence.evidence.entity.LinkedEntityType.from(value)

    @TypeConverter
    fun fromEvidenceLinkRole(value: com.jarvis.tidb.intelligence.evidence.entity.EvidenceLinkRole): String = value.value
    @TypeConverter
    fun toEvidenceLinkRole(value: String): com.jarvis.tidb.intelligence.evidence.entity.EvidenceLinkRole =
        com.jarvis.tidb.intelligence.evidence.entity.EvidenceLinkRole.from(value)

    @TypeConverter
    fun fromOutcomeVerdict(value: com.jarvis.tidb.intelligence.evidence.entity.OutcomeVerdict): String = value.value
    @TypeConverter
    fun toOutcomeVerdict(value: String): com.jarvis.tidb.intelligence.evidence.entity.OutcomeVerdict =
        com.jarvis.tidb.intelligence.evidence.entity.OutcomeVerdict.from(value)

    @TypeConverter
    fun fromPatternFamily(value: com.jarvis.tidb.intelligence.pattern.entity.PatternFamily): String = value.value
    @TypeConverter
    fun toPatternFamily(value: String): com.jarvis.tidb.intelligence.pattern.entity.PatternFamily =
        com.jarvis.tidb.intelligence.pattern.entity.PatternFamily.from(value)

    @TypeConverter
    fun fromRegimeType(value: com.jarvis.tidb.intelligence.regime.entity.RegimeType): String = value.value
    @TypeConverter
    fun toRegimeType(value: String): com.jarvis.tidb.intelligence.regime.entity.RegimeType =
        com.jarvis.tidb.intelligence.regime.entity.RegimeType.from(value)

    @TypeConverter
    fun fromConfidenceModelType(value: com.jarvis.tidb.intelligence.confidence.entity.ConfidenceModelType): String = value.value
    @TypeConverter
    fun toConfidenceModelType(value: String): com.jarvis.tidb.intelligence.confidence.entity.ConfidenceModelType =
        com.jarvis.tidb.intelligence.confidence.entity.ConfidenceModelType.from(value)

    @TypeConverter
    fun fromScoredEntityType(value: com.jarvis.tidb.intelligence.confidence.entity.ScoredEntityType): String = value.value
    @TypeConverter
    fun toScoredEntityType(value: String): com.jarvis.tidb.intelligence.confidence.entity.ScoredEntityType =
        com.jarvis.tidb.intelligence.confidence.entity.ScoredEntityType.from(value)

    @TypeConverter
    fun fromHypothesisStatus(value: com.jarvis.tidb.intelligence.research.entity.HypothesisStatus): String = value.value
    @TypeConverter
    fun toHypothesisStatus(value: String): com.jarvis.tidb.intelligence.research.entity.HypothesisStatus =
        com.jarvis.tidb.intelligence.research.entity.HypothesisStatus.from(value)

    @TypeConverter
    fun fromExperimentType(value: com.jarvis.tidb.intelligence.research.entity.ExperimentType): String = value.value
    @TypeConverter
    fun toExperimentType(value: String): com.jarvis.tidb.intelligence.research.entity.ExperimentType =
        com.jarvis.tidb.intelligence.research.entity.ExperimentType.from(value)

    @TypeConverter
    fun fromExperimentStatus(value: com.jarvis.tidb.intelligence.research.entity.ExperimentStatus): String = value.value
    @TypeConverter
    fun toExperimentStatus(value: String): com.jarvis.tidb.intelligence.research.entity.ExperimentStatus =
        com.jarvis.tidb.intelligence.research.entity.ExperimentStatus.from(value)

    @TypeConverter
    fun fromExperimentRunStatus(value: com.jarvis.tidb.intelligence.research.entity.ExperimentRunStatus): String = value.value
    @TypeConverter
    fun toExperimentRunStatus(value: String): com.jarvis.tidb.intelligence.research.entity.ExperimentRunStatus =
        com.jarvis.tidb.intelligence.research.entity.ExperimentRunStatus.from(value)

    @TypeConverter
    fun fromExperimentConclusion(value: com.jarvis.tidb.intelligence.research.entity.ExperimentConclusion?): String? = value?.value
    @TypeConverter
    fun toExperimentConclusion(value: String?): com.jarvis.tidb.intelligence.research.entity.ExperimentConclusion? =
        value?.let { com.jarvis.tidb.intelligence.research.entity.ExperimentConclusion.from(it) }

    @TypeConverter
    fun fromGraphEntityType(value: com.jarvis.tidb.intelligence.graph.entity.GraphEntityType): String = value.value
    @TypeConverter
    fun toGraphEntityType(value: String): com.jarvis.tidb.intelligence.graph.entity.GraphEntityType =
        com.jarvis.tidb.intelligence.graph.entity.GraphEntityType.from(value)

    @TypeConverter
    fun fromRelationshipType(value: com.jarvis.tidb.intelligence.graph.entity.RelationshipType): String = value.value
    @TypeConverter
    fun toRelationshipType(value: String): com.jarvis.tidb.intelligence.graph.entity.RelationshipType =
        com.jarvis.tidb.intelligence.graph.entity.RelationshipType.from(value)

    @TypeConverter
    fun fromCausalDirection(value: com.jarvis.tidb.intelligence.graph.entity.CausalDirection): String = value.value
    @TypeConverter
    fun toCausalDirection(value: String): com.jarvis.tidb.intelligence.graph.entity.CausalDirection =
        com.jarvis.tidb.intelligence.graph.entity.CausalDirection.from(value)

    // ---- TRADING-007A.1 (schema v7) — News & Sentiment Intelligence Platform ----
    // All persisted as String `value`, matching every prior module's convention.
    // Note: `NewsCategoryEntity.level` reuses `EvidenceCategoryLevel` directly (converter
    // already registered above) rather than duplicating a converter for an identical enum.

    @TypeConverter
    fun fromNewsSourceTier(value: com.jarvis.tidb.news.entity.NewsSourceTier): String = value.value
    @TypeConverter
    fun toNewsSourceTier(value: String): com.jarvis.tidb.news.entity.NewsSourceTier =
        com.jarvis.tidb.news.entity.NewsSourceTier.from(value)

    @TypeConverter
    fun fromNewsArticleStatus(value: com.jarvis.tidb.news.entity.NewsArticleStatus): String = value.value
    @TypeConverter
    fun toNewsArticleStatus(value: String): com.jarvis.tidb.news.entity.NewsArticleStatus =
        com.jarvis.tidb.news.entity.NewsArticleStatus.from(value)

    @TypeConverter
    fun fromNewsContentCompleteness(value: com.jarvis.tidb.news.entity.NewsContentCompleteness): String = value.value
    @TypeConverter
    fun toNewsContentCompleteness(value: String): com.jarvis.tidb.news.entity.NewsContentCompleteness =
        com.jarvis.tidb.news.entity.NewsContentCompleteness.from(value)

    @TypeConverter
    fun fromSentimentLabel(value: com.jarvis.tidb.news.entity.SentimentLabel): String = value.value
    @TypeConverter
    fun toSentimentLabel(value: String): com.jarvis.tidb.news.entity.SentimentLabel =
        com.jarvis.tidb.news.entity.SentimentLabel.from(value)
}

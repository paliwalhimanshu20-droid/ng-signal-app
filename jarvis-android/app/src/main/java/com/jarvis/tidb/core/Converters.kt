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
}

package com.jarvis.tidb.historical.dna.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jarvis.tidb.core.common.AuditMetadata
import com.jarvis.tidb.core.common.GlobalId
import com.jarvis.tidb.core.entity.InstrumentEntity

/**
 * HISTORICAL MARKET DATA PLATFORM — Instrument DNA Foundation (schema v5).
 *
 * Pure metadata. Every table here stores *descriptive statistics computed from historical
 * data* (via the Indicator Warehouse and raw candles) — nothing here is a prediction,
 * recommendation, or AI-generated inference. That's explicitly out of scope for this
 * foundation; a future Strategy/AI Learning module reads these tables as input, it doesn't
 * live here. Each facet is its own table (rather than one JSON blob) so profiles are
 * independently queryable, indexable, and re-computable on their own schedule; each also
 * carries a `detailsJson` escape hatch for facet-specific structure that doesn't warrant a
 * dedicated column yet, following the same pattern as `InstrumentEntity.vendorMetadata`.
 */

/** Realized volatility characteristics for one (instrument, timeframe) over a lookback window. */
@Entity(
    tableName = "dna_volatility_profiles",
    foreignKeys = [ForeignKey(entity = InstrumentEntity::class, parentColumns = ["instrumentId"], childColumns = ["instrumentId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)],
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["instrumentId", "timeframe"], unique = true, name = "idx_dna_volatility_unique")]
)
data class VolatilityProfileEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "profileId") val profileId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "instrumentId") val instrumentId: Long,
    @ColumnInfo(name = "timeframe") val timeframe: String,
    @ColumnInfo(name = "lookbackDays") val lookbackDays: Int,
    @ColumnInfo(name = "avgTrueRangePct") val avgTrueRangePct: Double,
    @ColumnInfo(name = "realizedVolatilityAnnualizedPct") val realizedVolatilityAnnualizedPct: Double,
    @ColumnInfo(name = "avgDailyRangePct") val avgDailyRangePct: Double,
    @ColumnInfo(name = "volatilityRegime") val volatilityRegime: String,
    @ColumnInfo(name = "detailsJson") val detailsJson: String? = null,
    @ColumnInfo(name = "computedAt") val computedAt: Long = System.currentTimeMillis(),
    @Embedded val audit: AuditMetadata = AuditMetadata()
)

/** How this instrument behaves across trading sessions (open/mid/close ranges, typical high/low session timing). */
@Entity(
    tableName = "dna_session_behavior_profiles",
    foreignKeys = [ForeignKey(entity = InstrumentEntity::class, parentColumns = ["instrumentId"], childColumns = ["instrumentId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)],
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["instrumentId"], unique = true)]
)
data class SessionBehaviorProfileEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "profileId") val profileId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "instrumentId") val instrumentId: Long,
    @ColumnInfo(name = "openingRangeVolatilityPct") val openingRangeVolatilityPct: Double,
    @ColumnInfo(name = "closingRangeVolatilityPct") val closingRangeVolatilityPct: Double,
    @ColumnInfo(name = "typicalHighTimeOfDayMinutes") val typicalHighTimeOfDayMinutes: Int?,
    @ColumnInfo(name = "typicalLowTimeOfDayMinutes") val typicalLowTimeOfDayMinutes: Int?,
    @ColumnInfo(name = "avgVolumeConcentrationOpenPct") val avgVolumeConcentrationOpenPct: Double,
    @ColumnInfo(name = "avgVolumeConcentrationClosePct") val avgVolumeConcentrationClosePct: Double,
    @ColumnInfo(name = "detailsJson") val detailsJson: String? = null,
    @ColumnInfo(name = "computedAt") val computedAt: Long = System.currentTimeMillis(),
    @Embedded val audit: AuditMetadata = AuditMetadata()
)

/** How long trends tend to persist for this instrument once established, at a given timeframe. */
@Entity(
    tableName = "dna_trend_persistence_profiles",
    foreignKeys = [ForeignKey(entity = InstrumentEntity::class, parentColumns = ["instrumentId"], childColumns = ["instrumentId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)],
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["instrumentId", "timeframe"], unique = true, name = "idx_dna_trend_unique")]
)
data class TrendPersistenceProfileEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "profileId") val profileId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "instrumentId") val instrumentId: Long,
    @ColumnInfo(name = "timeframe") val timeframe: String,
    @ColumnInfo(name = "avgTrendDurationBars") val avgTrendDurationBars: Double,
    @ColumnInfo(name = "avgTrendMagnitudePct") val avgTrendMagnitudePct: Double,
    @ColumnInfo(name = "meanReversionTendencyScore") val meanReversionTendencyScore: Double,
    @ColumnInfo(name = "trendingPctOfTime") val trendingPctOfTime: Double,
    @ColumnInfo(name = "detailsJson") val detailsJson: String? = null,
    @ColumnInfo(name = "computedAt") val computedAt: Long = System.currentTimeMillis(),
    @Embedded val audit: AuditMetadata = AuditMetadata()
)

/** Depth/tradability characteristics inferred from volume and (when available) open interest. */
@Entity(
    tableName = "dna_liquidity_profiles",
    foreignKeys = [ForeignKey(entity = InstrumentEntity::class, parentColumns = ["instrumentId"], childColumns = ["instrumentId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)],
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["instrumentId"], unique = true)]
)
data class LiquidityProfileEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "profileId") val profileId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "instrumentId") val instrumentId: Long,
    @ColumnInfo(name = "avgDailyVolume") val avgDailyVolume: Long,
    @ColumnInfo(name = "avgDailyOpenInterest") val avgDailyOpenInterest: Long?,
    @ColumnInfo(name = "volumeStabilityScore") val volumeStabilityScore: Double,
    @ColumnInfo(name = "liquidityScore") val liquidityScore: Double,
    @ColumnInfo(name = "illiquidSessionPct") val illiquidSessionPct: Double,
    @ColumnInfo(name = "detailsJson") val detailsJson: String? = null,
    @ColumnInfo(name = "computedAt") val computedAt: Long = System.currentTimeMillis(),
    @Embedded val audit: AuditMetadata = AuditMetadata()
)

/** Overnight/inter-session gap statistics. */
@Entity(
    tableName = "dna_gap_behavior_profiles",
    foreignKeys = [ForeignKey(entity = InstrumentEntity::class, parentColumns = ["instrumentId"], childColumns = ["instrumentId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)],
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["instrumentId"], unique = true)]
)
data class GapBehaviorProfileEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "profileId") val profileId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "instrumentId") val instrumentId: Long,
    @ColumnInfo(name = "avgGapPct") val avgGapPct: Double,
    @ColumnInfo(name = "gapFrequencyPct") val gapFrequencyPct: Double,
    @ColumnInfo(name = "gapFillRatePct") val gapFillRatePct: Double,
    @ColumnInfo(name = "avgGapFillDurationBars") val avgGapFillDurationBars: Double?,
    @ColumnInfo(name = "detailsJson") val detailsJson: String? = null,
    @ColumnInfo(name = "computedAt") val computedAt: Long = System.currentTimeMillis(),
    @Embedded val audit: AuditMetadata = AuditMetadata()
)

/** Recurring calendar tendencies (day-of-week / month-of-year), one row per (instrument, bucketType, bucketValue). */
@Entity(
    tableName = "dna_seasonal_tendencies",
    foreignKeys = [ForeignKey(entity = InstrumentEntity::class, parentColumns = ["instrumentId"], childColumns = ["instrumentId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["instrumentId", "bucketType", "bucketValue"], unique = true, name = "idx_dna_seasonal_unique")
    ]
)
data class SeasonalTendencyEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "tendencyId") val tendencyId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "instrumentId") val instrumentId: Long,
    /** "DAY_OF_WEEK", "MONTH_OF_YEAR", or "TRADING_SESSION". */
    @ColumnInfo(name = "bucketType") val bucketType: String,
    /** e.g. "MONDAY", "JANUARY", "PRE_OPEN" — the specific bucket within [bucketType]. */
    @ColumnInfo(name = "bucketValue") val bucketValue: String,
    @ColumnInfo(name = "avgReturnPct") val avgReturnPct: Double,
    @ColumnInfo(name = "positiveOccurrencePct") val positiveOccurrencePct: Double,
    @ColumnInfo(name = "sampleSize") val sampleSize: Int,
    @ColumnInfo(name = "detailsJson") val detailsJson: String? = null,
    @ColumnInfo(name = "computedAt") val computedAt: Long = System.currentTimeMillis(),
    @Embedded val audit: AuditMetadata = AuditMetadata()
)

/** How this instrument's price tends to interact with specific indicators (e.g. RSI overbought/oversold hit rates). References the Indicator Warehouse definition it was computed against. */
@Entity(
    tableName = "dna_indicator_behavior_profiles",
    foreignKeys = [
        ForeignKey(entity = InstrumentEntity::class, parentColumns = ["instrumentId"], childColumns = ["instrumentId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(
            entity = com.jarvis.tidb.historical.indicator.entity.IndicatorDefinitionEntity::class,
            parentColumns = ["indicatorDefId"], childColumns = ["indicatorDefId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["indicatorDefId"]),
        Index(value = ["instrumentId", "indicatorDefId", "timeframe"], unique = true, name = "idx_dna_indicator_behavior_unique")
    ]
)
data class IndicatorBehaviorProfileEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "profileId") val profileId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "instrumentId") val instrumentId: Long,
    @ColumnInfo(name = "indicatorDefId") val indicatorDefId: Long,
    @ColumnInfo(name = "timeframe") val timeframe: String,
    @ColumnInfo(name = "meanReversionAfterExtremePct") val meanReversionAfterExtremePct: Double,
    @ColumnInfo(name = "avgReactionMagnitudePct") val avgReactionMagnitudePct: Double,
    @ColumnInfo(name = "falseSignalRatePct") val falseSignalRatePct: Double,
    @ColumnInfo(name = "detailsJson") val detailsJson: String? = null,
    @ColumnInfo(name = "computedAt") val computedAt: Long = System.currentTimeMillis(),
    @Embedded val audit: AuditMetadata = AuditMetadata()
)

/** General-purpose statistical summary that doesn't fit the other facets (distribution shape, autocorrelation, etc.). */
@Entity(
    tableName = "dna_statistical_characteristics",
    foreignKeys = [ForeignKey(entity = InstrumentEntity::class, parentColumns = ["instrumentId"], childColumns = ["instrumentId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)],
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["instrumentId", "timeframe"], unique = true, name = "idx_dna_statistical_unique")]
)
data class StatisticalCharacteristicsEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "characteristicId") val characteristicId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "instrumentId") val instrumentId: Long,
    @ColumnInfo(name = "timeframe") val timeframe: String,
    @ColumnInfo(name = "returnSkewness") val returnSkewness: Double,
    @ColumnInfo(name = "returnKurtosis") val returnKurtosis: Double,
    @ColumnInfo(name = "autocorrelationLag1") val autocorrelationLag1: Double,
    @ColumnInfo(name = "sampleSize") val sampleSize: Int,
    @ColumnInfo(name = "detailsJson") val detailsJson: String? = null,
    @ColumnInfo(name = "computedAt") val computedAt: Long = System.currentTimeMillis(),
    @Embedded val audit: AuditMetadata = AuditMetadata()
)

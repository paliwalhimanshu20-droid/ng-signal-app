package com.jarvis.tidb.intelligence.regime.entity

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
 * TRADING-006 — Module 5 Phase 2: Market Regime tracking.
 *
 * Entirely new — Module 3's `InsightCategory.MARKET_REGIME` (see
 * `analytics.entity.LearningEntities.kt`) is only a classification tag on a learning insight;
 * no table previously recorded regime state itself. [MarketRegimeEntity] records a regime span
 * for an instrument/timeframe; [RegimeObservationEntity] records the individual metric readings
 * that supported classifying (or later reclassifying) that span.
 */

enum class RegimeType(val value: String) {
    TRENDING_UP("TRENDING_UP"),
    TRENDING_DOWN("TRENDING_DOWN"),
    RANGE_BOUND("RANGE_BOUND"),
    HIGH_VOLATILITY("HIGH_VOLATILITY"),
    LOW_VOLATILITY("LOW_VOLATILITY"),
    BREAKOUT("BREAKOUT"),
    REVERSAL("REVERSAL"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun from(value: String): RegimeType = entries.firstOrNull { it.value == value } ?: UNKNOWN
    }
}

/** A classified span of market behavior for one instrument/timeframe. `endTimestamp = null` means the regime is still considered active. */
@Entity(
    tableName = "market_regimes",
    foreignKeys = [
        ForeignKey(
            entity = InstrumentEntity::class,
            parentColumns = ["instrumentId"],
            childColumns = ["instrumentId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["instrumentId", "timeframe", "startTimestamp"]),
        Index(value = ["regimeType"]),
        Index(value = ["instrumentId", "timeframe", "endTimestamp"])
    ]
)
data class MarketRegimeEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "regimeId") val regimeId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "instrumentId") val instrumentId: Long,
    @ColumnInfo(name = "timeframe") val timeframe: String,
    @ColumnInfo(name = "regimeType") val regimeType: RegimeType,
    @ColumnInfo(name = "startTimestamp") val startTimestamp: Long,
    /** Null while the regime is still considered in effect. */
    @ColumnInfo(name = "endTimestamp") val endTimestamp: Long? = null,
    /** Descriptive classification confidence in [0.0, 1.0] — not a probability of continuation. */
    @ColumnInfo(name = "confidence") val confidence: Double,
    @ColumnInfo(name = "description") val description: String? = null,
    @Embedded val audit: AuditMetadata = AuditMetadata()
)

/** One supporting metric reading behind a [MarketRegimeEntity] classification (or reclassification). */
@Entity(
    tableName = "regime_observations",
    foreignKeys = [
        ForeignKey(
            entity = MarketRegimeEntity::class,
            parentColumns = ["regimeId"],
            childColumns = ["regimeId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["regimeId"]),
        Index(value = ["observedAt"])
    ]
)
data class RegimeObservationEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "regimeObservationId") val regimeObservationId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "regimeId") val regimeId: Long,
    @ColumnInfo(name = "observedAt") val observedAt: Long = System.currentTimeMillis(),
    /** e.g. "ADX", "ATR_PERCENTILE", "REALIZED_VOLATILITY". */
    @ColumnInfo(name = "supportingMetric") val supportingMetric: String,
    @ColumnInfo(name = "metricValue") val metricValue: Double,
    @ColumnInfo(name = "notes") val notes: String? = null
)

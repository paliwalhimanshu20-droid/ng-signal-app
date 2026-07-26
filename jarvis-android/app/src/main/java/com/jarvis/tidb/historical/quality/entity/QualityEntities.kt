package com.jarvis.tidb.historical.quality.entity

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
 * HISTORICAL MARKET DATA PLATFORM — Data Quality Engine (schema v5).
 *
 * Validates candle data already stored by Module 1 for: missing candles, duplicates, timestamp
 * continuity, OHLC internal consistency, volume anomalies, and (where applicable) corporate
 * actions. Produces a scored, queryable report per validation run rather than a pass/fail flag,
 * so quality can be tracked over time and consumed by downstream modules (Indicator Warehouse,
 * Instrument DNA) as a weighting input.
 */

enum class QualityIssueType(val value: String) {
    MISSING_CANDLE("MISSING_CANDLE"),
    DUPLICATE_CANDLE("DUPLICATE_CANDLE"),
    TIMESTAMP_DISCONTINUITY("TIMESTAMP_DISCONTINUITY"),
    OHLC_INCONSISTENT("OHLC_INCONSISTENT"),
    VOLUME_ANOMALY("VOLUME_ANOMALY"),
    UNADJUSTED_CORPORATE_ACTION("UNADJUSTED_CORPORATE_ACTION"),
    STALE_PRICE("STALE_PRICE");

    companion object {
        fun from(value: String): QualityIssueType = entries.firstOrNull { it.value == value } ?: MISSING_CANDLE
    }
}

enum class IssueSeverity(val value: String) {
    INFO("INFO"),
    WARNING("WARNING"),
    CRITICAL("CRITICAL");

    companion object {
        fun from(value: String): IssueSeverity = entries.firstOrNull { it.value == value } ?: WARNING
    }
}

enum class CorporateActionType(val value: String) {
    SPLIT("SPLIT"),
    BONUS("BONUS"),
    DIVIDEND("DIVIDEND"),
    MERGER("MERGER"),
    SYMBOL_CHANGE("SYMBOL_CHANGE"),
    CONTRACT_ROLLOVER("CONTRACT_ROLLOVER");

    companion object {
        fun from(value: String): CorporateActionType = entries.firstOrNull { it.value == value } ?: SPLIT
    }
}

/** Summary + score for one validation pass over a (instrument, timeframe, period) window. */
@Entity(
    tableName = "candle_quality_reports",
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
        Index(value = ["instrumentId", "timeframe"]),
        Index(value = ["generatedAt"])
    ]
)
data class CandleQualityReportEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "reportId")
    val reportId: Long = 0L,

    @ColumnInfo(name = "uuid")
    val uuid: String = GlobalId.new(),

    @ColumnInfo(name = "instrumentId")
    val instrumentId: Long,

    @ColumnInfo(name = "timeframe")
    val timeframe: String,

    @ColumnInfo(name = "periodStart")
    val periodStart: Long,

    @ColumnInfo(name = "periodEnd")
    val periodEnd: Long,

    @ColumnInfo(name = "expectedCandleCount")
    val expectedCandleCount: Int,

    @ColumnInfo(name = "actualCandleCount")
    val actualCandleCount: Int,

    @ColumnInfo(name = "missingCount")
    val missingCount: Int = 0,

    @ColumnInfo(name = "duplicateCount")
    val duplicateCount: Int = 0,

    @ColumnInfo(name = "ohlcViolationCount")
    val ohlcViolationCount: Int = 0,

    @ColumnInfo(name = "volumeAnomalyCount")
    val volumeAnomalyCount: Int = 0,

    @ColumnInfo(name = "timestampDiscontinuityCount")
    val timestampDiscontinuityCount: Int = 0,

    /** 0.0 (unusable) – 1.0 (perfect). Same scale as [com.jarvis.tidb.core.entity.HistoricalCandleEntity.qualityScore]. */
    @ColumnInfo(name = "qualityScore")
    val qualityScore: Double,

    @ColumnInfo(name = "generatedAt")
    val generatedAt: Long = System.currentTimeMillis(),

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)

/** One specific finding within a [CandleQualityReportEntity], optionally pointing at the offending candle. */
@Entity(
    tableName = "quality_issues",
    foreignKeys = [
        ForeignKey(
            entity = CandleQualityReportEntity::class,
            parentColumns = ["reportId"],
            childColumns = ["reportId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["reportId"]),
        Index(value = ["issueType"]),
        Index(value = ["severity"]),
        Index(value = ["resolved"]),
        Index(value = ["candleId"])
    ]
)
data class QualityIssueEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "issueId")
    val issueId: Long = 0L,

    @ColumnInfo(name = "reportId")
    val reportId: Long,

    @ColumnInfo(name = "issueType")
    val issueType: QualityIssueType,

    @ColumnInfo(name = "severity")
    val severity: IssueSeverity,

    /** Logical-only reference (Room FK deliberately omitted so a corrected/deleted candle doesn't cascade-delete history of the issue). */
    @ColumnInfo(name = "candleId")
    val candleId: Long? = null,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long? = null,

    @ColumnInfo(name = "details")
    val details: String,

    @ColumnInfo(name = "resolved")
    val resolved: Boolean = false,

    @ColumnInfo(name = "resolvedAt")
    val resolvedAt: Long? = null,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis()
)

/** A corporate action affecting an instrument's historical series (splits, dividends, rollovers, etc.). Applicable mainly to equities/indices; harmless no-op table for pure commodity futures. */
@Entity(
    tableName = "corporate_actions",
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
        Index(value = ["instrumentId"]),
        Index(value = ["effectiveDate"]),
        Index(value = ["applied"])
    ]
)
data class CorporateActionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "actionId")
    val actionId: Long = 0L,

    @ColumnInfo(name = "uuid")
    val uuid: String = GlobalId.new(),

    @ColumnInfo(name = "instrumentId")
    val instrumentId: Long,

    @ColumnInfo(name = "actionType")
    val actionType: CorporateActionType,

    @ColumnInfo(name = "effectiveDate")
    val effectiveDate: Long,

    /** Adjustment ratio to apply to pre-action historical prices (e.g. 0.5 for a 2:1 split). Null for non-ratio actions (e.g. symbol change). */
    @ColumnInfo(name = "adjustmentRatio")
    val adjustmentRatio: Double? = null,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "source")
    val source: String,

    /** Whether historical candles have already been back-adjusted for this action. */
    @ColumnInfo(name = "applied")
    val applied: Boolean = false,

    @ColumnInfo(name = "appliedAt")
    val appliedAt: Long? = null,

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)

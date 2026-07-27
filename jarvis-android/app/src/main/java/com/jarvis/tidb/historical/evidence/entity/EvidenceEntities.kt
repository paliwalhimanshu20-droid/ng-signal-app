package com.jarvis.tidb.historical.evidence.entity

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
 * HISTORICAL MARKET DATA PLATFORM — Evidence Foundation (schema v5).
 *
 * Pure database structure for recording *what was observed and why it might matter* —
 * observations, evidence, detected pattern occurrences, what indicators supported a piece of
 * evidence, and how confidence in it was composed. There is NO decision engine here: nothing
 * in this package scores a trade, issues a signal, or recommends an action. That is explicitly
 * future scope (a Strategy/Decision module would read these tables, not extend them).
 */

enum class ObservationType(val value: String) {
    PRICE_ACTION("PRICE_ACTION"),
    INDICATOR_STATE("INDICATOR_STATE"),
    VOLUME_BEHAVIOR("VOLUME_BEHAVIOR"),
    PATTERN_MATCH("PATTERN_MATCH"),
    QUALITY_EVENT("QUALITY_EVENT"),
    DNA_DEVIATION("DNA_DEVIATION");

    companion object {
        fun from(value: String): ObservationType = entries.firstOrNull { it.value == value } ?: PRICE_ACTION
    }
}

enum class EvidenceType(val value: String) {
    SUPPORTING("SUPPORTING"),
    CONTRADICTING("CONTRADICTING"),
    NEUTRAL("NEUTRAL");

    companion object {
        fun from(value: String): EvidenceType = entries.firstOrNull { it.value == value } ?: NEUTRAL
    }
}

enum class PatternOutcome(val value: String) {
    PENDING("PENDING"),
    CONFIRMED("CONFIRMED"),
    INVALIDATED("INVALIDATED"),
    EXPIRED("EXPIRED");

    companion object {
        fun from(value: String): PatternOutcome = entries.firstOrNull { it.value == value } ?: PENDING
    }
}

/** A single recorded observation about an instrument at a point in time — the atomic unit evidence is built from. */
@Entity(
    tableName = "market_observations",
    foreignKeys = [ForeignKey(entity = InstrumentEntity::class, parentColumns = ["instrumentId"], childColumns = ["instrumentId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["instrumentId", "timestamp"]),
        Index(value = ["observationType"])
    ]
)
data class MarketObservationEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "observationId") val observationId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "instrumentId") val instrumentId: Long,
    @ColumnInfo(name = "timeframe") val timeframe: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "observationType") val observationType: ObservationType,
    @ColumnInfo(name = "description") val description: String,
    /** Where this observation came from — "QUALITY_ENGINE", "INDICATOR_WAREHOUSE", "DNA_FOUNDATION", "MANUAL", etc. */
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "detailsJson") val detailsJson: String? = null,
    @Embedded val audit: AuditMetadata = AuditMetadata()
)

/** A weighted piece of evidence built from one or more observations. Still just a record — no scoring/decisioning happens here. */
@Entity(
    tableName = "evidence_records",
    foreignKeys = [
        ForeignKey(entity = InstrumentEntity::class, parentColumns = ["instrumentId"], childColumns = ["instrumentId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = MarketObservationEntity::class, parentColumns = ["observationId"], childColumns = ["observationId"], onDelete = ForeignKey.SET_NULL, onUpdate = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["instrumentId", "timestamp"]),
        Index(value = ["observationId"]),
        Index(value = ["evidenceType"])
    ]
)
data class EvidenceRecordEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "evidenceId") val evidenceId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "instrumentId") val instrumentId: Long,
    @ColumnInfo(name = "observationId") val observationId: Long? = null,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "evidenceType") val evidenceType: EvidenceType,
    /** Descriptive strength in [0.0, 1.0] — how strongly this evidence leans, not a probability of correctness. */
    @ColumnInfo(name = "strength") val strength: Double,
    @ColumnInfo(name = "description") val description: String,
    @Embedded val audit: AuditMetadata = AuditMetadata()
)

/** A detected occurrence of a named pattern (candlestick, chart, or statistical pattern) over a time range. */
@Entity(
    tableName = "pattern_occurrences",
    foreignKeys = [ForeignKey(entity = InstrumentEntity::class, parentColumns = ["instrumentId"], childColumns = ["instrumentId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["instrumentId", "timeframe", "startTimestamp"]),
        Index(value = ["patternName"]),
        Index(value = ["outcome"])
    ]
)
data class PatternOccurrenceEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "occurrenceId") val occurrenceId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "instrumentId") val instrumentId: Long,
    @ColumnInfo(name = "timeframe") val timeframe: String,
    @ColumnInfo(name = "patternName") val patternName: String,
    @ColumnInfo(name = "startTimestamp") val startTimestamp: Long,
    @ColumnInfo(name = "endTimestamp") val endTimestamp: Long?,
    /** Descriptive match quality in [0.0, 1.0] against the pattern's canonical definition — not a probability of a favorable outcome. */
    @ColumnInfo(name = "matchConfidence") val matchConfidence: Double,
    @ColumnInfo(name = "outcome") val outcome: PatternOutcome = PatternOutcome.PENDING,
    @ColumnInfo(name = "outcomeNotes") val outcomeNotes: String? = null,
    /**
     * TRADING-006 (schema v6) — additive nullable logical-only backlink to
     * `intelligence.pattern.entity.PatternEntity.patternId` (the `patterns` table), added via
     * `MIGRATION_5_6`'s `ALTER TABLE pattern_occurrences ADD COLUMN patternId INTEGER`. Not a
     * Room `@ForeignKey` — see `intelligence/pattern/entity/PatternEntities.kt` for why.
     * `patternName` (above) remains the original Module 4 field and is untouched.
     */
    @ColumnInfo(name = "patternId") val patternId: Long? = null,
    @Embedded val audit: AuditMetadata = AuditMetadata()
)

/** Links an [IndicatorValueEntity]-backed reading to a piece of evidence it supports, with a per-link contribution weight. */
@Entity(
    tableName = "supporting_indicators",
    foreignKeys = [
        ForeignKey(entity = EvidenceRecordEntity::class, parentColumns = ["evidenceId"], childColumns = ["evidenceId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(
            entity = com.jarvis.tidb.historical.indicator.entity.IndicatorValueEntity::class,
            parentColumns = ["indicatorValueId"], childColumns = ["indicatorValueId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["evidenceId"]), Index(value = ["indicatorValueId"])]
)
data class SupportingIndicatorEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "supportingIndicatorId") val supportingIndicatorId: Long = 0L,
    @ColumnInfo(name = "evidenceId") val evidenceId: Long,
    @ColumnInfo(name = "indicatorValueId") val indicatorValueId: Long,
    /** How much this specific indicator reading contributed to the evidence's overall [EvidenceRecordEntity.strength], in [0.0, 1.0]. */
    @ColumnInfo(name = "contributionWeight") val contributionWeight: Double,
    @ColumnInfo(name = "notes") val notes: String? = null
)

/** One named component of how confidence in a piece of evidence was composed (breakdown, not a final score). */
@Entity(
    tableName = "confidence_components",
    foreignKeys = [ForeignKey(entity = EvidenceRecordEntity::class, parentColumns = ["evidenceId"], childColumns = ["evidenceId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)],
    indices = [Index(value = ["evidenceId"])]
)
data class ConfidenceComponentEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "componentId") val componentId: Long = 0L,
    @ColumnInfo(name = "evidenceId") val evidenceId: Long,
    /** e.g. "DATA_QUALITY", "INDICATOR_ALIGNMENT", "PATTERN_MATCH_STRENGTH", "DNA_CONSISTENCY". */
    @ColumnInfo(name = "componentName") val componentName: String,
    @ColumnInfo(name = "weight") val weight: Double,
    @ColumnInfo(name = "score") val score: Double,
    @ColumnInfo(name = "rationale") val rationale: String
)

/** Where a piece of evidence's underlying facts came from — traceability, not a UI concern. */
@Entity(
    tableName = "source_references",
    foreignKeys = [ForeignKey(entity = EvidenceRecordEntity::class, parentColumns = ["evidenceId"], childColumns = ["evidenceId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)],
    indices = [Index(value = ["evidenceId"])]
)
data class SourceReferenceEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "sourceRefId") val sourceRefId: Long = 0L,
    @ColumnInfo(name = "evidenceId") val evidenceId: Long,
    /** "CANDLE", "INDICATOR_VALUE", "QUALITY_REPORT", "DNA_PROFILE", "CORPORATE_ACTION", "EXTERNAL". */
    @ColumnInfo(name = "sourceType") val sourceType: String,
    /** Logical-only reference into the relevant table's primary key; deliberately not a Room FK since sourceType varies per row. */
    @ColumnInfo(name = "sourceRowId") val sourceRowId: Long? = null,
    @ColumnInfo(name = "description") val description: String? = null,
    @ColumnInfo(name = "url") val url: String? = null
)

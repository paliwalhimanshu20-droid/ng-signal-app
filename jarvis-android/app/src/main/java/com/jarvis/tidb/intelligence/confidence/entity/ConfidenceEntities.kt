package com.jarvis.tidb.intelligence.confidence.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jarvis.tidb.core.common.AuditMetadata
import com.jarvis.tidb.core.common.GlobalId

/**
 * TRADING-006 — Module 5 Phase 2: Confidence modeling.
 *
 * [com.jarvis.tidb.historical.evidence.entity.ConfidenceComponentEntity] (Module 4) already
 * records a per-evidence-row confidence *breakdown* — it is intentionally kept as-is and is not
 * touched here. What is new: [ConfidenceModelEntity], a reusable, named methodology definition
 * (so "how confidence gets composed" can itself be versioned, compared, and referenced), and
 * [ConfidenceScoreEntity], a final composed score that — unlike `ConfidenceComponentEntity`,
 * which only ever attaches to an `evidence_records` row — can attach to *any* scoreable entity
 * in the system (a pattern occurrence, a regime observation, a hypothesis, ...) via a
 * polymorphic reference, consistent with the pattern established by `EvidenceLinkEntity` /
 * `LearningEvidenceLinkEntity`.
 */

enum class ConfidenceModelType(val value: String) {
    WEIGHTED_SUM("WEIGHTED_SUM"),
    BAYESIAN("BAYESIAN"),
    RULE_BASED("RULE_BASED"),
    ML_INFERENCE("ML_INFERENCE"),
    HYBRID("HYBRID");

    companion object {
        fun from(value: String): ConfidenceModelType = entries.firstOrNull { it.value == value } ?: RULE_BASED
    }
}

/** A named, versionable methodology for composing a confidence score. Pure definition — no scoring logic lives in the database layer. */
@Entity(
    tableName = "confidence_models",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["modelKey"], unique = true),
        Index(value = ["isActive"])
    ]
)
data class ConfidenceModelEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "modelId") val modelId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "modelKey") val modelKey: String,
    @ColumnInfo(name = "displayName") val displayName: String,
    @ColumnInfo(name = "modelType") val modelType: ConfidenceModelType,
    @ColumnInfo(name = "description") val description: String,
    /** Model parameters (weights, priors, thresholds) as JSON. */
    @ColumnInfo(name = "parametersJson") val parametersJson: String? = null,
    @ColumnInfo(name = "isActive") val isActive: Boolean = true,
    @Embedded val audit: AuditMetadata = AuditMetadata()
)

/** What kind of row a [ConfidenceScoreEntity] was computed for. */
enum class ScoredEntityType(val value: String) {
    EVIDENCE_RECORD("EVIDENCE_RECORD"),
    PATTERN_OCCURRENCE("PATTERN_OCCURRENCE"),
    REGIME_OBSERVATION("REGIME_OBSERVATION"),
    HYPOTHESIS("HYPOTHESIS"),
    TRADE("TRADE"),
    SIGNAL("SIGNAL");

    companion object {
        fun from(value: String): ScoredEntityType = entries.firstOrNull { it.value == value } ?: EVIDENCE_RECORD
    }
}

/** A final, composed confidence score for any scoreable entity, produced by a [ConfidenceModelEntity]. */
@Entity(
    tableName = "confidence_scores",
    foreignKeys = [
        ForeignKey(
            entity = ConfidenceModelEntity::class,
            parentColumns = ["modelId"],
            childColumns = ["modelId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["modelId"]),
        Index(value = ["scoredEntityType", "scoredEntityRowId"]),
        Index(value = ["computedAt"])
    ]
)
data class ConfidenceScoreEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "scoreId") val scoreId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "modelId") val modelId: Long,
    @ColumnInfo(name = "scoredEntityType") val scoredEntityType: ScoredEntityType,
    @ColumnInfo(name = "scoredEntityRowId") val scoredEntityRowId: Long,
    /** Final composed score in [0.0, 1.0]. */
    @ColumnInfo(name = "score") val score: Double,
    /** Per-component contributions as JSON, e.g. `[{"component":"DATA_QUALITY","weight":0.3,"score":0.8}]`. */
    @ColumnInfo(name = "breakdownJson") val breakdownJson: String? = null,
    @ColumnInfo(name = "computedAt") val computedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "notes") val notes: String? = null
)

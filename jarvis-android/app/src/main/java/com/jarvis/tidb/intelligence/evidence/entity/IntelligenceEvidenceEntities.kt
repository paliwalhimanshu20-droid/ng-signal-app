package com.jarvis.tidb.intelligence.evidence.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jarvis.tidb.core.common.AuditMetadata
import com.jarvis.tidb.core.common.GlobalId
import com.jarvis.tidb.historical.evidence.entity.EvidenceRecordEntity

/**
 * TRADING-006 — Module 5: Trading Intelligence & Evidence Engine — Evidence Foundation
 * extensions (schema v6).
 *
 * Module 4 (TRADING-005, schema v5, package `historical.evidence`) already built the core
 * Evidence Foundation: [EvidenceRecordEntity] *is* "an evidence record" and
 * [com.jarvis.tidb.historical.evidence.entity.SourceReferenceEntity] already captures
 * per-evidence-row provenance. This file does NOT redefine either — see
 * `docs/database/TRADING-006-Trading-Intelligence-Evidence-Engine.md` §1 for the full
 * reconciliation. What genuinely did not exist yet and is added here:
 *
 * - [EvidenceCategoryEntity] — a reusable taxonomy for evidence, orthogonal to the fixed
 *   3-value [com.jarvis.tidb.historical.evidence.entity.EvidenceType] enum (supporting/
 *   contradicting/neutral). A category is "what kind of thing is this evidence about"
 *   (e.g. "PRICE_STRUCTURE", "VOLUME_ANOMALY"), not "which way does it lean."
 * - [EvidenceSourceEntity] — a named, reusable *source registry* (with a trust weight and a
 *   kind), distinct in purpose from `SourceReferenceEntity`, which is a per-row citation
 *   pointing at one physical fact behind one piece of evidence. `SourceReferenceEntity` and
 *   `MarketObservationEntity.source` reference this table's [EvidenceSourceEntity.sourceCode]
 *   logically (by convention, not a Room FK, to avoid touching the Module 4 tables).
 * - [EvidenceLinkEntity] — the generic case that
 *   [com.jarvis.tidb.historical.evidence.entity.SupportingIndicatorEntity] deliberately does
 *   NOT cover: linking a piece of evidence to *any* other row in the system (a trade, a
 *   hypothesis, a pattern occurrence, a regime observation, ...), mirroring the polymorphic
 *   `sourceType`/`sourceRowId` convention already established by
 *   [com.jarvis.tidb.analytics.entity.LearningEvidenceLinkEntity].
 * - [EvidenceOutcomeEntity] — generalizes
 *   [com.jarvis.tidb.historical.evidence.entity.PatternOccurrenceEntity]'s single mutable
 *   `outcome` field to *any* evidence record, and allows more than one evaluation checkpoint
 *   over time (e.g. "still pending after 3 sessions", "confirmed after 5 sessions") rather than
 *   one terminal value.
 */

enum class EvidenceCategoryLevel(val value: String) {
    ROOT("ROOT"),
    SUBCATEGORY("SUBCATEGORY");

    companion object {
        fun from(value: String): EvidenceCategoryLevel = entries.firstOrNull { it.value == value } ?: ROOT
    }
}

/** A reusable, hierarchical classification for evidence — orthogonal to [com.jarvis.tidb.historical.evidence.entity.EvidenceType]. */
@Entity(
    tableName = "evidence_categories",
    foreignKeys = [
        ForeignKey(
            entity = EvidenceCategoryEntity::class,
            parentColumns = ["categoryId"],
            childColumns = ["parentCategoryId"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["code"], unique = true),
        Index(value = ["parentCategoryId"])
    ]
)
data class EvidenceCategoryEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "categoryId") val categoryId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    /** Stable machine slug, e.g. "PRICE_STRUCTURE", "VOLUME_ANOMALY", "DNA_DEVIATION". */
    @ColumnInfo(name = "code") val code: String,
    @ColumnInfo(name = "displayName") val displayName: String,
    @ColumnInfo(name = "description") val description: String? = null,
    @ColumnInfo(name = "level") val level: EvidenceCategoryLevel = EvidenceCategoryLevel.ROOT,
    @ColumnInfo(name = "parentCategoryId") val parentCategoryId: Long? = null,
    @ColumnInfo(name = "isActive") val isActive: Boolean = true,
    @Embedded val audit: AuditMetadata = AuditMetadata()
)

enum class EvidenceSourceKind(val value: String) {
    SYSTEM_ENGINE("SYSTEM_ENGINE"),
    EXTERNAL_FEED("EXTERNAL_FEED"),
    MANUAL_ENTRY("MANUAL_ENTRY"),
    AI_INFERENCE("AI_INFERENCE");

    companion object {
        fun from(value: String): EvidenceSourceKind = entries.firstOrNull { it.value == value } ?: MANUAL_ENTRY
    }
}

/** A named, reusable registry entry for "where evidence comes from" — a catalog, not a per-row citation. */
@Entity(
    tableName = "evidence_sources",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["sourceCode"], unique = true),
        Index(value = ["isActive"])
    ]
)
data class EvidenceSourceEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "sourceId") val sourceId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    /** Stable machine slug — by convention this is what populates `MarketObservationEntity.source` and `SourceReferenceEntity.sourceType`. */
    @ColumnInfo(name = "sourceCode") val sourceCode: String,
    @ColumnInfo(name = "displayName") val displayName: String,
    @ColumnInfo(name = "sourceKind") val sourceKind: EvidenceSourceKind,
    /** Default trust multiplier in [0.0, 1.0] applied when this source's evidence is composed into a confidence score. */
    @ColumnInfo(name = "reliabilityWeight") val reliabilityWeight: Double = 1.0,
    @ColumnInfo(name = "description") val description: String? = null,
    @ColumnInfo(name = "isActive") val isActive: Boolean = true,
    @Embedded val audit: AuditMetadata = AuditMetadata()
)

/** What kind of row an [EvidenceLinkEntity] points at — deliberately open-ended across every Module 5 concept. */
enum class LinkedEntityType(val value: String) {
    TRADE("TRADE"),
    SIGNAL("SIGNAL"),
    BACKTEST_RUN("BACKTEST_RUN"),
    HYPOTHESIS("HYPOTHESIS"),
    EXPERIMENT("EXPERIMENT"),
    EXPERIMENT_RESULT("EXPERIMENT_RESULT"),
    PATTERN_OCCURRENCE("PATTERN_OCCURRENCE"),
    REGIME_OBSERVATION("REGIME_OBSERVATION"),
    LEARNING_INSIGHT("LEARNING_INSIGHT"),
    CAUSAL_OBSERVATION("CAUSAL_OBSERVATION"),
    CORRELATION("CORRELATION");

    companion object {
        fun from(value: String): LinkedEntityType = entries.firstOrNull { it.value == value } ?: TRADE
    }
}

enum class EvidenceLinkRole(val value: String) {
    SUPPORTS("SUPPORTS"),
    CONTRADICTS("CONTRADICTS"),
    CONTEXTUALIZES("CONTEXTUALIZES"),
    TRIGGERED_BY("TRIGGERED_BY");

    companion object {
        fun from(value: String): EvidenceLinkRole = entries.firstOrNull { it.value == value } ?: SUPPORTS
    }
}

/**
 * Generic link from one [EvidenceRecordEntity] to any other row in the system. `linkedEntityType`
 * + `linkedEntityRowId` is a polymorphic reference (not a Room `@ForeignKey`, since SQLite has no
 * polymorphic FK) — the same pattern already proven by
 * [com.jarvis.tidb.analytics.entity.LearningEvidenceLinkEntity]. Referential integrity for the
 * linked-entity side is the repository layer's responsibility.
 */
@Entity(
    tableName = "evidence_links",
    foreignKeys = [
        ForeignKey(
            entity = EvidenceRecordEntity::class,
            parentColumns = ["evidenceId"],
            childColumns = ["evidenceId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["evidenceId"]),
        Index(value = ["linkedEntityType", "linkedEntityRowId"]),
        Index(value = ["evidenceId", "linkedEntityType", "linkedEntityRowId"], unique = true)
    ]
)
data class EvidenceLinkEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "linkId") val linkId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "evidenceId") val evidenceId: Long,
    @ColumnInfo(name = "linkedEntityType") val linkedEntityType: LinkedEntityType,
    @ColumnInfo(name = "linkedEntityRowId") val linkedEntityRowId: Long,
    @ColumnInfo(name = "role") val role: EvidenceLinkRole = EvidenceLinkRole.SUPPORTS,
    /** How much this specific link contributed, in [0.0, 1.0] — analogous to `SupportingIndicatorEntity.contributionWeight`. */
    @ColumnInfo(name = "weight") val weight: Double = 1.0,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "linkedAt") val linkedAt: Long = System.currentTimeMillis()
)

enum class OutcomeVerdict(val value: String) {
    PENDING("PENDING"),
    CONFIRMED("CONFIRMED"),
    PARTIALLY_CONFIRMED("PARTIALLY_CONFIRMED"),
    INVALIDATED("INVALIDATED"),
    INCONCLUSIVE("INCONCLUSIVE"),
    EXPIRED("EXPIRED");

    companion object {
        fun from(value: String): OutcomeVerdict = entries.firstOrNull { it.value == value } ?: PENDING
    }
}

/**
 * One evaluation checkpoint of "did this evidence's implication play out" — generalizes
 * [com.jarvis.tidb.historical.evidence.entity.PatternOccurrenceEntity.outcome] (a single mutable
 * field) to any [EvidenceRecordEntity], and allows multiple checkpoints per evidence row rather
 * than one terminal value (e.g. re-evaluated at 1 session, 5 sessions, 20 sessions out).
 */
@Entity(
    tableName = "evidence_outcomes",
    foreignKeys = [
        ForeignKey(
            entity = EvidenceRecordEntity::class,
            parentColumns = ["evidenceId"],
            childColumns = ["evidenceId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["evidenceId"]),
        Index(value = ["verdict"]),
        Index(value = ["evaluatedAt"])
    ]
)
data class EvidenceOutcomeEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "outcomeId") val outcomeId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "evidenceId") val evidenceId: Long,
    @ColumnInfo(name = "verdict") val verdict: OutcomeVerdict = OutcomeVerdict.PENDING,
    @ColumnInfo(name = "evaluatedAt") val evaluatedAt: Long = System.currentTimeMillis(),
    /** Human-readable evaluation horizon, e.g. "5 sessions", "next earnings release". */
    @ColumnInfo(name = "horizonDescription") val horizonDescription: String? = null,
    @ColumnInfo(name = "actualMovePercent") val actualMovePercent: Double? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @Embedded val audit: AuditMetadata = AuditMetadata()
)

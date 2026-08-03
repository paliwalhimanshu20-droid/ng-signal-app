package com.jarvis.tidb.decision.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jarvis.tidb.core.common.AuditMetadata
import com.jarvis.tidb.core.common.GlobalId
import com.jarvis.tidb.core.common.SoftDeleteMetadata
import com.jarvis.tidb.core.entity.ContractEntity
import com.jarvis.tidb.core.entity.InstrumentEntity
import com.jarvis.tidb.intelligence.evidence.entity.OutcomeVerdict

/**
 * TRADING-007B — Decision Intelligence Engine (schema v9).
 *
 * Implements exactly the approved TRADING-007B architecture blueprint
 * (`docs/database/TRADING-007B-Decision-Intelligence-Engine-Architecture.md`) — 5 new tables,
 * nothing else. This module reasons over the existing Knowledge Layer; it does not duplicate
 * any of it. Per the approved blueprint's central finding, three existing polymorphic
 * mechanisms already cover most of what a naive reading of "Decision Intelligence Engine" would
 * suggest needs new tables:
 *
 *  - **Evidence weighting / supporting / contradicting evidence** — NOT a new table.
 *    [com.jarvis.tidb.intelligence.evidence.entity.EvidenceLinkEntity] already carries `role`
 *    (SUPPORTS/CONTRADICTS/CONTEXTUALIZES/TRIGGERED_BY) and `weight` ([0.0, 1.0]). This module
 *    reuses it verbatim by adding `LinkedEntityType.DECISION` (see
 *    `intelligence/evidence/entity/IntelligenceEvidenceEntities.kt`) so evidence can link
 *    directly to a [RecommendationEntity] row.
 *  - **Confidence** — NOT a new table. [RecommendationEntity.confidenceScoreId] is a logical
 *    reference into the existing `confidence_scores` table
 *    ([com.jarvis.tidb.intelligence.confidence.entity.ConfidenceScoreEntity]), scored via the
 *    newly-added `ScoredEntityType.DECISION` (see `intelligence/confidence/entity/
 *    ConfidenceEntities.kt`). No confidence number is stored redundantly on this table.
 *  - **Decision drift / bias / recalibration** — NOT a new table.
 *    [com.jarvis.tidb.context.entity.DriftMetricEntity] /
 *    [com.jarvis.tidb.context.entity.CalibrationMetricEntity] (TRADING-007A.2) already do
 *    polymorphic subject monitoring; the newly-added `ContextMonitoringSubjectType
 *    .RECOMMENDATION_ENGINE` value (see `context/entity/MarketContextIntelligenceEntities.kt`)
 *    covers this module's own drift/calibration tracking with zero new tables.
 *  - **Outcome verdict** — NOT a new enum. [RecommendationOutcomeEntity.verdict] reuses
 *    [OutcomeVerdict] (`intelligence.evidence`) directly rather than defining a parallel
 *    success/failure vocabulary.
 *  - **The lightweight decision log** — NOT touched, NOT redesigned.
 *    `analytics.entity.DecisionRecordEntity` / `DecisionExplanationEntity` (Module 3, Executive
 *    Trading Memory) remain exactly as they are — a thinner, pre-existing "decision happened"
 *    log with no evidence graph or confidence score. [RecommendationEntity.linkedDecisionRecordId]
 *    is a logical bridge (no Room `@ForeignKey`, since it's an optional cross-package pointer)
 *    to a `decision_records` row created if/when a recommendation is acted on — the two
 *    concepts coexist by design; see the architecture doc §16 for the full reconciliation.
 *
 * This module implements persistence only — no inference, no scoring, no recommendation
 * algorithm lives in this database layer, consistent with every prior module's "nothing here
 * scores, ranks, or recommends" principle (this is the one module in the schema whose whole
 * *purpose* is downstream of that scoring, but the scoring itself still happens elsewhere).
 */

// ======================================================================================
// Recommendation
// ======================================================================================

/** What action a recommendation proposes. Closed, code-owned vocabulary — not a table, per the approved blueprint §6 (only genuinely open, ops-managed vocabularies like `EconomicEventCategoryEntity` warrant a table). */
enum class RecommendationType(val value: String) {
    ENTRY_LONG("ENTRY_LONG"),
    ENTRY_SHORT("ENTRY_SHORT"),
    EXIT("EXIT"),
    HOLD("HOLD"),
    HEDGE("HEDGE"),
    SCALE_IN("SCALE_IN"),
    SCALE_OUT("SCALE_OUT"),
    WATCH("WATCH"),
    AVOID("AVOID");

    companion object {
        fun from(value: String): RecommendationType = entries.firstOrNull { it.value == value } ?: WATCH
    }
}

/** Lifecycle status of a recommendation. Terminal states are final — a changed mind produces a new row via [RecommendationEntity.revisesRecommendationId], never a state rollback, per the "supersede, don't mutate" principle already used throughout this schema. */
enum class RecommendationStatus(val value: String) {
    DRAFT("DRAFT"),
    ACTIVE("ACTIVE"),
    EXECUTED("EXECUTED"),
    EXPIRED("EXPIRED"),
    WITHDRAWN("WITHDRAWN"),
    SUPERSEDED("SUPERSEDED"),
    REJECTED("REJECTED");

    companion object {
        fun from(value: String): RecommendationStatus = entries.firstOrNull { it.value == value } ?: DRAFT
    }
}

/** How far out a recommendation is meant to play out. */
enum class DecisionTimeHorizon(val value: String) {
    INTRADAY("INTRADAY"),
    SHORT_TERM("SHORT_TERM"),
    SWING("SWING"),
    POSITION("POSITION"),
    LONG_TERM("LONG_TERM");

    companion object {
        fun from(value: String): DecisionTimeHorizon = entries.firstOrNull { it.value == value } ?: SWING
    }
}

/**
 * The core output of the Decision Intelligence Engine — one evidence-driven, explainable
 * recommendation. Per the approved blueprint, this is deliberately NOT a replacement for
 * `analytics.entity.DecisionRecordEntity` — see file doc.
 *
 * Explainability is structural, not narrative: "why" / "why not" / "which evidence mattered
 * most" are answered by querying `intelligence.evidence.entity.EvidenceLinkEntity` rows with
 * `linkedEntityType = DECISION` and `linkedEntityRowId = recommendationId`, ordered by `role`
 * and `weight` — not by columns on this table. [assumptionsJson] and [reasoningSummary] are the
 * only free-text fields here, because "what did this specific reasoning pass assume" is
 * genuinely not derivable from anywhere else in the schema (see architecture doc §7).
 */
@Entity(
    tableName = "decision_recommendations",
    foreignKeys = [
        ForeignKey(entity = InstrumentEntity::class, parentColumns = ["instrumentId"], childColumns = ["instrumentId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = ContractEntity::class, parentColumns = ["contractId"], childColumns = ["contractId"], onDelete = ForeignKey.SET_NULL, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = RecommendationEntity::class, parentColumns = ["recommendationId"], childColumns = ["revisesRecommendationId"], onDelete = ForeignKey.SET_NULL, onUpdate = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["instrumentId"]),
        Index(value = ["instrumentId", "status"]),
        Index(value = ["status"]),
        Index(value = ["recommendationType"]),
        Index(value = ["expiresAt"]),
        Index(value = ["revisesRecommendationId"]),
        Index(value = ["linkedDecisionRecordId"]),
        Index(value = ["decidedAt"])
    ]
)
data class RecommendationEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "recommendationId") val recommendationId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "instrumentId") val instrumentId: Long,
    @ColumnInfo(name = "contractId") val contractId: Long? = null,
    @ColumnInfo(name = "recommendationType") val recommendationType: RecommendationType,
    @ColumnInfo(name = "status") val status: RecommendationStatus = RecommendationStatus.DRAFT,
    /** Logical-only reference to `confidence_scores.scoreId` (`ScoredEntityType.DECISION`) — the composed confidence for this recommendation. Not a Room `@ForeignKey`: `intelligence.confidence` does not depend on `decision`, and this module reads/writes confidence scores through the existing confidence machinery rather than owning the relationship structurally. */
    @ColumnInfo(name = "confidenceScoreId") val confidenceScoreId: Long? = null,
    /**
     * Phase 4B, Section 1+7+8 — Trust Layer (schema v11, additive `ALTER TABLE`, see
     * `database/migration/TrustLayerMigration.kt`). Logical-only reference to
     * `confidence_scores.scoreId` (`ScoredEntityType.TRUST_ASSESSMENT`) — same non-FK treatment
     * as [confidenceScoreId] and for the same reason (this module reads/writes through the
     * existing confidence machinery rather than owning the relationship structurally). Distinct
     * from [confidenceScoreId]: that field is how strongly the collected evidence leans; this
     * one is how complete the six-dimension evidence base behind the recommendation is
     * (historical data, indicators, optimization, backtests, learning, paper trading) — see
     * `com.jarvis.os.app.core.trading.reasoning.TrustScoreCalculator`. Null only for
     * pre-Phase-4B rows and for the transient DRAFT row `DecisionLifecycleRunner` inserts before
     * either score exists yet.
     */
    @ColumnInfo(name = "trustScoreId") val trustScoreId: Long? = null,
    /** How large/aggressive the recommendation is (e.g. a conviction or suggested-size multiplier), independent of [confidenceScoreId] — a high-confidence WATCH and a high-confidence ENTRY_LONG can share a confidence score while differing entirely in strength. */
    @ColumnInfo(name = "strength") val strength: Double? = null,
    @ColumnInfo(name = "timeHorizon") val timeHorizon: DecisionTimeHorizon = DecisionTimeHorizon.SWING,
    @ColumnInfo(name = "expectedOutcomeDescription") val expectedOutcomeDescription: String? = null,
    @ColumnInfo(name = "expectedTargetLevel") val expectedTargetLevel: Double? = null,
    @ColumnInfo(name = "expectedInvalidationLevel") val expectedInvalidationLevel: Double? = null,
    /** Structured record of what this specific reasoning pass assumed (e.g. "assumes no FOMC surprise") — see class doc; not derivable from any other table. */
    @ColumnInfo(name = "assumptionsJson") val assumptionsJson: String? = null,
    /** A rendered natural-language summary of the evidence graph — informational only, never authoritative. The evidence graph itself (via `EvidenceLinkEntity`) is the source of truth. */
    @ColumnInfo(name = "reasoningSummary") val reasoningSummary: String? = null,
    @ColumnInfo(name = "decidedAt") val decidedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "expiresAt") val expiresAt: Long? = null,
    @ColumnInfo(name = "resolvedAt") val resolvedAt: Long? = null,
    /** Self-referential — points at the recommendation this one revises. Null for an original recommendation. "Supersede, don't mutate": a changed reasoning basis is always a new row. */
    @ColumnInfo(name = "revisesRecommendationId") val revisesRecommendationId: Long? = null,
    /** Logical-only reference to `decision_records.rowId` (`analytics.entity.DecisionRecordEntity`) — populated if/when this recommendation is acted on. See file doc for why this bridges rather than replaces that table. */
    @ColumnInfo(name = "linkedDecisionRecordId") val linkedDecisionRecordId: Long? = null,
    @ColumnInfo(name = "generatedBy") val generatedBy: String = "system",
    @Embedded val audit: AuditMetadata = AuditMetadata(),
    @Embedded val softDelete: SoftDeleteMetadata = SoftDeleteMetadata()
)

// ======================================================================================
// Risk Assessment
// ======================================================================================

/** The six risk categories named in the approved blueprint §9. One row per category per assessment pass — not six columns on one row — so a future category can be added without a schema change to the parent. */
enum class RecommendationRiskCategory(val value: String) {
    MARKET_RISK("MARKET_RISK"),
    EVENT_RISK("EVENT_RISK"),
    LIQUIDITY_RISK("LIQUIDITY_RISK"),
    VOLATILITY_RISK("VOLATILITY_RISK"),
    CORRELATION_RISK("CORRELATION_RISK"),
    UNKNOWN_RISK("UNKNOWN_RISK");

    companion object {
        fun from(value: String): RecommendationRiskCategory = entries.firstOrNull { it.value == value } ?: UNKNOWN_RISK
    }
}

enum class RiskLevel(val value: String) {
    NEGLIGIBLE("NEGLIGIBLE"),
    LOW("LOW"),
    MODERATE("MODERATE"),
    HIGH("HIGH"),
    SEVERE("SEVERE");

    companion object {
        fun from(value: String): RiskLevel = entries.firstOrNull { it.value == value } ?: MODERATE
    }
}

/**
 * Per-recommendation, per-category risk score. Reads `analytics.entity.PortfolioRiskEntity` /
 * `historical.dna.entity.VolatilityProfileEntity` / `historical.dna.entity.LiquidityProfileEntity`
 * / `context.entity.DriftMetricEntity` / `intelligence.graph.entity.CorrelationEntity` as inputs
 * (see architecture doc §9) — this table stores the resulting assessment, it does not
 * duplicate any of those upstream computations. Insert-only: a re-assessment (e.g. triggered by
 * a [DecisionReviewEntity]) is a new row set, preserving the risk picture at each point in time
 * rather than overwriting it.
 */
@Entity(
    tableName = "recommendation_risk_assessments",
    foreignKeys = [
        ForeignKey(entity = RecommendationEntity::class, parentColumns = ["recommendationId"], childColumns = ["recommendationId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["recommendationId", "riskCategory"]),
        Index(value = ["riskCategory"]),
        Index(value = ["riskLevel"]),
        Index(value = ["assessedAt"])
    ]
)
data class RecommendationRiskAssessmentEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "assessmentId") val assessmentId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "recommendationId") val recommendationId: Long,
    @ColumnInfo(name = "riskCategory") val riskCategory: RecommendationRiskCategory,
    @ColumnInfo(name = "riskLevel") val riskLevel: RiskLevel = RiskLevel.MODERATE,
    /** Normalized risk score in [0.0, 1.0] for [riskCategory]. */
    @ColumnInfo(name = "probability") val probability: Double? = null,
    @ColumnInfo(name = "severity") val severity: Double? = null,
    /** Free-text description of the specific factors driving this category's score (e.g. "HIGH-importance FOMC decision falls inside the SWING horizon"). */
    @ColumnInfo(name = "riskFactors") val riskFactors: String? = null,
    @ColumnInfo(name = "mitigation") val mitigation: String? = null,
    @ColumnInfo(name = "assessedAt") val assessedAt: Long = System.currentTimeMillis()
)

// ======================================================================================
// Alternatives
// ======================================================================================

/**
 * "Why not?" — a scenario the pipeline evaluated and rejected alongside the winning
 * [RecommendationEntity] (e.g. BUY / WAIT / SELL / REDUCE_POSITION, per the approved blueprint's
 * examples). Insert-only, permanent — part of the recommendation's explainability record.
 */
@Entity(
    tableName = "recommendation_alternatives",
    foreignKeys = [
        ForeignKey(entity = RecommendationEntity::class, parentColumns = ["recommendationId"], childColumns = ["recommendationId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["recommendationId"])
    ]
)
data class RecommendationAlternativeEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "alternativeId") val alternativeId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    /** The winning recommendation this alternative was evaluated alongside. */
    @ColumnInfo(name = "recommendationId") val recommendationId: Long,
    @ColumnInfo(name = "alternativeType") val alternativeType: RecommendationType,
    @ColumnInfo(name = "rejectionReason") val rejectionReason: String,
    /** Logical-only reference to `confidence_scores.scoreId`, if the alternative was itself scored during evaluation. */
    @ColumnInfo(name = "relativeConfidenceScoreId") val relativeConfidenceScoreId: Long? = null,
    @ColumnInfo(name = "consideredAt") val consideredAt: Long = System.currentTimeMillis()
)

// ======================================================================================
// Outcome
// ======================================================================================

/**
 * What actually happened, tracked against [RecommendationEntity.expectedOutcomeDescription].
 * Reuses [OutcomeVerdict] directly rather than a parallel success/failure enum — see file doc.
 * Insert-only: a recommendation can accumulate multiple outcome checkpoints over its horizon
 * (interim + final), the same "append the next fact" convention as
 * `intelligence.evidence.entity.EvidenceOutcomeEntity`. This is the feedback source for the
 * Learning Framework (architecture doc §8) — outcomes here feed
 * `analytics.entity.LearningObservationEntity`, `context.entity.calibration_metrics`
 * (`RECOMMENDATION_ENGINE` subject), and `intelligence.evidence.entity.EvidenceSourceEntity
 * .reliabilityWeight` at the repository-call-site, not via any new table.
 */
@Entity(
    tableName = "recommendation_outcomes",
    foreignKeys = [
        ForeignKey(entity = RecommendationEntity::class, parentColumns = ["recommendationId"], childColumns = ["recommendationId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["recommendationId"]),
        Index(value = ["recommendationId", "evaluatedAt"]),
        Index(value = ["verdict"])
    ]
)
data class RecommendationOutcomeEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "outcomeId") val outcomeId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "recommendationId") val recommendationId: Long,
    @ColumnInfo(name = "verdict") val verdict: OutcomeVerdict = OutcomeVerdict.PENDING,
    @ColumnInfo(name = "actualMovePercent") val actualMovePercent: Double? = null,
    @ColumnInfo(name = "actualOutcomeDescription") val actualOutcomeDescription: String? = null,
    /** Realized performance figure for this checkpoint (e.g. P&L or return attributable to acting on this recommendation), when known. Distinct from [actualMovePercent], which is price movement rather than realized performance. */
    @ColumnInfo(name = "performanceValue") val performanceValue: Double? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "evaluatedAt") val evaluatedAt: Long = System.currentTimeMillis()
)

// ======================================================================================
// Review
// ======================================================================================

/** What prompted a [DecisionReviewEntity] row. */
enum class DecisionReviewTrigger(val value: String) {
    SCHEDULED("SCHEDULED"),
    DRIFT_ALERT("DRIFT_ALERT"),
    CALIBRATION_ALERT("CALIBRATION_ALERT"),
    NEW_CONTRADICTING_EVIDENCE("NEW_CONTRADICTING_EVIDENCE"),
    MANUAL("MANUAL");

    companion object {
        fun from(value: String): DecisionReviewTrigger = entries.firstOrNull { it.value == value } ?: MANUAL
    }
}

/** What a [DecisionReviewEntity] concluded. */
enum class DecisionReviewConclusion(val value: String) {
    NO_CHANGE("NO_CHANGE"),
    REVISED("REVISED"),
    WITHDRAWN("WITHDRAWN");

    companion object {
        fun from(value: String): DecisionReviewConclusion = entries.firstOrNull { it.value == value } ?: NO_CHANGE
    }
}

/** Which table [DecisionReviewEntity.triggerReferenceRowId] resolves against, when [DecisionReviewEntity.triggerType] is DRIFT_ALERT or CALIBRATION_ALERT. */
enum class DecisionReviewTriggerReferenceType(val value: String) {
    DRIFT_METRIC("DRIFT_METRIC"),
    CALIBRATION_METRIC("CALIBRATION_METRIC"),
    EVIDENCE_LINK("EVIDENCE_LINK");

    companion object {
        fun from(value: String): DecisionReviewTriggerReferenceType = entries.firstOrNull { it.value == value } ?: EVIDENCE_LINK
    }
}

/**
 * "Decision Review" — human review, AI review, revision, approval, and post-analysis, per the
 * approved blueprint, unified into one table rather than one per review kind (a review is a
 * review regardless of who/what performed it — [reviewedBy] captures that distinction, the same
 * way `generatedBy` does elsewhere in this schema). Records *that* a still-active recommendation
 * was reviewed, what triggered it, and what it concluded.
 *
 * [triggerReferenceType] / [triggerReferenceRowId] are a logical-only reference (no Room
 * `@ForeignKey`, since the target table varies) — the same polymorphic-lite shape already used
 * by `context.entity.DriftMetricEntity.subjectType`/`subjectRowId`.
 */
@Entity(
    tableName = "decision_reviews",
    foreignKeys = [
        ForeignKey(entity = RecommendationEntity::class, parentColumns = ["recommendationId"], childColumns = ["recommendationId"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["recommendationId"]),
        Index(value = ["triggerType"]),
        Index(value = ["reviewedAt"])
    ]
)
data class DecisionReviewEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "reviewId") val reviewId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "recommendationId") val recommendationId: Long,
    @ColumnInfo(name = "triggerType") val triggerType: DecisionReviewTrigger = DecisionReviewTrigger.MANUAL,
    /** Populated when [triggerType] is DRIFT_ALERT / CALIBRATION_ALERT / a specific evidence event — see enum doc. Null for SCHEDULED/MANUAL reviews. */
    @ColumnInfo(name = "triggerReferenceType") val triggerReferenceType: DecisionReviewTriggerReferenceType? = null,
    @ColumnInfo(name = "triggerReferenceRowId") val triggerReferenceRowId: Long? = null,
    @ColumnInfo(name = "conclusion") val conclusion: DecisionReviewConclusion = DecisionReviewConclusion.NO_CHANGE,
    /** If [conclusion] is REVISED, the new recommendation row (which itself sets `revisesRecommendationId` back to this review's [recommendationId]). Logical-only, for convenience lookup. */
    @ColumnInfo(name = "resultingRecommendationId") val resultingRecommendationId: Long? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "reviewedAt") val reviewedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "reviewedBy") val reviewedBy: String = "system"
)

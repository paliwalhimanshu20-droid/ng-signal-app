package com.jarvis.tidb.analytics.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jarvis.tidb.core.common.AuditMetadata
import com.jarvis.tidb.core.common.GlobalId
import com.jarvis.tidb.core.common.SoftDeleteMetadata

/**
 * SECTION 4 — AI LEARNING
 *
 * This module STORES AI findings; it does not generate them. Every entity here is a durable
 * record of something a (future, external) learning/inference process concluded, with its
 * supporting evidence and a confidence score, so that conclusion can be audited, revised, or
 * cited by later insights. All rows are append-only from this module's point of view — the
 * DAOs expose insert + read, never update, matching Module 2's immutability convention for
 * anything that represents "what the AI observed/concluded at time T".
 */

enum class ObservationSource {
    TRADE_OUTCOME,
    BACKTEST_RESULT,
    SIGNAL_ACCURACY,
    MARKET_CONDITION,
    PORTFOLIO_BEHAVIOR,
    MANUAL
}

enum class InsightCategory {
    ENTRY_TIMING,
    EXIT_TIMING,
    RISK_SIZING,
    INSTRUMENT_SELECTION,
    STRATEGY_TUNING,
    MARKET_REGIME,
    EXECUTION_QUALITY,
    OTHER
}

enum class SuggestionStatus {
    PROPOSED,
    UNDER_REVIEW,
    ACCEPTED,
    REJECTED,
    APPLIED,
    SUPERSEDED
}

enum class FailureCategory {
    STOP_TOO_TIGHT,
    STOP_TOO_WIDE,
    LATE_ENTRY,
    LATE_EXIT,
    WRONG_DIRECTION,
    POOR_SIZING,
    NEWS_EVENT,
    LIQUIDITY,
    EXECUTION_SLIPPAGE,
    STRATEGY_MISMATCH,
    UNKNOWN
}

/** A single, atomic observation about trading behavior or outcomes — raw evidence, not yet a conclusion. */
@Entity(
    tableName = "learning_observations",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["source"]),
        Index(value = ["relatedTradeRowId"]),
        Index(value = ["relatedBacktestRunRowId"]),
        Index(value = ["generatedAt"]),
        Index(value = ["confidence"])
    ]
)
data class LearningObservationEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    val source: ObservationSource,

    val observation: String,

    val confidence: Double,

    val relatedTradeRowId: Long? = null,

    val relatedBacktestRunRowId: Long? = null,

    val relatedInstrumentId: Long? = null,

    val relatedStrategyId: String? = null,

    val generatedAt: Long = System.currentTimeMillis(),

    val generatedBy: String = "unknown",

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)

/** A conclusion synthesized from one or more [LearningObservationEntity] rows. */
@Entity(
    tableName = "learning_insights",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["category"]),
        Index(value = ["confidence"]),
        Index(value = ["generatedAt"])
    ]
)
data class LearningInsightEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    val category: InsightCategory,

    val insight: String,

    val confidence: Double,

    /** Comma-separated `learning_observations.rowId` values this insight was derived from. */
    val supportingObservationRowIdsCsv: String? = null,

    val impactScore: Double? = null,

    val generatedAt: Long = System.currentTimeMillis(),

    val generatedBy: String = "unknown",

    @Embedded
    val audit: AuditMetadata = AuditMetadata(),

    @Embedded
    val softDelete: SoftDeleteMetadata = SoftDeleteMetadata()
)

/** A concrete, actionable suggestion derived from one or more insights. Status is mutable — this is the one learning entity a human/AI workflow actually progresses. */
@Entity(
    tableName = "optimization_suggestions",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["status"]),
        Index(value = ["relatedInsightRowId"]),
        Index(value = ["impactScore"]),
        Index(value = ["generatedAt"])
    ]
)
data class OptimizationSuggestionEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    val relatedInsightRowId: Long? = null,

    val suggestedImprovement: String,

    val impactScore: Double,

    val confidence: Double,

    val status: SuggestionStatus = SuggestionStatus.PROPOSED,

    val reviewedBy: String? = null,

    val reviewedAt: Long? = null,

    val generatedAt: Long = System.currentTimeMillis(),

    val generatedBy: String = "unknown",

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)

/** A recurring pattern noticed across many trades/backtests — e.g. "shorts underperform on Fridays". */
@Entity(
    tableName = "pattern_discoveries",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["patternKey"]),
        Index(value = ["confidence"]),
        Index(value = ["generatedAt"])
    ]
)
data class PatternDiscoveryEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    /** Short machine-stable slug identifying the pattern class, e.g. "friday_short_underperformance". */
    val patternKey: String,

    val description: String,

    val occurrenceCount: Int,

    val confidence: Double,

    val supportingEvidence: String? = null,

    val firstObservedAt: Long,

    val lastObservedAt: Long,

    val generatedBy: String = "unknown",

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)

/**
 * v1.0 consolidation item 5 — LEARNING EVIDENCE LINKS.
 *
 * The fixed `relatedTradeRowId`/`relatedBacktestRunRowId`/`relatedInstrumentId`/
 * `relatedStrategyId` columns already on [LearningObservationEntity] and (below)
 * [FailureAnalysisEntity] cover the single-most-common case (one observation, one trade). This
 * table exists for the general case the brief calls out explicitly: "every AI insight should
 * always be traceable back to evidence," where the evidence can be a Signal, a Trade, a
 * Backtest (run), a Performance Metric, or a Timeline Event, and there can be more than one
 * piece of evidence per insight/suggestion/pattern.
 *
 * `sourceType` + `sourceRowId` is a generic polymorphic reference rather than five nullable FK
 * columns, because most links only ever populate one of the five anyway — a link row exists
 * only when there's something to link. `sourceType` is intentionally NOT a Room `@ForeignKey`
 * itself (SQLite has no polymorphic FK); referential integrity for evidence rows is the
 * repository layer's job (`LearningRepositoryImpl` resolves `sourceType` to the right
 * repository before writing).
 */
enum class EvidenceSourceType {
    SIGNAL,
    TRADE,
    BACKTEST_RUN,
    PERFORMANCE_METRIC,
    TIMELINE_EVENT
}

enum class LearningEntityType {
    OBSERVATION,
    INSIGHT,
    SUGGESTION,
    PATTERN,
    FAILURE_ANALYSIS
}

@Entity(
    tableName = "learning_evidence_links",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["linkedEntityType", "linkedEntityRowId"]),
        Index(value = ["sourceType", "sourceRowId"]),
        Index(value = ["linkedEntityType", "linkedEntityRowId", "sourceType", "sourceRowId"], unique = true)
    ]
)
data class LearningEvidenceLinkEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    /** Which learning-layer row this evidence supports. */
    val linkedEntityType: LearningEntityType,
    val linkedEntityRowId: Long,

    /** Which evidence row supports it — Signal (formerly Module 2), Trade/BacktestRun/PerformanceMetric (this module), or a TimelineEvent. */
    val sourceType: EvidenceSourceType,
    val sourceRowId: Long,

    val note: String? = null,

    val linkedAt: Long = System.currentTimeMillis()
)

/** A specific post-mortem on why a trade or backtest run lost money / underperformed. */
@Entity(
    tableName = "failure_analyses",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["relatedTradeRowId"]),
        Index(value = ["relatedBacktestRunRowId"]),
        Index(value = ["category"]),
        Index(value = ["generatedAt"])
    ]
)
data class FailureAnalysisEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    val relatedTradeRowId: Long? = null,

    val relatedBacktestRunRowId: Long? = null,

    val category: FailureCategory,

    val observation: String,

    val confidence: Double,

    val supportingEvidence: String? = null,

    val suggestedImprovement: String? = null,

    val impactScore: Double? = null,

    val generatedAt: Long = System.currentTimeMillis(),

    val generatedBy: String = "unknown",

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)

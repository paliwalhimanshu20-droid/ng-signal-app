package com.jarvis.tidb.analytics.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jarvis.tidb.core.common.AuditMetadata
import com.jarvis.tidb.core.common.GlobalId

/**
 * SECTION 5 — EXECUTIVE TRADING MEMORY
 *
 * [TradingTimelineEventEntity] is the append-only, permanent event log spanning every module —
 * "nothing should ever be permanently lost". It's intentionally the widest, simplest table
 * (a generic event envelope) so any current or future module can append to it without a
 * schema change: Signal Generated, Trade Executed, Stop Loss Hit, Strategy Updated, Backtest
 * Completed, AI Observation, Portfolio Rebalanced, etc. Nothing in this file is ever updated
 * or deleted by this module's DAOs — no update methods are exposed, same immutability
 * convention as Module 2's lifecycle/snapshot tables.
 *
 * [DecisionRecordEntity] captures a specific decision point (why JARVIS or the user chose to
 * act), [DecisionExplanationEntity] is the (optionally AI-generated) natural-language
 * explanation attached to a decision, and [LessonLearnedEntity] is a distilled, durable
 * takeaway — the executive-summary layer above raw [LearningInsightEntity] rows.
 */

enum class TimelineEventType {
    SIGNAL_GENERATED,
    TRADE_EXECUTED,
    TRADE_CLOSED,
    STOP_LOSS_HIT,
    TARGET_HIT,
    STRATEGY_UPDATED,
    BACKTEST_COMPLETED,
    AI_OBSERVATION,
    AI_INSIGHT,
    OPTIMIZATION_SUGGESTED,
    PORTFOLIO_REBALANCED,
    CAPITAL_DEPOSITED,
    CAPITAL_WITHDRAWN,
    RISK_LIMIT_BREACHED,
    DECISION_MADE,
    LESSON_RECORDED,
    /** TRADING-007B (schema v9) — Decision Intelligence Engine. */
    RECOMMENDATION_ISSUED,
    /** TRADING-007B (schema v9) — Decision Intelligence Engine. */
    RECOMMENDATION_REVIEWED,
    OTHER
}

enum class TimelineSeverity {
    INFO,
    NOTABLE,
    WARNING,
    CRITICAL
}

enum class DecisionOutcome {
    PENDING,
    SUCCESSFUL,
    UNSUCCESSFUL,
    NEUTRAL,
    UNKNOWN
}

/** The permanent, append-only timeline. Every other module writes here; this module owns the table. */
@Entity(
    tableName = "trading_timeline_events",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["eventType"]),
        Index(value = ["severity"]),
        Index(value = ["occurredAt"]),
        Index(value = ["relatedTradeRowId"]),
        Index(value = ["relatedSignalId"]),
        Index(value = ["relatedInstrumentId"]),
        Index(value = ["eventType", "occurredAt"])
    ]
)
data class TradingTimelineEventEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    val eventType: TimelineEventType,

    val severity: TimelineSeverity = TimelineSeverity.INFO,

    val title: String,

    val details: String? = null,

    val occurredAt: Long = System.currentTimeMillis(),

    val relatedTradeRowId: Long? = null,

    val relatedSignalId: Long? = null,

    val relatedInstrumentId: Long? = null,

    val relatedBacktestRunRowId: Long? = null,

    val relatedPortfolioRowId: Long? = null,

    /** JSON blob for any additional structured payload specific to [eventType]. */
    val payloadJson: String? = null,

    val source: String = "system",

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)

@Entity(
    tableName = "decision_records",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["relatedTradeRowId"]),
        Index(value = ["relatedSignalId"]),
        Index(value = ["outcome"]),
        Index(value = ["decidedAt"])
    ]
)
data class DecisionRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    val relatedTradeRowId: Long? = null,

    val relatedSignalId: Long? = null,

    val decisionSummary: String,

    val decidedBy: String = "system",

    val outcome: DecisionOutcome = DecisionOutcome.PENDING,

    val decidedAt: Long = System.currentTimeMillis(),

    val resolvedAt: Long? = null,

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)

/** Natural-language explanation attached to a decision — separated from [DecisionRecordEntity] so multiple explanations (e.g. rule-based + AI-generated) can coexist for one decision. */
@Entity(
    tableName = "decision_explanations",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["decisionRecordRowId"]),
        Index(value = ["generatedAt"])
    ]
)
data class DecisionExplanationEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    val decisionRecordRowId: Long,

    val explanation: String,

    val confidence: Double? = null,

    val generatedBy: String = "unknown",

    val generatedAt: Long = System.currentTimeMillis(),

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)

/** A distilled, durable takeaway — the executive-summary layer above raw learning insights. */
@Entity(
    tableName = "lessons_learned",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["relatedInsightRowId"]),
        Index(value = ["relatedFailureAnalysisRowId"]),
        Index(value = ["recordedAt"])
    ]
)
data class LessonLearnedEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    val lesson: String,

    val relatedInsightRowId: Long? = null,

    val relatedFailureAnalysisRowId: Long? = null,

    val importance: Double? = null,

    val recordedAt: Long = System.currentTimeMillis(),

    val recordedBy: String = "system",

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)

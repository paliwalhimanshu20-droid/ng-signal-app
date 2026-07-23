package com.jarvis.tidb.analytics.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * SECTION 7 — ANALYTICS RELATIONSHIPS
 *
 * Room `@Relation` POJOs connecting Signal -> Trade -> Performance -> Learning -> Timeline ->
 * Portfolio for hydrated reads. These are read-only projections, never persisted directly —
 * same two-tier "lightweight list vs. full detail" hydration strategy as Module 2's
 * `@Relation` POJOs. `signalId`/`instrumentId` links stay logical (no cross-database
 * `@ForeignKey`), so hydrating the Signal or Instrument side of these POJOs requires the
 * repository layer to join in Module 2 / Module 1 data by id after the Room query returns.
 */

/** A trade with its full execution/exit/fee/journal history — the primary "reconstruct one trade" read. */
data class TradeWithDetails(
    @Embedded
    val trade: TradeEntity,

    @Relation(parentColumn = "rowId", entityColumn = "tradeRowId")
    val executions: List<TradeExecutionEntity>,

    @Relation(parentColumn = "rowId", entityColumn = "tradeRowId")
    val exits: List<TradeExitEntity>,

    @Relation(parentColumn = "rowId", entityColumn = "tradeRowId")
    val fees: List<TradeFeesEntity>,

    @Relation(parentColumn = "rowId", entityColumn = "tradeRowId")
    val journal: List<TradeJournalEntity>
)

/** A backtest run with its config, generated trades, and summary result — the "reconstruct one backtest" read. */
data class BacktestRunWithDetails(
    @Embedded
    val run: BacktestRunEntity,

    @Relation(parentColumn = "runRowId", entityColumn = "rowId", entity = BacktestTradeEntity::class)
    val trades: List<BacktestTradeEntity>,

    @Relation(parentColumn = "rowId", entityColumn = "runRowId")
    val result: BacktestResultEntity?
)

/** A performance snapshot with its extensible metric rows. */
data class PerformanceSnapshotWithMetrics(
    @Embedded
    val snapshot: PerformanceSnapshotEntity,

    @Relation(parentColumn = "rowId", entityColumn = "snapshotRowId")
    val metrics: List<PerformanceMetricEntity>
)

/** A trade paired with everything the learning layer concluded about it — the "why did this trade go the way it did" read. */
data class TradeWithLearning(
    @Embedded
    val trade: TradeEntity,

    @Relation(parentColumn = "rowId", entityColumn = "relatedTradeRowId")
    val observations: List<LearningObservationEntity>,

    @Relation(parentColumn = "rowId", entityColumn = "relatedTradeRowId")
    val failureAnalyses: List<FailureAnalysisEntity>,

    @Relation(parentColumn = "rowId", entityColumn = "relatedTradeRowId")
    val decisions: List<DecisionRecordEntity>,

    @Relation(parentColumn = "rowId", entityColumn = "relatedTradeRowId")
    val timelineEvents: List<TradingTimelineEventEntity>
)

/** A decision record with its explanation(s) — decisions and explanations are 1:N to allow multiple explanation sources. */
data class DecisionWithExplanations(
    @Embedded
    val decision: DecisionRecordEntity,

    @Relation(parentColumn = "rowId", entityColumn = "decisionRecordRowId")
    val explanations: List<DecisionExplanationEntity>
)

/** A portfolio with its current open positions, latest allocation rows, and latest risk snapshot. */
data class PortfolioWithDetails(
    @Embedded
    val portfolio: PortfolioEntity,

    @Relation(parentColumn = "rowId", entityColumn = "portfolioRowId")
    val positions: List<PortfolioPositionEntity>,

    @Relation(parentColumn = "rowId", entityColumn = "portfolioRowId")
    val allocations: List<PortfolioAllocationEntity>,

    @Relation(parentColumn = "rowId", entityColumn = "portfolioRowId")
    val riskSnapshots: List<PortfolioRiskEntity>,

    @Relation(parentColumn = "rowId", entityColumn = "portfolioRowId")
    val recentMovements: List<CapitalMovementEntity>
)

/** Full "reconstruct complete trading history" read for one trade: Signal(id) -> Trade -> Learning -> Timeline. Instrument/Signal payloads are hydrated by the repository via Module 1/2 repositories, not by Room. */
data class TradeFullHistory(
    @Embedded
    val trade: TradeEntity,

    @Relation(parentColumn = "rowId", entityColumn = "tradeRowId")
    val executions: List<TradeExecutionEntity>,

    @Relation(parentColumn = "rowId", entityColumn = "tradeRowId")
    val exits: List<TradeExitEntity>,

    @Relation(parentColumn = "rowId", entityColumn = "relatedTradeRowId")
    val learningObservations: List<LearningObservationEntity>,

    @Relation(parentColumn = "rowId", entityColumn = "relatedTradeRowId")
    val timelineEvents: List<TradingTimelineEventEntity>
)

package com.jarvis.tidb.analytics.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jarvis.tidb.core.common.AuditMetadata
import com.jarvis.tidb.core.common.GlobalId
import com.jarvis.tidb.core.common.SoftDeleteMetadata

/**
 * SECTION 3 — PERFORMANCE ANALYTICS
 *
 * [PerformanceSnapshotEntity] is a point-in-time rollup (e.g. "as of end of day") that owns a
 * set of [PerformanceMetricEntity] rows — a generic name/value table so new metrics can be
 * added later without a schema migration. [StrategyPerformanceEntity], [InstrumentPerformanceEntity]
 * and [MonthlyPerformanceEntity] are pre-aggregated, denormalized slices kept as their own
 * tables (rather than computed on the fly) because they're read far more often than the
 * underlying trades change, and large-scale historical queries should not require scanning
 * `trades` every time a dashboard renders.
 */

enum class PerformanceScope {
    OVERALL,
    STRATEGY,
    INSTRUMENT,
    MONTHLY,
    CUSTOM
}

@Entity(
    tableName = "performance_snapshots",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["scope"]),
        Index(value = ["scopeKey"]),
        Index(value = ["snapshotAt"]),
        Index(value = ["scope", "scopeKey", "snapshotAt"])
    ]
)
data class PerformanceSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    val scope: PerformanceScope,

    /** Strategy id, instrumentId as string, "YYYY-MM", or "ALL" depending on [scope]. */
    val scopeKey: String,

    val snapshotAt: Long,

    val periodStart: Long,

    val periodEnd: Long,

    val tradeCount: Int,

    val netProfit: Double,

    val winRate: Double,

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)

/** Generic, extensible name/value metric attached to a snapshot. Avoids schema churn as new metrics are added. */
@Entity(
    tableName = "performance_metrics",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["snapshotRowId"]),
        Index(value = ["metricName"]),
        Index(value = ["snapshotRowId", "metricName"], unique = true)
    ]
)
data class PerformanceMetricEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    val snapshotRowId: Long,

    val metricName: String,

    val metricValue: Double,

    val unit: String? = null,

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)

@Entity(
    tableName = "strategy_performance",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["strategyId"], unique = true),
        Index(value = ["recomputedAt"])
    ]
)
data class StrategyPerformanceEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    val strategyId: String,

    val totalTrades: Int = 0,

    val winningTrades: Int = 0,

    val losingTrades: Int = 0,

    val winRate: Double = 0.0,

    val lossRate: Double = 0.0,

    val netProfit: Double = 0.0,

    val averageProfit: Double = 0.0,

    val averageLoss: Double = 0.0,

    val riskReward: Double? = null,

    val profitFactor: Double? = null,

    val maxDrawdown: Double = 0.0,

    val recoveryFactor: Double? = null,

    val sharpeRatio: Double? = null,

    val sortinoRatio: Double? = null,

    val expectancy: Double? = null,

    val consecutiveWins: Int = 0,

    val consecutiveLosses: Int = 0,

    val maxConsecutiveWins: Int = 0,

    val maxConsecutiveLosses: Int = 0,

    val recomputedAt: Long = System.currentTimeMillis(),

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)

@Entity(
    tableName = "instrument_performance",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["instrumentId"], unique = true),
        Index(value = ["recomputedAt"])
    ]
)
data class InstrumentPerformanceEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    val instrumentId: Long,

    val totalTrades: Int = 0,

    val winningTrades: Int = 0,

    val losingTrades: Int = 0,

    val winRate: Double = 0.0,

    val lossRate: Double = 0.0,

    val netProfit: Double = 0.0,

    val averageProfit: Double = 0.0,

    val averageLoss: Double = 0.0,

    val riskReward: Double? = null,

    val profitFactor: Double? = null,

    val maxDrawdown: Double = 0.0,

    val recoveryFactor: Double? = null,

    val sharpeRatio: Double? = null,

    val sortinoRatio: Double? = null,

    val expectancy: Double? = null,

    val consecutiveWins: Int = 0,

    val consecutiveLosses: Int = 0,

    val maxConsecutiveWins: Int = 0,

    val maxConsecutiveLosses: Int = 0,

    val recomputedAt: Long = System.currentTimeMillis(),

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)

@Entity(
    tableName = "monthly_performance",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["yearMonth"], unique = true),
    ]
)
data class MonthlyPerformanceEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    /** "YYYY-MM", e.g. "2026-07". */
    val yearMonth: String,

    val totalTrades: Int = 0,

    val winningTrades: Int = 0,

    val losingTrades: Int = 0,

    val winRate: Double = 0.0,

    val netProfit: Double = 0.0,

    val averageProfit: Double = 0.0,

    val averageLoss: Double = 0.0,

    val profitFactor: Double? = null,

    val maxDrawdown: Double = 0.0,

    val sharpeRatio: Double? = null,

    val sortinoRatio: Double? = null,

    val expectancy: Double? = null,

    val bestTradePnl: Double? = null,

    val worstTradePnl: Double? = null,

    @Embedded
    val audit: AuditMetadata = AuditMetadata(),

    @Embedded
    val softDelete: SoftDeleteMetadata = SoftDeleteMetadata()
)

package com.jarvis.tidb.analytics.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jarvis.tidb.core.common.AuditMetadata
import com.jarvis.tidb.core.common.GlobalId
import com.jarvis.tidb.core.common.SoftDeleteMetadata

/**
 * SECTION 2 — BACKTESTING (data model only — no simulation engine)
 *
 * [BacktestEntity] is the definition of a backtest (what/when/how). [BacktestRunEntity] is one
 * execution of that definition (a definition can be re-run, e.g. after a parameter tweak).
 * [BacktestTradeEntity] rows are synthetic trades produced by a run — same shape family as
 * [TradeEntity] but intentionally kept as a separate table, since backtest trades must never
 * be mixed with live trade history in aggregate performance queries. [BacktestResultEntity]
 * holds the run's summary statistics, and [BacktestConfigurationEntity] versions the parameter
 * set so a run can always be traced back to the exact config that produced it.
 */

enum class BacktestStatus {
    DRAFT,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Entity(
    tableName = "backtests",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["strategyId"]),
        Index(value = ["createdAt"])
    ]
)
data class BacktestEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    val name: String,

    val strategyId: String,

    val description: String? = null,

    val periodStart: Long,

    val periodEnd: Long,

    /** Comma-separated logical instrumentIds (Module 1), validated at repository layer. */
    val instrumentIdsCsv: String,

    val latestRunId: Long? = null,

    @Embedded
    val audit: AuditMetadata = AuditMetadata(),

    @Embedded
    val softDelete: SoftDeleteMetadata = SoftDeleteMetadata()
)

@Entity(
    tableName = "backtest_configurations",
    foreignKeys = [
        ForeignKey(
            entity = BacktestEntity::class,
            parentColumns = ["rowId"],
            childColumns = ["backtestRowId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["backtestRowId"]),
        Index(value = ["version"])
    ]
)
data class BacktestConfigurationEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    val backtestRowId: Long,

    val version: Int = 1,

    /** JSON blob of strategy parameters — arbitrary shape owned by the (future) strategy engine. */
    val parametersJson: String,

    val initialCapital: Double,

    val riskPerTradePercent: Double? = null,

    val commissionModelJson: String? = null,

    val slippageModelJson: String? = null,

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)

@Entity(
    tableName = "backtest_runs",
    foreignKeys = [
        ForeignKey(
            entity = BacktestEntity::class,
            parentColumns = ["rowId"],
            childColumns = ["backtestRowId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = BacktestConfigurationEntity::class,
            parentColumns = ["rowId"],
            childColumns = ["configurationRowId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["backtestRowId"]),
        Index(value = ["configurationRowId"]),
        Index(value = ["status"]),
        Index(value = ["startedAt"])
    ]
)
data class BacktestRunEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    val backtestRowId: Long,

    val configurationRowId: Long?,

    val status: BacktestStatus = BacktestStatus.QUEUED,

    val startedAt: Long? = null,

    val completedAt: Long? = null,

    val engineVersion: String? = null,

    val failureReason: String? = null,

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)

@Entity(
    tableName = "backtest_trades",
    foreignKeys = [
        ForeignKey(
            entity = BacktestRunEntity::class,
            parentColumns = ["rowId"],
            childColumns = ["runRowId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["runRowId"]),
        Index(value = ["instrumentId"]),
        Index(value = ["entryTimestamp"])
    ]
)
data class BacktestTradeEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    val runRowId: Long,

    val instrumentId: Long,

    val direction: TradeDirection,

    val entryPrice: Double,

    val exitPrice: Double,

    val quantity: Double,

    val entryTimestamp: Long,

    val exitTimestamp: Long,

    val closeReason: TradeCloseReason,

    val grossPnl: Double,

    val netPnl: Double,

    val pnlPercent: Double,

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)

@Entity(
    tableName = "backtest_results",
    foreignKeys = [
        ForeignKey(
            entity = BacktestRunEntity::class,
            parentColumns = ["rowId"],
            childColumns = ["runRowId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["runRowId"], unique = true)
    ]
)
data class BacktestResultEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    val runRowId: Long,

    val totalTrades: Int,

    val winningTrades: Int,

    val losingTrades: Int,

    val netProfit: Double,

    val winRate: Double,

    val maxDrawdown: Double,

    val maxDrawdownPercent: Double,

    val sharpeRatio: Double? = null,

    val sortinoRatio: Double? = null,

    val profitFactor: Double? = null,

    val expectancy: Double? = null,

    val averageWin: Double? = null,

    val averageLoss: Double? = null,

    val largestWin: Double? = null,

    val largestLoss: Double? = null,

    val maxConsecutiveWins: Int = 0,

    val maxConsecutiveLosses: Int = 0,

    val startingCapital: Double,

    val endingCapital: Double,

    val cagr: Double? = null,

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)

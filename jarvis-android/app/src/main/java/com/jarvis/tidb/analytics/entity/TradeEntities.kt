package com.jarvis.tidb.analytics.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jarvis.tidb.core.common.AuditMetadata
import com.jarvis.tidb.core.common.GlobalId
import com.jarvis.tidb.core.common.SoftDeleteMetadata
import com.jarvis.tidb.core.entity.InstrumentEntity
import com.jarvis.tidb.signals.entity.SignalEntity

/**
 * SECTION 1 — TRADE LIFECYCLE
 *
 * A [TradeEntity] is the durable record of everything JARVIS did in response to a single
 * Signal (formerly Module 2), from entry to final close.
 *
 * v1.0 consolidation: now that `core`, `signals`, and `analytics` all live in one physical
 * `TradingIntelligenceDatabase`, `signalId` and `instrumentId` are upgraded from logical-only
 * references (validated purely at the repository layer, back when these were three separate
 * SQLite files) to real Room `@ForeignKey` constraints. Repository-layer validation via
 * `SignalRepository.exists()` / `InstrumentRepository.exists()` is kept in `TradeRepositoryImpl`
 * regardless, so callers get an immediate, descriptive `IllegalArgumentException` instead of a
 * raw SQLite foreign-key-constraint failure.
 */

enum class TradeStatus {
    PENDING,
    OPEN,
    PARTIALLY_CLOSED,
    CLOSED,
    CANCELLED
}

enum class TradeCloseReason {
    TARGET_HIT,
    STOP_LOSS_HIT,
    MANUAL_EXIT,
    TRAILING_STOP,
    TIME_EXIT,
    CANCELLED,
    STILL_OPEN
}

enum class TradeDirection {
    LONG,
    SHORT
}

enum class ExecutionType {
    ENTRY,
    ADD_ON,
    PARTIAL_EXIT,
    FULL_EXIT,
    STOP_LOSS,
    TARGET,
    MANUAL,
    CANCELLATION
}

enum class ExecutionQuality {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
    UNKNOWN
}

enum class FeeType {
    BROKERAGE,
    EXCHANGE_CHARGE,
    STT,
    GST,
    STAMP_DUTY,
    SEBI_CHARGE,
    SLIPPAGE,
    OTHER
}

@Entity(
    tableName = "trades",
    foreignKeys = [
        ForeignKey(
            entity = SignalEntity::class,
            parentColumns = ["signalId"],
            childColumns = ["signalId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = InstrumentEntity::class,
            parentColumns = ["instrumentId"],
            childColumns = ["instrumentId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["signalId"]),
        Index(value = ["instrumentId"]),
        Index(value = ["status"]),
        Index(value = ["direction"]),
        Index(value = ["entryTimestamp"]),
        Index(value = ["closeTimestamp"]),
        Index(value = ["closeReason"]),
        Index(value = ["instrumentId", "status"]),
        Index(value = ["entryTimestamp", "instrumentId"])
    ]
)
data class TradeEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    @ColumnInfo(name = "signalId")
    val signalId: Long,

    val signalUuid: String,

    @ColumnInfo(name = "instrumentId")
    val instrumentId: Long,

    val strategyId: String? = null,

    val direction: TradeDirection,

    val status: TradeStatus = TradeStatus.PENDING,

    val closeReason: TradeCloseReason = TradeCloseReason.STILL_OPEN,

    val plannedEntryPrice: Double,

    val plannedStopLoss: Double? = null,

    val plannedTarget: Double? = null,

    val averageEntryPrice: Double? = null,

    val averageExitPrice: Double? = null,

    val totalQuantity: Double,

    val openQuantity: Double,

    val closedQuantity: Double = 0.0,

    val entryTimestamp: Long? = null,

    val closeTimestamp: Long? = null,

    val holdingTimeMillis: Long? = null,

    val grossPnl: Double? = null,

    val netPnl: Double? = null,

    val pnlPercent: Double? = null,

    val riskAmount: Double? = null,

    val riskRewardPlanned: Double? = null,

    val riskRewardRealized: Double? = null,

    val maxAdverseExcursion: Double? = null,

    val maxFavorableExcursion: Double? = null,

    val executionQuality: ExecutionQuality = ExecutionQuality.UNKNOWN,

    val notes: String? = null,

    @Embedded
    val audit: AuditMetadata = AuditMetadata(),

    @Embedded
    val softDelete: SoftDeleteMetadata = SoftDeleteMetadata()
)

@Entity(
    tableName = "trade_executions",
    foreignKeys = [
        ForeignKey(
            entity = TradeEntity::class,
            parentColumns = ["rowId"],
            childColumns = ["tradeRowId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["tradeRowId"]),
        Index(value = ["executionType"]),
        Index(value = ["executedAt"]),
        Index(value = ["tradeRowId", "executedAt"])
    ]
)
data class TradeExecutionEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    val tradeRowId: Long,

    val executionType: ExecutionType,

    val price: Double,

    val quantity: Double,

    val executedAt: Long,

    val orderReference: String? = null,

    val slippage: Double? = null,

    val quality: ExecutionQuality = ExecutionQuality.UNKNOWN,

    val notes: String? = null,

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)

@Entity(
    tableName = "trade_exits",
    foreignKeys = [
        ForeignKey(
            entity = TradeEntity::class,
            parentColumns = ["rowId"],
            childColumns = ["tradeRowId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["tradeRowId"]),
        Index(value = ["exitReason"]),
        Index(value = ["exitedAt"])
    ]
)
data class TradeExitEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    val tradeRowId: Long,

    val exitReason: TradeCloseReason,

    val exitPrice: Double,

    val exitQuantity: Double,

    val exitedAt: Long,

    val realizedPnl: Double,

    val realizedPnlPercent: Double? = null,

    val holdingTimeMillis: Long? = null,

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)

@Entity(
    tableName = "trade_fees",
    foreignKeys = [
        ForeignKey(
            entity = TradeEntity::class,
            parentColumns = ["rowId"],
            childColumns = ["tradeRowId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["tradeRowId"]),
        Index(value = ["feeType"])
    ]
)
data class TradeFeesEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    val tradeRowId: Long,

    val executionRowId: Long? = null,

    val feeType: FeeType,

    val amount: Double,

    val currency: String = "INR",

    val chargedAt: Long,

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)

@Entity(
    tableName = "trade_journal",
    foreignKeys = [
        ForeignKey(
            entity = TradeEntity::class,
            parentColumns = ["rowId"],
            childColumns = ["tradeRowId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["tradeRowId"]),
        Index(value = ["author"]),
        Index(value = ["createdAt"])
    ]
)
data class TradeJournalEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    val tradeRowId: Long,

    val entryText: String,

    val author: String = "system",

    val tag: String? = null,

    val createdAt: Long = System.currentTimeMillis(),

    @Embedded
    val softDelete: SoftDeleteMetadata = SoftDeleteMetadata()
)

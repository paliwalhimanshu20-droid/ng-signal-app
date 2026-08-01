package com.jarvis.tidb.analytics.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jarvis.tidb.core.common.AuditMetadata
import com.jarvis.tidb.core.common.GlobalId
import com.jarvis.tidb.core.common.SoftDeleteMetadata
import com.jarvis.tidb.core.entity.InstrumentEntity

/**
 * SECTION 6 — PORTFOLIO INTELLIGENCE
 *
 * [PortfolioEntity] is the top-level book (JARVIS currently manages one live portfolio, but the
 * model supports many — e.g. paper vs. live, or multiple strategies run in isolation).
 * [PortfolioPositionEntity] holds current per-instrument holdings, [PortfolioAllocationEntity]
 * tracks target-vs-actual allocation weights, [PortfolioRiskEntity] is a point-in-time risk
 * snapshot, and [CapitalMovementEntity] is the append-only ledger of every deposit/withdrawal/
 * fee/settlement affecting cash balance.
 */

enum class PositionStatus {
    OPEN,
    CLOSED
}

enum class CapitalMovementType {
    DEPOSIT,
    WITHDRAWAL,
    REALIZED_PNL,
    FEE_SETTLEMENT,
    DIVIDEND,
    INTEREST,
    ADJUSTMENT
}

@Entity(
    tableName = "portfolios",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["name"], unique = true)
    ]
)
data class PortfolioEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    val name: String,

    val isLive: Boolean = true,

    val baseCurrency: String = "INR",

    val cashBalance: Double = 0.0,

    val availableCapital: Double = 0.0,

    val totalExposure: Double = 0.0,

    val unrealizedPnl: Double = 0.0,

    val realizedPnl: Double = 0.0,

    val balanceUpdatedAt: Long = System.currentTimeMillis(),

    @Embedded
    val audit: AuditMetadata = AuditMetadata(),

    @Embedded
    val softDelete: SoftDeleteMetadata = SoftDeleteMetadata()
)

@Entity(
    tableName = "portfolio_positions",
    foreignKeys = [
        ForeignKey(
            entity = PortfolioEntity::class,
            parentColumns = ["rowId"],
            childColumns = ["portfolioRowId"],
            onDelete = ForeignKey.CASCADE
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
        Index(value = ["portfolioRowId"]),
        Index(value = ["instrumentId"]),
        Index(value = ["status"]),
        Index(value = ["portfolioRowId", "instrumentId", "status"])
    ]
)
data class PortfolioPositionEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    val portfolioRowId: Long,

    val instrumentId: Long,

    val relatedTradeRowId: Long? = null,

    val status: PositionStatus = PositionStatus.OPEN,

    val quantity: Double,

    val averagePrice: Double,

    val currentPrice: Double? = null,

    val unrealizedPnl: Double? = null,

    val realizedPnl: Double = 0.0,

    val openedAt: Long,

    val closedAt: Long? = null,

    val positionUpdatedAt: Long = System.currentTimeMillis(),

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)

/** Target vs. actual allocation weight, tracked per instrument or per strategy (see [scopeKey]). */
@Entity(
    tableName = "portfolio_allocations",
    foreignKeys = [
        ForeignKey(
            entity = PortfolioEntity::class,
            parentColumns = ["rowId"],
            childColumns = ["portfolioRowId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["portfolioRowId"]),
        Index(value = ["scopeKey"]),
        Index(value = ["asOf"])
    ]
)
data class PortfolioAllocationEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    val portfolioRowId: Long,

    /** instrumentId (as string), strategyId, or asset-class label depending on how allocation is sliced. */
    val scopeKey: String,

    val targetWeightPercent: Double,

    val actualWeightPercent: Double,

    val driftPercent: Double,

    val asOf: Long = System.currentTimeMillis(),

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)

/** Point-in-time portfolio risk snapshot. Append-only — a new row per computation, never updated in place. */
@Entity(
    tableName = "portfolio_risk",
    foreignKeys = [
        ForeignKey(
            entity = PortfolioEntity::class,
            parentColumns = ["rowId"],
            childColumns = ["portfolioRowId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["portfolioRowId"]),
        Index(value = ["computedAt"])
    ]
)
data class PortfolioRiskEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    val portfolioRowId: Long,

    val totalExposure: Double,

    val exposurePercentOfCapital: Double,

    val diversificationScore: Double? = null,

    val concentrationRiskPercent: Double? = null,

    val valueAtRisk: Double? = null,

    val maxDrawdownToDate: Double? = null,

    val openPositionCount: Int,

    val computedAt: Long = System.currentTimeMillis(),

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)

/** Append-only ledger of everything affecting cash balance. This is the audit trail behind [PortfolioEntity.cashBalance]. */
@Entity(
    tableName = "capital_movements",
    foreignKeys = [
        ForeignKey(
            entity = PortfolioEntity::class,
            parentColumns = ["rowId"],
            childColumns = ["portfolioRowId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["portfolioRowId"]),
        Index(value = ["movementType"]),
        Index(value = ["occurredAt"]),
        Index(value = ["portfolioRowId", "occurredAt"])
    ]
)
data class CapitalMovementEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    val portfolioRowId: Long,

    val movementType: CapitalMovementType,

    val amount: Double,

    val balanceAfter: Double,

    val relatedTradeRowId: Long? = null,

    val note: String? = null,

    val occurredAt: Long = System.currentTimeMillis(),

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)

/**
 * v1.0 consolidation item 4 — PORTFOLIO SNAPSHOTS.
 *
 * An immutable, point-in-time freeze of a portfolio's full state: cash, exposure, P&L, open
 * position count, and a `positionsJson` payload capturing every open position's instrument,
 * quantity, average price, and unrealized P&L at that moment. Deliberately denormalized (a
 * JSON blob of positions rather than a join to the live, mutable `portfolio_positions` table)
 * because a snapshot must remain byte-for-byte reconstructable even after later trades close
 * or modify those same positions — that's the whole point of a snapshot.
 *
 * `snapshotType` distinguishes the cadence a snapshot was taken at (daily close, month-end,
 * or an ad-hoc AI-requested comparison point), which is exactly the granularity `Daily reports
 * / Monthly reports / AI comparisons / Portfolio evolution` (per the consolidation brief) need
 * to query against without re-deriving history from `capital_movements` + `portfolio_positions`
 * on every read. No update method is exposed on the DAO — insert-only, same immutability
 * convention as every other append-only table in this database.
 */
enum class PortfolioSnapshotType {
    DAILY,
    MONTHLY,
    AD_HOC,
    AI_COMPARISON
}

@Entity(
    tableName = "portfolio_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = PortfolioEntity::class,
            parentColumns = ["rowId"],
            childColumns = ["portfolioRowId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["portfolioRowId"]),
        Index(value = ["snapshotType"]),
        Index(value = ["snapshotAt"]),
        Index(value = ["portfolioRowId", "snapshotType", "snapshotAt"])
    ]
)
data class PortfolioSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    val portfolioRowId: Long,

    val snapshotType: PortfolioSnapshotType,

    val snapshotAt: Long = System.currentTimeMillis(),

    val cashBalance: Double,

    val totalExposure: Double,

    val unrealizedPnl: Double,

    val realizedPnl: Double,

    val netAssetValue: Double,

    val openPositionCount: Int,

    /** Frozen array of {instrumentId, quantity, averagePrice, currentPrice, unrealizedPnl} for every open position at snapshot time. */
    val positionsJson: String,

    /** Frozen array of {scopeKey, targetWeightPercent, actualWeightPercent} at snapshot time, mirroring PortfolioAllocationEntity. */
    val allocationsJson: String? = null,

    val note: String? = null,

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)

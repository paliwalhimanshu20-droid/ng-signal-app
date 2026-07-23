package com.jarvis.tidb.core.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The single latest live quote for an instrument. `instrumentId` remains the primary key (not
 * autoGenerate) precisely so there can only ever be one row per instrument — every update is
 * an upsert (REPLACE), never an insert of a new row. Tick-by-tick history, if ever needed, is
 * a separate future table; this table is intentionally small and hot.
 *
 * Revision 1: adds [uuid] and embedded [audit]/[softDelete] per module-wide policy. Note that
 * because REPLACE upserts this row wholesale, [audit].version and [audit].updatedAt are what
 * downstream consumers should watch to detect a fresh tick versus a stale cached read — not
 * [uuid], which is regenerated on every upsert. Soft delete here means "instrument delisted /
 * feed withdrawn", not a normal lifecycle event.
 */
@Entity(
    tableName = "live_market_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = InstrumentEntity::class,
            parentColumns = ["instrumentId"],
            childColumns = ["instrumentId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["isDeleted"])
    ]
)
data class LiveMarketSnapshotEntity(
    @PrimaryKey
    @ColumnInfo(name = "instrumentId")
    val instrumentId: Long,

    @ColumnInfo(name = "uuid")
    val uuid: String = GlobalId.new(),

    @ColumnInfo(name = "lastPrice")
    val lastPrice: Double,

    @ColumnInfo(name = "bid")
    val bid: Double,

    @ColumnInfo(name = "ask")
    val ask: Double,

    @ColumnInfo(name = "spread")
    val spread: Double,

    @ColumnInfo(name = "volume")
    val volume: Long,

    @ColumnInfo(name = "openInterest")
    val openInterest: Long? = null,

    @ColumnInfo(name = "vwap")
    val vwap: Double,

    @ColumnInfo(name = "dayHigh")
    val dayHigh: Double,

    @ColumnInfo(name = "dayLow")
    val dayLow: Double,

    @ColumnInfo(name = "previousClose")
    val previousClose: Double,

    @ColumnInfo(name = "marketStatus")
    val marketStatus: MarketStatus,

    @Embedded
    val audit: AuditMetadata,

    @Embedded
    val softDelete: SoftDeleteMetadata = SoftDeleteMetadata.notDeleted()
)

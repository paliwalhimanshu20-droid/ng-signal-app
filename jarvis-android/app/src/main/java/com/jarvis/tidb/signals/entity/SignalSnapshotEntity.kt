package com.jarvis.tidb.signals.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A frozen record of market conditions at the moment a Signal was generated.
 *
 * This is deliberately write-once: the repository layer exposes no update method for this
 * entity (see `SignalSnapshotRepository`). Without an immutable snapshot, "why did the signal
 * fire" becomes unanswerable in hindsight because the live indicator values will have moved on.
 * This is also the row future Backtesting/AI Learning modules will replay against.
 *
 * One-to-one with its parent Signal (one snapshot per signal), enforced by a unique index on
 * `signalId`. Cascade-deletes with the parent signal.
 */
@Entity(
    tableName = "signal_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = SignalEntity::class,
            parentColumns = ["signalId"],
            childColumns = ["signalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["signalId"], unique = true),
        Index(value = ["uuid"], unique = true)
    ]
)
data class SignalSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "snapshotId")
    val snapshotId: Long = 0,

    @ColumnInfo(name = "uuid")
    val uuid: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "signalId")
    val signalId: Long,

    @ColumnInfo(name = "open")
    val open: Double,

    @ColumnInfo(name = "high")
    val high: Double,

    @ColumnInfo(name = "low")
    val low: Double,

    @ColumnInfo(name = "close")
    val close: Double,

    @ColumnInfo(name = "volume")
    val volume: Double,

    @ColumnInfo(name = "atr")
    val atr: Double? = null,

    @ColumnInfo(name = "ema20")
    val ema20: Double? = null,

    @ColumnInfo(name = "ema50")
    val ema50: Double? = null,

    @ColumnInfo(name = "ema200")
    val ema200: Double? = null,

    @ColumnInfo(name = "rsi")
    val rsi: Double? = null,

    @ColumnInfo(name = "macd")
    val macd: Double? = null,

    @ColumnInfo(name = "macdSignal")
    val macdSignal: Double? = null,

    @ColumnInfo(name = "macdHistogram")
    val macdHistogram: Double? = null,

    @ColumnInfo(name = "adx")
    val adx: Double? = null,

    @ColumnInfo(name = "supertrend")
    val supertrend: Double? = null,

    @ColumnInfo(name = "volatility")
    val volatility: Double? = null,

    @ColumnInfo(name = "marketTrend")
    val marketTrend: MarketTrend = MarketTrend.UNKNOWN,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis()
)

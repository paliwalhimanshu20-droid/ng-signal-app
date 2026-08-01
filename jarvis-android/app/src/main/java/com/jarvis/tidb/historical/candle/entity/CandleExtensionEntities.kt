package com.jarvis.tidb.historical.candle.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jarvis.tidb.core.common.AuditMetadata
import com.jarvis.tidb.core.common.GlobalId
import com.jarvis.tidb.core.entity.HistoricalCandleEntity
import com.jarvis.tidb.core.entity.InstrumentEntity

/**
 * HISTORICAL MARKET DATA PLATFORM — Candle Storage extensions (schema v5).
 *
 * The candle table itself ([HistoricalCandleEntity], multi-exchange/multi-asset-class/
 * multi-timeframe already) is Module 1 and is NOT touched here. This package adds only what
 * that table doesn't already cover: an immutable correction trail (versioning — a candle can
 * be corrected without losing what it used to say) and gap bookkeeping (detected holes in a
 * (instrument, timeframe) series, and whether/how they were resolved). Duplicate prevention is
 * already enforced by the existing unique index on (instrumentId, timeframe, timestamp).
 */

enum class GapStatus(val value: String) {
    DETECTED("DETECTED"),
    BACKFILLING("BACKFILLING"),
    BACKFILLED("BACKFILLED"),
    IGNORED("IGNORED"),
    UNRESOLVABLE("UNRESOLVABLE");

    companion object {
        fun from(value: String): GapStatus = entries.firstOrNull { it.value == value } ?: DETECTED
    }
}

enum class GapReason(val value: String) {
    MARKET_CLOSED("MARKET_CLOSED"),
    HOLIDAY("HOLIDAY"),
    FEED_OUTAGE("FEED_OUTAGE"),
    LOW_LIQUIDITY("LOW_LIQUIDITY"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun from(value: String): GapReason = entries.firstOrNull { it.value == value } ?: UNKNOWN
    }
}

/**
 * One immutable prior state of a candle, written whenever an existing bar is corrected
 * (re-import found a different close, a vendor issued a restated bar, etc.). The live row in
 * `historical_candles` always holds the current value; this table is the audit trail.
 */
@Entity(
    tableName = "candle_versions",
    foreignKeys = [
        ForeignKey(
            entity = HistoricalCandleEntity::class,
            parentColumns = ["candleId"],
            childColumns = ["candleId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["candleId"]),
        Index(value = ["candleId", "versionNumber"], unique = true, name = "idx_candle_version_unique")
    ]
)
data class CandleVersionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "candleVersionId")
    val candleVersionId: Long = 0L,

    @ColumnInfo(name = "uuid")
    val uuid: String = GlobalId.new(),

    @ColumnInfo(name = "candleId")
    val candleId: Long,

    /** 1-based; version N is the state that was replaced when correction N happened. */
    @ColumnInfo(name = "versionNumber")
    val versionNumber: Int,

    @ColumnInfo(name = "open")
    val open: Double,

    @ColumnInfo(name = "high")
    val high: Double,

    @ColumnInfo(name = "low")
    val low: Double,

    @ColumnInfo(name = "close")
    val close: Double,

    @ColumnInfo(name = "volume")
    val volume: Long,

    @ColumnInfo(name = "openInterest")
    val openInterest: Long? = null,

    @ColumnInfo(name = "qualityScore")
    val qualityScore: Double,

    @ColumnInfo(name = "changeReason")
    val changeReason: String,

    @ColumnInfo(name = "changedBy")
    val changedBy: String = "SYSTEM",

    @ColumnInfo(name = "supersededAt")
    val supersededAt: Long = System.currentTimeMillis()
)

/**
 * A detected hole in a (instrument, timeframe) candle series — one or more expected bars
 * missing between two known-good candles. Detection logic lives in the Quality Engine
 * ([com.jarvis.tidb.historical.quality]); this table is just where the finding is recorded and
 * tracked through to resolution so a backfill job can be dispatched and closed out.
 */
@Entity(
    tableName = "candle_gaps",
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
        Index(value = ["instrumentId", "timeframe"]),
        Index(value = ["status"]),
        Index(value = ["gapStart"])
    ]
)
data class CandleGapEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "gapId")
    val gapId: Long = 0L,

    @ColumnInfo(name = "uuid")
    val uuid: String = GlobalId.new(),

    @ColumnInfo(name = "instrumentId")
    val instrumentId: Long,

    @ColumnInfo(name = "timeframe")
    val timeframe: String,

    /** Epoch millis of the last known-good candle before the gap. */
    @ColumnInfo(name = "gapStart")
    val gapStart: Long,

    /** Epoch millis of the first known-good candle after the gap. */
    @ColumnInfo(name = "gapEnd")
    val gapEnd: Long,

    /** How many bars should exist between gapStart and gapEnd at this timeframe's cadence. */
    @ColumnInfo(name = "expectedCandleCount")
    val expectedCandleCount: Int,

    @ColumnInfo(name = "status")
    val status: GapStatus = GapStatus.DETECTED,

    @ColumnInfo(name = "reason")
    val reason: GapReason = GapReason.UNKNOWN,

    /** Set once a [com.jarvis.tidb.historical.ingestion.entity.IngestionJobEntity] is dispatched to fill this gap. */
    @ColumnInfo(name = "backfillJobId")
    val backfillJobId: Long? = null,

    @ColumnInfo(name = "detectedAt")
    val detectedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "resolvedAt")
    val resolvedAt: Long? = null,

    @Embedded
    val audit: AuditMetadata = AuditMetadata()
)

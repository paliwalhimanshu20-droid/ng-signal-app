package com.jarvis.tidb.core.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * OHLCV candle data. This is expected to be the largest table in the database (millions of
 * rows once multiple instruments x multiple timeframes x years of history are loaded), so
 * indexing is deliberately narrow and query-driven — see docs/database/TRADING-001 for the
 * indexing rationale.
 *
 * The composite unique index on (instrumentId, timeframe, timestamp) is the single most
 * important index in this module: it is both the natural key of a candle and the index that
 * makes upserts (backfill / re-import) and range scans for charting fast.
 *
 * Revision 1 additions (§5, data provenance):
 *  - [sourceId] — which concrete feed/vendor connection produced this row (finer-grained than
 *    the existing [source] enum, which only says the category of source).
 *  - [importBatchId] — groups every row inserted by a single import/backfill run, so a bad
 *    batch can be identified and rolled back as a unit.
 *  - [checksum] — optional hash of the raw upstream payload for this bar, enabling future
 *    dedup/integrity verification against the vendor.
 *
 * Also adds [uuid] and embedded [audit]/[softDelete] per module-wide policy. The pre-existing
 * [importedAt] field is unchanged and keeps its original meaning (first-import timestamp);
 * [audit].updatedAt tracks any later correction/re-import of the same bar.
 */
@Entity(
    tableName = "historical_candles",
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
        Index(
            value = ["instrumentId", "timeframe", "timestamp"],
            unique = true,
            name = "idx_candle_instrument_timeframe_timestamp"
        ),
        Index(value = ["timestamp"]),
        Index(value = ["instrumentId"]),
        Index(value = ["timeframe"]),
        Index(value = ["uuid"], unique = true),
        Index(value = ["importBatchId"]),
        Index(value = ["isDeleted"])
    ]
)
data class HistoricalCandleEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "candleId")
    val candleId: Long = 0L,

    @ColumnInfo(name = "uuid")
    val uuid: String = GlobalId.new(),

    @ColumnInfo(name = "instrumentId")
    val instrumentId: Long,

    @ColumnInfo(name = "timeframe")
    val timeframe: Timeframe,

    /** Candle open timestamp, epoch millis UTC. */
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

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

    @ColumnInfo(name = "source")
    val source: CandleSource,

    /** 0.0–1.0 confidence score used by downstream modules to weight/filter candles. */
    @ColumnInfo(name = "qualityScore")
    val qualityScore: Double = 1.0,

    /** First-import timestamp for this bar (epoch millis). Preserved from Module 1 v1. */
    @ColumnInfo(name = "importedAt")
    val importedAt: Long,

    /** Fine-grained feed/vendor-connection identifier that produced this row. Optional. */
    @ColumnInfo(name = "sourceId")
    val sourceId: String? = null,

    /** Identifier grouping all rows written by a single import/backfill run. Optional. */
    @ColumnInfo(name = "importBatchId")
    val importBatchId: String? = null,

    /** Hash of the raw upstream payload, for future dedup/integrity checks. Optional. */
    @ColumnInfo(name = "checksum")
    val checksum: String? = null,

    @Embedded
    val audit: AuditMetadata,

    @Embedded
    val softDelete: SoftDeleteMetadata = SoftDeleteMetadata.notDeleted()
)

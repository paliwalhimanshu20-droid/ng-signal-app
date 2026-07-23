package com.jarvis.tidb.signals.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jarvis.tidb.core.common.GlobalId
import com.jarvis.tidb.core.entity.InstrumentEntity

/**
 * MODULE 2 — SIGNAL INTELLIGENCE ENGINE (unchanged functionality, carried into the unified
 * TradingIntelligenceDatabase as of v1.0).
 *
 * A Signal is a prediction JARVIS made — what, at what price, with what risk, and when it stops
 * being relevant. It does NOT execute trades.
 *
 * §1 architectural note (v1.0 consolidation): `instrumentId` was a *logical-only* foreign key
 * while `SignalDatabase` was a physically separate Room database from `TidbDatabase` — SQLite
 * cannot enforce a `@ForeignKey` across two `.db` files. Now that everything lives in one
 * `TradingIntelligenceDatabase`, that constraint is upgraded to a real Room `@ForeignKey` (see
 * §2 of the architecture doc). This is additive: every `instrumentId` that was already being
 * validated via `InstrumentRepository.exists()` at write time continues to satisfy the new
 * constraint automatically, so no existing data is invalidated by this change.
 *
 * `SignalEntity` predates the shared `core.common.AuditMetadata`/`SoftDeleteMetadata` classes
 * and uses flat `updatedAt`/`updatedBy`/`version`/`isDeleted`/`deletedAt` columns rather than
 * `@Embedded` metadata. Preserved as-is per "do not redesign the architecture."
 */
@Entity(
    tableName = "signals",
    foreignKeys = [
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
        Index(value = ["instrumentId"]),
        Index(value = ["status"]),
        Index(value = ["signalType"]),
        Index(value = ["timeframe"]),
        Index(value = ["confidenceScore"]),
        Index(value = ["generatedAt"]),
        Index(value = ["isDeleted"]),
        Index(value = ["instrumentId", "status"])
    ]
)
data class SignalEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "signalId")
    val signalId: Long = 0L,

    @ColumnInfo(name = "uuid")
    val uuid: String = GlobalId.new(),

    @ColumnInfo(name = "instrumentId")
    val instrumentId: Long,

    @ColumnInfo(name = "signalType")
    val signalType: SignalType,

    @ColumnInfo(name = "status")
    val status: SignalStatus = SignalStatus.ACTIVE,

    /** Kept as a raw String (not Module 1's `Timeframe` enum) to keep this module decoupled from Module 1's schema types. */
    @ColumnInfo(name = "timeframe")
    val timeframe: String,

    @ColumnInfo(name = "entryPrice")
    val entryPrice: Double,

    @ColumnInfo(name = "stopLoss")
    val stopLoss: Double? = null,

    @ColumnInfo(name = "target")
    val target: Double? = null,

    @ColumnInfo(name = "confidenceScore")
    val confidenceScore: Double,

    @ColumnInfo(name = "marketTrend")
    val marketTrend: MarketTrend = MarketTrend.UNKNOWN,

    @ColumnInfo(name = "expiresAt")
    val expiresAt: Long? = null,

    @ColumnInfo(name = "generatedAt")
    val generatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "generatedBy")
    val generatedBy: String = "unknown",

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updatedBy")
    val updatedBy: String = "SYSTEM",

    @ColumnInfo(name = "version")
    val version: Long = 1L,

    @ColumnInfo(name = "isDeleted")
    val isDeleted: Boolean = false,

    @ColumnInfo(name = "deletedAt")
    val deletedAt: Long? = null
)

/** Human/AI-readable reasons supporting a signal — append-only. */
@Entity(
    tableName = "signal_reasons",
    foreignKeys = [
        ForeignKey(entity = SignalEntity::class, parentColumns = ["signalId"], childColumns = ["signalId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["signalId"])]
)
data class SignalReasonEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "reasonId")
    val reasonId: Long = 0L,

    @ColumnInfo(name = "uuid")
    val uuid: String = GlobalId.new(),

    @ColumnInfo(name = "signalId")
    val signalId: Long,

    @ColumnInfo(name = "reasonText")
    val reasonText: String,

    @ColumnInfo(name = "weight")
    val weight: Double = 1.0,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis()
)

/** Immutable market-context snapshot captured at the moment the signal was generated — append-only, no update method. */
@Entity(
    tableName = "signal_snapshots",
    foreignKeys = [
        ForeignKey(entity = SignalEntity::class, parentColumns = ["signalId"], childColumns = ["signalId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["signalId"], unique = true)]
)
data class SignalSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "snapshotId")
    val snapshotId: Long = 0L,

    @ColumnInfo(name = "uuid")
    val uuid: String = GlobalId.new(),

    @ColumnInfo(name = "signalId")
    val signalId: Long,

    @ColumnInfo(name = "lastPrice")
    val lastPrice: Double,

    @ColumnInfo(name = "volume")
    val volume: Long = 0,

    @ColumnInfo(name = "marketTrend")
    val marketTrend: MarketTrend = MarketTrend.UNKNOWN,

    /** Free-form JSON — indicator values, order book depth, etc., captured at generation time. */
    @ColumnInfo(name = "indicatorsJson")
    val indicatorsJson: String? = null,

    @ColumnInfo(name = "capturedAt")
    val capturedAt: Long = System.currentTimeMillis()
)

/** Append-only status-transition audit trail — no update method, exactly the immutability convention used across the whole TIDB. */
@Entity(
    tableName = "signal_lifecycle",
    foreignKeys = [
        ForeignKey(entity = SignalEntity::class, parentColumns = ["signalId"], childColumns = ["signalId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["signalId"]), Index(value = ["changedAt"])]
)
data class SignalLifecycleEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "lifecycleId")
    val lifecycleId: Long = 0L,

    @ColumnInfo(name = "uuid")
    val uuid: String = GlobalId.new(),

    @ColumnInfo(name = "signalId")
    val signalId: Long,

    @ColumnInfo(name = "previousStatus")
    val previousStatus: String?,

    @ColumnInfo(name = "newStatus")
    val newStatus: String,

    @ColumnInfo(name = "reason")
    val reason: String? = null,

    @ColumnInfo(name = "changedBy")
    val changedBy: String = "SYSTEM",

    @ColumnInfo(name = "changedAt")
    val changedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "signal_tags",
    foreignKeys = [
        ForeignKey(entity = SignalEntity::class, parentColumns = ["signalId"], childColumns = ["signalId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["signalId"]), Index(value = ["tag"]), Index(value = ["signalId", "tag"], unique = true)]
)
data class SignalTagEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "tagId")
    val tagId: Long = 0L,

    @ColumnInfo(name = "uuid")
    val uuid: String = GlobalId.new(),

    @ColumnInfo(name = "signalId")
    val signalId: Long,

    @ColumnInfo(name = "tag")
    val tag: String,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "signal_notes",
    foreignKeys = [
        ForeignKey(entity = SignalEntity::class, parentColumns = ["signalId"], childColumns = ["signalId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["signalId"]), Index(value = ["author"])]
)
data class SignalNoteEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "noteId")
    val noteId: Long = 0L,

    @ColumnInfo(name = "uuid")
    val uuid: String = GlobalId.new(),

    @ColumnInfo(name = "signalId")
    val signalId: Long,

    @ColumnInfo(name = "noteText")
    val noteText: String,

    @ColumnInfo(name = "author")
    val author: String = "system",

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis()
)

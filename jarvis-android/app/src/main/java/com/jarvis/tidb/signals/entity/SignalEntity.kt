package com.jarvis.tidb.signals.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * The primary Signal record.
 *
 * A Signal is a prediction JARVIS made — what, at what price, with what risk, and when it
 * stops being relevant. It does NOT execute trades and does NOT belong to a strategy engine;
 * this module only records and manages signals that some other (future) module generates.
 *
 * `instrumentId` is a logical foreign key into Module 1's `InstrumentEntity`. It is intentionally
 * NOT declared as a Room `@ForeignKey` here because Module 2 must never depend on Module 1's
 * table structure directly — only on its repository interface. Referential validity against
 * Module 1 is enforced at the repository layer (see `SignalRepositoryImpl`), not by SQLite.
 */
@Entity(
    tableName = "signals",
    indices = [
        Index(value = ["instrumentId"]),
        Index(value = ["generatedAt"]),
        Index(value = ["status"]),
        Index(value = ["confidenceScore"]),
        Index(value = ["timeframe"]),
        Index(value = ["signalType"]),
        Index(value = ["uuid"], unique = true),
        // Composite index: the most common dashboard query is
        // "active signals for instrument X on timeframe Y, newest first".
        Index(value = ["instrumentId", "timeframe", "status", "generatedAt"])
    ]
)
data class SignalEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "signalId")
    val signalId: Long = 0,

    @ColumnInfo(name = "uuid")
    val uuid: String = UUID.randomUUID().toString(),

    /** Logical FK -> Module 1 InstrumentEntity.instrumentId. Not a Room FK — see class doc. */
    @ColumnInfo(name = "instrumentId")
    val instrumentId: Long,

    /** Raw timeframe string (e.g. "5m", "1H", "Daily") — mirrors Module 1's Timeframe.value. */
    @ColumnInfo(name = "timeframe")
    val timeframe: String,

    @ColumnInfo(name = "signalType")
    val signalType: SignalType,

    @ColumnInfo(name = "confidenceScore")
    val confidenceScore: Double,

    @ColumnInfo(name = "strengthScore")
    val strengthScore: Double,

    @ColumnInfo(name = "entryPrice")
    val entryPrice: Double,

    @ColumnInfo(name = "stopLoss")
    val stopLoss: Double,

    @ColumnInfo(name = "target1")
    val target1: Double? = null,

    @ColumnInfo(name = "target2")
    val target2: Double? = null,

    @ColumnInfo(name = "target3")
    val target3: Double? = null,

    @ColumnInfo(name = "riskRewardRatio")
    val riskRewardRatio: Double? = null,

    @ColumnInfo(name = "generatedAt")
    val generatedAt: Long,

    @ColumnInfo(name = "expiresAt")
    val expiresAt: Long? = null,

    @ColumnInfo(name = "status")
    val status: SignalStatus = SignalStatus.ACTIVE,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "createdBy")
    val createdBy: String,

    @ColumnInfo(name = "updatedBy")
    val updatedBy: String,

    @ColumnInfo(name = "version")
    val version: Int = 1,

    @ColumnInfo(name = "isDeleted")
    val isDeleted: Boolean = false,

    @ColumnInfo(name = "deletedAt")
    val deletedAt: Long? = null
)

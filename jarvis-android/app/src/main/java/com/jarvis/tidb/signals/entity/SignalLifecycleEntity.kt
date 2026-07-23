package com.jarvis.tidb.signals.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * One row per state transition a Signal goes through over its life.
 *
 * `previousStatus`/`newStatus` are free-text rather than the [SignalStatus] enum on purpose:
 * the spec's example lifecycle (ACTIVE -> EXECUTED -> TARGET1 HIT -> TARGET2 HIT -> CLOSED)
 * includes granular milestones ("TARGET1 HIT") that are richer than the four coarse values on
 * `SignalEntity.status`. This table is the detailed audit log; `SignalEntity.status` stays the
 * coarse current-state field DAOs filter/sort on.
 *
 * Never updated or deleted once written (append-only), except via cascade when the parent
 * Signal itself is removed.
 */
@Entity(
    tableName = "signal_lifecycle_events",
    foreignKeys = [
        ForeignKey(
            entity = SignalEntity::class,
            parentColumns = ["signalId"],
            childColumns = ["signalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["signalId"]),
        Index(value = ["changedAt"]),
        Index(value = ["uuid"], unique = true)
    ]
)
data class SignalLifecycleEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "lifecycleId")
    val lifecycleId: Long = 0,

    @ColumnInfo(name = "uuid")
    val uuid: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "signalId")
    val signalId: Long,

    @ColumnInfo(name = "previousStatus")
    val previousStatus: String?,

    @ColumnInfo(name = "newStatus")
    val newStatus: String,

    @ColumnInfo(name = "reason")
    val reason: String? = null,

    @ColumnInfo(name = "changedAt")
    val changedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "changedBy")
    val changedBy: String
)

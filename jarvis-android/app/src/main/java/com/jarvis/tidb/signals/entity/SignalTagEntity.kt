package com.jarvis.tidb.signals.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single tag attached to a Signal (e.g. "High Confidence", "Breakout", "Swing"). A signal may
 * have any number of tags; a tag string may be reused freely across signals. Uniqueness is
 * enforced per (signalId, tag) pair so the same tag can't be attached twice to one signal.
 */
@Entity(
    tableName = "signal_tags",
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
        Index(value = ["tag"]),
        Index(value = ["signalId", "tag"], unique = true)
    ]
)
data class SignalTagEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "tagId")
    val tagId: Long = 0,

    @ColumnInfo(name = "signalId")
    val signalId: Long,

    @ColumnInfo(name = "tag")
    val tag: String
)

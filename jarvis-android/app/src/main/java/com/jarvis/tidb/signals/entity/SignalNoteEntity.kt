package com.jarvis.tidb.signals.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A free-text note attached to a Signal, written by a human analyst or (in future) an AI
 * learning process. `author` is a free-text identifier (username, "system", "jarvis-ai", etc.)
 * rather than an enum since the set of possible authors is open-ended.
 */
@Entity(
    tableName = "signal_notes",
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
        Index(value = ["createdAt"])
    ]
)
data class SignalNoteEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "noteId")
    val noteId: Long = 0,

    @ColumnInfo(name = "signalId")
    val signalId: Long,

    @ColumnInfo(name = "author")
    val author: String,

    @ColumnInfo(name = "note")
    val note: String,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis()
)

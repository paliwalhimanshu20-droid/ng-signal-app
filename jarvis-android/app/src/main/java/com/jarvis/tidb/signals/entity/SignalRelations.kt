package com.jarvis.tidb.signals.entity

import androidx.room.Embedded
import androidx.room.Relation

/** Lightweight list read: signal + its tags only. */
data class SignalWithTags(
    @Embedded val signal: SignalEntity,
    @Relation(parentColumn = "signalId", entityColumn = "signalId")
    val tags: List<SignalTagEntity>
)

/** Full detail read: signal + reasons, snapshot, lifecycle, notes, tags. For detail screens, not list rendering. */
data class SignalFullDetail(
    @Embedded val signal: SignalEntity,
    @Relation(parentColumn = "signalId", entityColumn = "signalId")
    val reasons: List<SignalReasonEntity>,
    @Relation(parentColumn = "signalId", entityColumn = "signalId")
    val snapshot: SignalSnapshotEntity?,
    @Relation(parentColumn = "signalId", entityColumn = "signalId")
    val lifecycle: List<SignalLifecycleEntity>,
    @Relation(parentColumn = "signalId", entityColumn = "signalId")
    val tags: List<SignalTagEntity>,
    @Relation(parentColumn = "signalId", entityColumn = "signalId")
    val notes: List<SignalNoteEntity>
)

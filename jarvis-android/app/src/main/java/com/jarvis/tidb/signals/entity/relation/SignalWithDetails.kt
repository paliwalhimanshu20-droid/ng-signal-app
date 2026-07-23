package com.jarvis.tidb.signals.entity.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.jarvis.tidb.signals.entity.SignalEntity
import com.jarvis.tidb.signals.entity.SignalLifecycleEntity
import com.jarvis.tidb.signals.entity.SignalNoteEntity
import com.jarvis.tidb.signals.entity.SignalReasonEntity
import com.jarvis.tidb.signals.entity.SignalSnapshotEntity
import com.jarvis.tidb.signals.entity.SignalTagEntity

/**
 * A Signal hydrated with every child record in one query:
 * Signal -> Reasons -> Snapshot -> Lifecycle -> Tags -> Notes
 *
 * This is the read model for detail screens ("show me everything about this signal") and for
 * future AI/backtesting consumers that need the full picture in one shot. It is NOT used for
 * list/dashboard queries — those should stay on the lighter `SignalEntity`-only DAO methods to
 * avoid pulling six tables per row for something like an "active signals" list.
 */
data class SignalWithDetails(
    @Embedded
    val signal: SignalEntity,

    @Relation(
        parentColumn = "signalId",
        entityColumn = "signalId"
    )
    val reasons: List<SignalReasonEntity>,

    @Relation(
        parentColumn = "signalId",
        entityColumn = "signalId"
    )
    val snapshot: SignalSnapshotEntity?,

    @Relation(
        parentColumn = "signalId",
        entityColumn = "signalId"
    )
    val lifecycle: List<SignalLifecycleEntity>,

    @Relation(
        parentColumn = "signalId",
        entityColumn = "signalId"
    )
    val tags: List<SignalTagEntity>,

    @Relation(
        parentColumn = "signalId",
        entityColumn = "signalId"
    )
    val notes: List<SignalNoteEntity>
)

/** Lightweight variant for list screens that only need reasons + tags, not the full graph. */
data class SignalWithReasonsAndTags(
    @Embedded
    val signal: SignalEntity,

    @Relation(
        parentColumn = "signalId",
        entityColumn = "signalId"
    )
    val reasons: List<SignalReasonEntity>,

    @Relation(
        parentColumn = "signalId",
        entityColumn = "signalId"
    )
    val tags: List<SignalTagEntity>
)

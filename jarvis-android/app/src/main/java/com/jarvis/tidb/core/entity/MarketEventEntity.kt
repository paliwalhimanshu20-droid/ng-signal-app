package com.jarvis.tidb.core.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A discrete, timestamped market event (expiry, rollover, halt, circuit, volatility spike,
 * volume spike, news shock, gap open). This is the audit trail future modules (AI Learning,
 * Instrument DNA) will mine for pattern recognition around abnormal market behavior, and —
 * per Revision 1 §6 — one of the two entities (with [ContractEntity]) most directly relevant
 * to future Timeline-First Executive Memory integration: every row here is already a natural
 * timeline event candidate (see docs, §9 Timeline Integration Readiness).
 *
 * Revision 1: adds [uuid] and embedded [audit]/[softDelete] per module-wide policy.
 */
@Entity(
    tableName = "market_events",
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
        Index(value = ["instrumentId"]),
        Index(value = ["timestamp"]),
        Index(value = ["eventType"]),
        Index(value = ["instrumentId", "timestamp"]),
        Index(value = ["uuid"], unique = true),
        Index(value = ["isDeleted"])
    ]
)
data class MarketEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "eventId")
    val eventId: Long = 0L,

    @ColumnInfo(name = "uuid")
    val uuid: String = GlobalId.new(),

    @ColumnInfo(name = "instrumentId")
    val instrumentId: Long,

    @ColumnInfo(name = "eventType")
    val eventType: MarketEventType,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "severity")
    val severity: EventSeverity = EventSeverity.INFO,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "source")
    val source: String,

    /** Free-form JSON blob for event-type-specific structured data. */
    @ColumnInfo(name = "metadata")
    val metadata: String? = null,

    @Embedded
    val audit: AuditMetadata,

    @Embedded
    val softDelete: SoftDeleteMetadata = SoftDeleteMetadata.notDeleted()
)

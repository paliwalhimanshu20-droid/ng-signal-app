package com.jarvis.tidb.core.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Root of the market data hierarchy. Every [InstrumentEntity] and [MarketSessionEntity]
 * belongs to exactly one Exchange.
 *
 * Revision 1: adds a stable [uuid] (survives any future migration to Postgres/cloud/sync),
 * embedded [audit] and [softDelete] metadata. The local autoIncrement [exchangeId] remains
 * the primary key and all existing foreign keys continue to point at it — nothing that
 * referenced `exchangeId` needs to change.
 */
@Entity(
    tableName = "exchanges",
    indices = [
        Index(value = ["exchangeCode"], unique = true),
        Index(value = ["uuid"], unique = true),
        Index(value = ["status"]),
        Index(value = ["isDeleted"])
    ]
)
data class ExchangeEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "exchangeId")
    val exchangeId: Long = 0L,

    /** Globally unique, migration-stable identifier. Never reused, never reassigned. */
    @ColumnInfo(name = "uuid")
    val uuid: String = GlobalId.new(),

    @ColumnInfo(name = "exchangeCode")
    val exchangeCode: String,

    @ColumnInfo(name = "exchangeName")
    val exchangeName: String,

    @ColumnInfo(name = "timezone")
    val timezone: String,

    @ColumnInfo(name = "country")
    val country: String,

    @ColumnInfo(name = "currency")
    val currency: String,

    @ColumnInfo(name = "status")
    val status: RecordStatus = RecordStatus.ACTIVE,

    @Embedded
    val audit: AuditMetadata,

    @Embedded
    val softDelete: SoftDeleteMetadata = SoftDeleteMetadata.notDeleted()
)

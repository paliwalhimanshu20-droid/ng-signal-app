package com.jarvis.tidb.core.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Trading session definitions for an exchange (e.g. regular session, pre-open, evening
 * session for MCX commodities). Also used to encode holiday and early-close calendars.
 *
 * Revision 1: adds [uuid], [audit], [softDelete] per module-wide policy. No domain-specific
 * fields were requested for this entity.
 */
@Entity(
    tableName = "market_sessions",
    foreignKeys = [
        ForeignKey(
            entity = ExchangeEntity::class,
            parentColumns = ["exchangeId"],
            childColumns = ["exchangeId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["exchangeId"]),
        Index(value = ["uuid"], unique = true),
        Index(value = ["isDeleted"])
    ]
)
data class MarketSessionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "sessionId")
    val sessionId: Long = 0L,

    @ColumnInfo(name = "uuid")
    val uuid: String = GlobalId.new(),

    @ColumnInfo(name = "exchangeId")
    val exchangeId: Long,

    @ColumnInfo(name = "sessionName")
    val sessionName: String,

    /** Local time-of-day, "HH:mm" (24h), interpreted in [timezone]. */
    @ColumnInfo(name = "openTime")
    val openTime: String,

    @ColumnInfo(name = "closeTime")
    val closeTime: String,

    @ColumnInfo(name = "timezone")
    val timezone: String,

    @ColumnInfo(name = "holidayFlag")
    val holidayFlag: Boolean = false,

    @ColumnInfo(name = "earlyCloseFlag")
    val earlyCloseFlag: Boolean = false,

    @ColumnInfo(name = "remarks")
    val remarks: String? = null,

    @Embedded
    val audit: AuditMetadata,

    @Embedded
    val softDelete: SoftDeleteMetadata = SoftDeleteMetadata.notDeleted()
)

package com.jarvis.tidb.core.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A tradable instrument (e.g. NATGASMINI, CRUDEOIL, GOLD). Asset-class-agnostic by design so
 * the same table serves commodities, equities, futures, options, forex, and crypto without
 * schema changes — asset-class-specific behavior is driven by [AssetClass] / [InstrumentType]
 * plus downstream modules (Instrument DNA, Strategy Engine), not by new columns here.
 *
 * Revision 1 additions:
 *  - [uuid], [audit], [softDelete] — universal identity/audit/soft-delete, per module-wide policy.
 *  - [brokerInstrumentKey], [exchangeToken], [isin], [vendorMetadata] — optional external
 *    provider mapping (§4), so a broker/data-vendor integration module can resolve this
 *    instrument against its own identifiers without a new table.
 */
@Entity(
    tableName = "instruments",
    foreignKeys = [
        ForeignKey(
            entity = ExchangeEntity::class,
            parentColumns = ["exchangeId"],
            childColumns = ["exchangeId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["symbol"], unique = true),
        Index(value = ["uuid"], unique = true),
        Index(value = ["exchangeId"]),
        Index(value = ["assetClass"]),
        Index(value = ["status"]),
        Index(value = ["isDeleted"]),
        Index(value = ["brokerInstrumentKey"]),
        Index(value = ["exchangeToken"])
    ]
)
data class InstrumentEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "instrumentId")
    val instrumentId: Long = 0L,

    @ColumnInfo(name = "uuid")
    val uuid: String = GlobalId.new(),

    @ColumnInfo(name = "symbol")
    val symbol: String,

    @ColumnInfo(name = "displayName")
    val displayName: String,

    @ColumnInfo(name = "exchangeId")
    val exchangeId: Long,

    @ColumnInfo(name = "assetClass")
    val assetClass: AssetClass,

    @ColumnInfo(name = "instrumentType")
    val instrumentType: InstrumentType,

    @ColumnInfo(name = "tickSize")
    val tickSize: Double,

    @ColumnInfo(name = "lotSize")
    val lotSize: Int,

    @ColumnInfo(name = "multiplier")
    val multiplier: Double,

    @ColumnInfo(name = "quoteCurrency")
    val quoteCurrency: String,

    @ColumnInfo(name = "tradingCurrency")
    val tradingCurrency: String,

    @ColumnInfo(name = "tradingHours")
    val tradingHours: String,

    @ColumnInfo(name = "status")
    val status: RecordStatus = RecordStatus.ACTIVE,

    /** Broker-side instrument identifier (e.g. Zerodha/Angel/IBKR instrument key). Optional. */
    @ColumnInfo(name = "brokerInstrumentKey")
    val brokerInstrumentKey: String? = null,

    /** Exchange-assigned numeric/alphanumeric token (e.g. MCX scrip token). Optional. */
    @ColumnInfo(name = "exchangeToken")
    val exchangeToken: String? = null,

    /** ISIN, where applicable (mainly equities). Null for most futures/commodities. */
    @ColumnInfo(name = "isin")
    val isin: String? = null,

    /** Free-form JSON for anything vendor-specific that doesn't warrant its own column. */
    @ColumnInfo(name = "vendorMetadata")
    val vendorMetadata: String? = null,

    @Embedded
    val audit: AuditMetadata,

    @Embedded
    val softDelete: SoftDeleteMetadata = SoftDeleteMetadata.notDeleted()
)

package com.jarvis.tidb.core.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single dated futures/options contract for an [InstrumentEntity]. An instrument can have
 * unlimited contracts over time (e.g. NATGASMINI JUL26, AUG26, SEP26 ...).
 *
 * Revision 1 additions: [uuid]/[audit]/[softDelete], plus the same optional external-provider
 * mapping fields as [InstrumentEntity] (§4) — a contract can have its own broker key/exchange
 * token distinct from its parent instrument's (common for futures, where the broker mints a
 * fresh instrument key per expiry).
 */
@Entity(
    tableName = "contracts",
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
        Index(value = ["uuid"], unique = true),
        Index(value = ["expiryDate"]),
        Index(value = ["instrumentId", "tradingStatus"]),
        Index(value = ["isDeleted"]),
        Index(value = ["brokerInstrumentKey"]),
        Index(value = ["exchangeToken"])
    ]
)
data class ContractEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "contractId")
    val contractId: Long = 0L,

    @ColumnInfo(name = "uuid")
    val uuid: String = GlobalId.new(),

    @ColumnInfo(name = "instrumentId")
    val instrumentId: Long,

    @ColumnInfo(name = "expiryDate")
    val expiryDate: Long,

    @ColumnInfo(name = "rollDate")
    val rollDate: Long,

    @ColumnInfo(name = "contractSize")
    val contractSize: Double,

    @ColumnInfo(name = "marginRequirement")
    val marginRequirement: Double,

    @ColumnInfo(name = "tradingStatus")
    val tradingStatus: ContractTradingStatus = ContractTradingStatus.ACTIVE,

    @ColumnInfo(name = "brokerInstrumentKey")
    val brokerInstrumentKey: String? = null,

    @ColumnInfo(name = "exchangeToken")
    val exchangeToken: String? = null,

    @ColumnInfo(name = "isin")
    val isin: String? = null,

    @ColumnInfo(name = "vendorMetadata")
    val vendorMetadata: String? = null,

    @Embedded
    val audit: AuditMetadata,

    @Embedded
    val softDelete: SoftDeleteMetadata = SoftDeleteMetadata.notDeleted()
)

package com.jarvis.tidb.core.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jarvis.tidb.core.common.AuditMetadata
import com.jarvis.tidb.core.common.GlobalId
import com.jarvis.tidb.core.common.SoftDeleteMetadata

/**
 * MODULE 1 — CORE MARKET FOUNDATION (unchanged functionality, carried into the unified
 * TradingIntelligenceDatabase as of v1.0). Asset-class-agnostic by design so the same tables
 * serve commodities, equities, futures, options, forex, and crypto without schema changes.
 *
 * Every entity carries a globally unique [GlobalId]-generated `uuid`, embedded [AuditMetadata]
 * and [SoftDeleteMetadata], exactly as established in Module 1 Revision 1 (schema v2).
 */

@Entity(
    tableName = "exchanges",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["exchangeCode"], unique = true),
        Index(value = ["isDeleted"])
    ]
)
data class ExchangeEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "exchangeId")
    val exchangeId: Long = 0L,

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
    val audit: AuditMetadata = AuditMetadata(),

    @Embedded
    val softDelete: SoftDeleteMetadata = SoftDeleteMetadata.notDeleted()
)

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
        Index(value = ["uuid"], unique = true),
        Index(value = ["exchangeId"]),
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
    val audit: AuditMetadata = AuditMetadata(),

    @Embedded
    val softDelete: SoftDeleteMetadata = SoftDeleteMetadata.notDeleted()
)

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
    val audit: AuditMetadata = AuditMetadata(),

    @Embedded
    val softDelete: SoftDeleteMetadata = SoftDeleteMetadata.notDeleted()
)

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
    val audit: AuditMetadata = AuditMetadata(),

    @Embedded
    val softDelete: SoftDeleteMetadata = SoftDeleteMetadata.notDeleted()
)

@Entity(
    tableName = "historical_candles",
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
        Index(value = ["instrumentId", "timeframe", "timestamp"], unique = true, name = "idx_candle_instrument_timeframe_timestamp"),
        Index(value = ["timestamp"]),
        Index(value = ["instrumentId"]),
        Index(value = ["timeframe"]),
        Index(value = ["uuid"], unique = true),
        Index(value = ["importBatchId"]),
        Index(value = ["isDeleted"])
    ]
)
data class HistoricalCandleEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "candleId")
    val candleId: Long = 0L,

    @ColumnInfo(name = "uuid")
    val uuid: String = GlobalId.new(),

    @ColumnInfo(name = "instrumentId")
    val instrumentId: Long,

    @ColumnInfo(name = "timeframe")
    val timeframe: Timeframe,

    /** Candle open timestamp, epoch millis UTC. */
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "open")
    val open: Double,

    @ColumnInfo(name = "high")
    val high: Double,

    @ColumnInfo(name = "low")
    val low: Double,

    @ColumnInfo(name = "close")
    val close: Double,

    @ColumnInfo(name = "volume")
    val volume: Long,

    @ColumnInfo(name = "openInterest")
    val openInterest: Long? = null,

    @ColumnInfo(name = "source")
    val source: CandleSource,

    /** 0.0–1.0 confidence score used by downstream modules to weight/filter candles. */
    @ColumnInfo(name = "qualityScore")
    val qualityScore: Double = 1.0,

    /** First-import timestamp for this bar (epoch millis). `audit.updatedAt` tracks any later correction/re-import. */
    @ColumnInfo(name = "importedAt")
    val importedAt: Long = System.currentTimeMillis(),

    /** Which concrete feed/vendor connection produced this row (finer-grained than [source]). */
    @ColumnInfo(name = "sourceId")
    val sourceId: String? = null,

    /** Groups every row inserted by a single import/backfill run, so a bad batch can be rolled back as a unit. */
    @ColumnInfo(name = "importBatchId")
    val importBatchId: String? = null,

    /** Optional hash of the raw upstream payload for this bar, for future dedup/integrity verification. */
    @ColumnInfo(name = "checksum")
    val checksum: String? = null,

    @Embedded
    val audit: AuditMetadata = AuditMetadata(),

    @Embedded
    val softDelete: SoftDeleteMetadata = SoftDeleteMetadata.notDeleted()
)

@Entity(
    tableName = "live_market_snapshots",
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
        Index(value = ["uuid"], unique = true),
        Index(value = ["isDeleted"])
    ]
)
data class LiveMarketSnapshotEntity(
    /** 1:1 with Instrument — instrumentId is both PK and FK. */
    @PrimaryKey
    @ColumnInfo(name = "instrumentId")
    val instrumentId: Long,

    @ColumnInfo(name = "uuid")
    val uuid: String = GlobalId.new(),

    @ColumnInfo(name = "lastPrice")
    val lastPrice: Double,

    @ColumnInfo(name = "bid")
    val bid: Double? = null,

    @ColumnInfo(name = "ask")
    val ask: Double? = null,

    @ColumnInfo(name = "spread")
    val spread: Double? = null,

    @ColumnInfo(name = "volume")
    val volume: Long = 0,

    @ColumnInfo(name = "openInterest")
    val openInterest: Long? = null,

    @ColumnInfo(name = "vwap")
    val vwap: Double? = null,

    @ColumnInfo(name = "dayHigh")
    val dayHigh: Double? = null,

    @ColumnInfo(name = "dayLow")
    val dayLow: Double? = null,

    @ColumnInfo(name = "previousClose")
    val previousClose: Double? = null,

    @ColumnInfo(name = "marketStatus")
    val marketStatus: MarketStatus = MarketStatus.CLOSED,

    @Embedded
    val audit: AuditMetadata = AuditMetadata(),

    @Embedded
    val softDelete: SoftDeleteMetadata = SoftDeleteMetadata.notDeleted()
)

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
        Index(value = ["uuid"], unique = true),
        Index(value = ["instrumentId"]),
        Index(value = ["eventType"]),
        Index(value = ["timestamp"]),
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
    val description: String? = null,

    @ColumnInfo(name = "severity")
    val severity: EventSeverity = EventSeverity.INFO,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "source")
    val source: String? = null,

    /** Free-form JSON payload specific to [eventType]. */
    @ColumnInfo(name = "metadata")
    val metadata: String? = null,

    @Embedded
    val audit: AuditMetadata = AuditMetadata(),

    @Embedded
    val softDelete: SoftDeleteMetadata = SoftDeleteMetadata.notDeleted()
)

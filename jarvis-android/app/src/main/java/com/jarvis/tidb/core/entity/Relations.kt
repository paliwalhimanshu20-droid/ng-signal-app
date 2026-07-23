package com.jarvis.tidb.core.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Composite read models expressing the module's core hierarchy:
 *
 *   Exchange -> Instrument -> Contract -> Historical Candle -> Live Snapshot -> Market Event
 *
 * These are query-time projections only (never persisted) and are what repositories return
 * when a caller needs a fully hydrated object graph instead of raw foreign keys.
 */

data class ExchangeWithInstruments(
    @Embedded val exchange: ExchangeEntity,
    @Relation(
        parentColumn = "exchangeId",
        entityColumn = "exchangeId"
    )
    val instruments: List<InstrumentEntity>
)

data class ExchangeWithSessions(
    @Embedded val exchange: ExchangeEntity,
    @Relation(
        parentColumn = "exchangeId",
        entityColumn = "exchangeId"
    )
    val sessions: List<MarketSessionEntity>
)

data class InstrumentWithContracts(
    @Embedded val instrument: InstrumentEntity,
    @Relation(
        parentColumn = "instrumentId",
        entityColumn = "instrumentId"
    )
    val contracts: List<ContractEntity>
)

data class InstrumentWithLiveSnapshot(
    @Embedded val instrument: InstrumentEntity,
    @Relation(
        parentColumn = "instrumentId",
        entityColumn = "instrumentId"
    )
    val snapshot: LiveMarketSnapshotEntity?
)

data class InstrumentWithEvents(
    @Embedded val instrument: InstrumentEntity,
    @Relation(
        parentColumn = "instrumentId",
        entityColumn = "instrumentId"
    )
    val events: List<MarketEventEntity>
)

/**
 * Full hydration of a single instrument: its active contract(s), latest snapshot, and recent
 * events. Intended for detail screens / strategy bootstrap, not for list rendering (expensive).
 */
data class InstrumentFullDetail(
    @Embedded val instrument: InstrumentEntity,
    @Relation(
        parentColumn = "instrumentId",
        entityColumn = "instrumentId"
    )
    val contracts: List<ContractEntity>,
    @Relation(
        parentColumn = "instrumentId",
        entityColumn = "instrumentId"
    )
    val snapshot: LiveMarketSnapshotEntity?,
    @Relation(
        parentColumn = "instrumentId",
        entityColumn = "instrumentId"
    )
    val recentEvents: List<MarketEventEntity>
)

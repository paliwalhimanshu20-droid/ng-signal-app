package com.jarvis.tidb.core.entity

import androidx.room.Embedded
import androidx.room.Relation

data class InstrumentWithEvents(
    @Embedded val instrument: InstrumentEntity,
    @Relation(parentColumn = "instrumentId", entityColumn = "instrumentId")
    val events: List<MarketEventEntity>
)

/** Full hydration of a single instrument: its contracts, latest snapshot, and recent events. For detail screens, not list rendering. */
data class InstrumentFullDetail(
    @Embedded val instrument: InstrumentEntity,
    @Relation(parentColumn = "instrumentId", entityColumn = "instrumentId")
    val contracts: List<ContractEntity>,
    @Relation(parentColumn = "instrumentId", entityColumn = "instrumentId")
    val snapshot: LiveMarketSnapshotEntity?,
    @Relation(parentColumn = "instrumentId", entityColumn = "instrumentId")
    val recentEvents: List<MarketEventEntity>
)

package com.jarvis.tidb.core.repository

import com.jarvis.tidb.core.dao.MarketEventDao
import com.jarvis.tidb.core.entity.EventSeverity
import com.jarvis.tidb.core.entity.MarketEventEntity
import com.jarvis.tidb.core.entity.MarketEventType
import kotlinx.coroutines.flow.Flow

interface MarketEventRepository {
    suspend fun record(event: MarketEventEntity): Long
    suspend fun recordAll(events: List<MarketEventEntity>): List<Long>
    suspend fun delete(event: MarketEventEntity)
    suspend fun softDelete(eventId: Long, actor: String = "SYSTEM")
    suspend fun getByUuid(uuid: String): MarketEventEntity?
    fun observeByInstrument(instrumentId: Long): Flow<List<MarketEventEntity>>
    fun observeByInstrumentAndType(instrumentId: Long, eventType: MarketEventType): Flow<List<MarketEventEntity>>
    fun observeBySeverity(
        minSeverities: List<EventSeverity> = listOf(EventSeverity.HIGH, EventSeverity.CRITICAL),
        limit: Int = 100
    ): Flow<List<MarketEventEntity>>
    fun observeInRange(instrumentId: Long, fromEpochMillis: Long, toEpochMillis: Long): Flow<List<MarketEventEntity>>
}

class MarketEventRepositoryImpl(
    private val dao: MarketEventDao
) : MarketEventRepository {

    override suspend fun record(event: MarketEventEntity): Long = dao.insert(event)

    override suspend fun recordAll(events: List<MarketEventEntity>): List<Long> = dao.insertAll(events)

    override suspend fun delete(event: MarketEventEntity) = dao.delete(event)

    override suspend fun softDelete(eventId: Long, actor: String) =
        dao.softDelete(eventId, System.currentTimeMillis(), actor)

    override suspend fun getByUuid(uuid: String): MarketEventEntity? = dao.getByUuid(uuid)

    override fun observeByInstrument(instrumentId: Long): Flow<List<MarketEventEntity>> =
        dao.observeByInstrument(instrumentId)

    override fun observeByInstrumentAndType(
        instrumentId: Long,
        eventType: MarketEventType
    ): Flow<List<MarketEventEntity>> = dao.observeByInstrumentAndType(instrumentId, eventType)

    override fun observeBySeverity(
        minSeverities: List<EventSeverity>,
        limit: Int
    ): Flow<List<MarketEventEntity>> = dao.observeBySeverity(minSeverities, limit)

    override fun observeInRange(
        instrumentId: Long,
        fromEpochMillis: Long,
        toEpochMillis: Long
    ): Flow<List<MarketEventEntity>> = dao.observeInRange(instrumentId, fromEpochMillis, toEpochMillis)
}

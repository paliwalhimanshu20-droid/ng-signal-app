package com.jarvis.tidb.core.repository

import com.jarvis.tidb.core.dao.MarketSessionDao
import com.jarvis.tidb.core.entity.MarketSessionEntity
import kotlinx.coroutines.flow.Flow

interface MarketSessionRepository {
    suspend fun upsert(session: MarketSessionEntity): Long
    suspend fun upsertAll(sessions: List<MarketSessionEntity>): List<Long>
    suspend fun delete(session: MarketSessionEntity)
    suspend fun softDelete(sessionId: Long, actor: String = "SYSTEM")
    fun observeById(sessionId: Long): Flow<MarketSessionEntity?>
    suspend fun getByUuid(uuid: String): MarketSessionEntity?
    fun observeByExchange(exchangeId: Long): Flow<List<MarketSessionEntity>>
    fun observeHolidays(exchangeId: Long): Flow<List<MarketSessionEntity>>
}

class MarketSessionRepositoryImpl(
    private val dao: MarketSessionDao
) : MarketSessionRepository {

    override suspend fun upsert(session: MarketSessionEntity): Long =
        if (session.sessionId == 0L) dao.insert(session) else {
            dao.update(session); session.sessionId
        }

    override suspend fun upsertAll(sessions: List<MarketSessionEntity>): List<Long> = dao.insertAll(sessions)

    override suspend fun delete(session: MarketSessionEntity) = dao.delete(session)

    override suspend fun softDelete(sessionId: Long, actor: String) =
        dao.softDelete(sessionId, System.currentTimeMillis(), actor)

    override fun observeById(sessionId: Long): Flow<MarketSessionEntity?> = dao.observeById(sessionId)

    override suspend fun getByUuid(uuid: String): MarketSessionEntity? = dao.getByUuid(uuid)

    override fun observeByExchange(exchangeId: Long): Flow<List<MarketSessionEntity>> =
        dao.observeByExchange(exchangeId)

    override fun observeHolidays(exchangeId: Long): Flow<List<MarketSessionEntity>> =
        dao.observeHolidays(exchangeId)
}

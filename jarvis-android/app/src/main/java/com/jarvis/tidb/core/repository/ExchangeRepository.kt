package com.jarvis.tidb.core.repository

import com.jarvis.tidb.core.dao.ExchangeDao
import com.jarvis.tidb.core.entity.ExchangeEntity
import com.jarvis.tidb.core.entity.ExchangeWithInstruments
import com.jarvis.tidb.core.entity.ExchangeWithSessions
import com.jarvis.tidb.core.entity.RecordStatus
import kotlinx.coroutines.flow.Flow

/**
 * Repository Interface -> Repository Implementation, per Revision 1 §7: consumers depend on
 * this interface only, so a future cloud-backed implementation can be swapped in without
 * touching any calling code.
 *
 * All reads here exclude soft-deleted rows by default (Revision 1 §3). [delete] remains for
 * backward compatibility / admin use; prefer [softDelete] in application code.
 */
interface ExchangeRepository {
    suspend fun upsert(exchange: ExchangeEntity): Long
    suspend fun upsertAll(exchanges: List<ExchangeEntity>): List<Long>
    suspend fun delete(exchange: ExchangeEntity)
    suspend fun softDelete(exchangeId: Long, actor: String = "SYSTEM")
    fun observeById(exchangeId: Long): Flow<ExchangeEntity?>
    suspend fun getByUuid(uuid: String): ExchangeEntity?
    suspend fun getByCode(exchangeCode: String): ExchangeEntity?
    fun observeActive(): Flow<List<ExchangeEntity>>
    fun observeAll(): Flow<List<ExchangeEntity>>
    fun observeWithInstruments(exchangeId: Long): Flow<ExchangeWithInstruments?>
    fun observeWithSessions(exchangeId: Long): Flow<ExchangeWithSessions?>
}

class ExchangeRepositoryImpl(
    private val dao: ExchangeDao
) : ExchangeRepository {

    override suspend fun upsert(exchange: ExchangeEntity): Long =
        if (exchange.exchangeId == 0L) dao.insert(exchange) else {
            dao.update(exchange); exchange.exchangeId
        }

    override suspend fun upsertAll(exchanges: List<ExchangeEntity>): List<Long> =
        dao.insertAll(exchanges)

    override suspend fun delete(exchange: ExchangeEntity) = dao.delete(exchange)

    override suspend fun softDelete(exchangeId: Long, actor: String) =
        dao.softDelete(exchangeId, System.currentTimeMillis(), actor)

    override fun observeById(exchangeId: Long): Flow<ExchangeEntity?> = dao.observeById(exchangeId)

    override suspend fun getByUuid(uuid: String): ExchangeEntity? = dao.getByUuid(uuid)

    override suspend fun getByCode(exchangeCode: String): ExchangeEntity? = dao.getByCode(exchangeCode)

    override fun observeActive(): Flow<List<ExchangeEntity>> = dao.observeByStatus(RecordStatus.ACTIVE)

    override fun observeAll(): Flow<List<ExchangeEntity>> = dao.observeAll()

    override fun observeWithInstruments(exchangeId: Long): Flow<ExchangeWithInstruments?> =
        dao.observeWithInstruments(exchangeId)

    override fun observeWithSessions(exchangeId: Long): Flow<ExchangeWithSessions?> =
        dao.observeWithSessions(exchangeId)
}

package com.jarvis.tidb.core.repository

import com.jarvis.tidb.core.dao.InstrumentDao
import com.jarvis.tidb.core.entity.AssetClass
import com.jarvis.tidb.core.entity.InstrumentEntity
import com.jarvis.tidb.core.entity.InstrumentFullDetail
import com.jarvis.tidb.core.entity.InstrumentWithContracts
import com.jarvis.tidb.core.entity.InstrumentWithLiveSnapshot
import com.jarvis.tidb.core.entity.RecordStatus
import kotlinx.coroutines.flow.Flow

interface InstrumentRepository {
    suspend fun upsert(instrument: InstrumentEntity): Long
    suspend fun upsertAll(instruments: List<InstrumentEntity>): List<Long>
    suspend fun delete(instrument: InstrumentEntity)
    suspend fun softDelete(instrumentId: Long, actor: String = "SYSTEM")
    fun observeById(instrumentId: Long): Flow<InstrumentEntity?>
    suspend fun getByUuid(uuid: String): InstrumentEntity?
    suspend fun getBySymbol(symbol: String): InstrumentEntity?
    suspend fun getByBrokerInstrumentKey(key: String): InstrumentEntity?
    fun observeByExchange(exchangeId: Long): Flow<List<InstrumentEntity>>
    fun observeByAssetClass(assetClass: AssetClass): Flow<List<InstrumentEntity>>
    fun observeActive(): Flow<List<InstrumentEntity>>
    fun observeAll(): Flow<List<InstrumentEntity>>
    fun observeWithContracts(instrumentId: Long): Flow<InstrumentWithContracts?>
    fun observeWithLiveSnapshot(instrumentId: Long): Flow<InstrumentWithLiveSnapshot?>
    fun observeFullDetail(instrumentId: Long): Flow<InstrumentFullDetail?>
}

class InstrumentRepositoryImpl(
    private val dao: InstrumentDao
) : InstrumentRepository {

    override suspend fun upsert(instrument: InstrumentEntity): Long =
        if (instrument.instrumentId == 0L) dao.insert(instrument) else {
            dao.update(instrument); instrument.instrumentId
        }

    override suspend fun upsertAll(instruments: List<InstrumentEntity>): List<Long> =
        dao.insertAll(instruments)

    override suspend fun delete(instrument: InstrumentEntity) = dao.delete(instrument)

    override suspend fun softDelete(instrumentId: Long, actor: String) =
        dao.softDelete(instrumentId, System.currentTimeMillis(), actor)

    override fun observeById(instrumentId: Long): Flow<InstrumentEntity?> = dao.observeById(instrumentId)

    override suspend fun getByUuid(uuid: String): InstrumentEntity? = dao.getByUuid(uuid)

    override suspend fun getBySymbol(symbol: String): InstrumentEntity? = dao.getBySymbol(symbol)

    override suspend fun getByBrokerInstrumentKey(key: String): InstrumentEntity? =
        dao.getByBrokerInstrumentKey(key)

    override fun observeByExchange(exchangeId: Long): Flow<List<InstrumentEntity>> =
        dao.observeByExchange(exchangeId)

    override fun observeByAssetClass(assetClass: AssetClass): Flow<List<InstrumentEntity>> =
        dao.observeByAssetClass(assetClass)

    override fun observeActive(): Flow<List<InstrumentEntity>> = dao.observeByStatus(RecordStatus.ACTIVE)

    override fun observeAll(): Flow<List<InstrumentEntity>> = dao.observeAll()

    override fun observeWithContracts(instrumentId: Long): Flow<InstrumentWithContracts?> =
        dao.observeWithContracts(instrumentId)

    override fun observeWithLiveSnapshot(instrumentId: Long): Flow<InstrumentWithLiveSnapshot?> =
        dao.observeWithLiveSnapshot(instrumentId)

    override fun observeFullDetail(instrumentId: Long): Flow<InstrumentFullDetail?> =
        dao.observeFullDetail(instrumentId)
}

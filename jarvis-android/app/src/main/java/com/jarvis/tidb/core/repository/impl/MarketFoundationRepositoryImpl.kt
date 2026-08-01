package com.jarvis.tidb.core.repository.impl

import com.jarvis.tidb.core.dao.ContractDao
import com.jarvis.tidb.core.dao.ExchangeDao
import com.jarvis.tidb.core.dao.HistoricalCandleDao
import com.jarvis.tidb.core.dao.InstrumentDao
import com.jarvis.tidb.core.dao.LiveMarketSnapshotDao
import com.jarvis.tidb.core.dao.MarketEventDao
import com.jarvis.tidb.core.dao.MarketSessionDao
import com.jarvis.tidb.core.entity.ContractEntity
import com.jarvis.tidb.core.entity.ContractTradingStatus
import com.jarvis.tidb.core.entity.ExchangeEntity
import com.jarvis.tidb.core.entity.HistoricalCandleEntity
import com.jarvis.tidb.core.entity.InstrumentEntity
import com.jarvis.tidb.core.entity.InstrumentFullDetail
import com.jarvis.tidb.core.entity.InstrumentWithEvents
import com.jarvis.tidb.core.entity.LiveMarketSnapshotEntity
import com.jarvis.tidb.core.entity.MarketEventEntity
import com.jarvis.tidb.core.entity.MarketSessionEntity
import com.jarvis.tidb.core.entity.Timeframe
import com.jarvis.tidb.core.repository.ContractRepository
import com.jarvis.tidb.core.repository.ExchangeRepository
import com.jarvis.tidb.core.repository.HistoricalCandleRepository
import com.jarvis.tidb.core.repository.InstrumentRepository
import com.jarvis.tidb.core.repository.LiveMarketSnapshotRepository
import com.jarvis.tidb.core.repository.MarketEventRepository
import com.jarvis.tidb.core.repository.MarketSessionRepository
import kotlinx.coroutines.flow.Flow

class ExchangeRepositoryImpl(private val dao: ExchangeDao) : ExchangeRepository {
    override suspend fun upsert(exchange: ExchangeEntity): Long =
        if (exchange.exchangeId == 0L) dao.insert(exchange) else { dao.update(exchange); exchange.exchangeId }
    override suspend fun getById(exchangeId: Long): ExchangeEntity? = dao.findById(exchangeId)
    override suspend fun getByUuid(uuid: String): ExchangeEntity? = dao.findByUuid(uuid)
    override fun observeAll(): Flow<List<ExchangeEntity>> = dao.observeAll()
    override suspend fun softDelete(exchangeId: Long) = dao.softDelete(exchangeId)
}

class MarketSessionRepositoryImpl(private val dao: MarketSessionDao) : MarketSessionRepository {
    override suspend fun upsert(session: MarketSessionEntity): Long =
        if (session.sessionId == 0L) dao.insert(session) else { dao.update(session); session.sessionId }
    override suspend fun upsertAll(sessions: List<MarketSessionEntity>): List<Long> = dao.insertAll(sessions)
    override suspend fun getById(sessionId: Long): MarketSessionEntity? = dao.findById(sessionId)
    override fun observeByExchange(exchangeId: Long): Flow<List<MarketSessionEntity>> = dao.observeByExchange(exchangeId)
    override suspend fun softDelete(sessionId: Long) = dao.softDelete(sessionId)
}

class InstrumentRepositoryImpl(private val dao: InstrumentDao) : InstrumentRepository {
    override suspend fun upsert(instrument: InstrumentEntity): Long =
        if (instrument.instrumentId == 0L) dao.insert(instrument) else { dao.update(instrument); instrument.instrumentId }
    override suspend fun getById(instrumentId: Long): InstrumentEntity? = dao.findById(instrumentId)
    override suspend fun getByUuid(uuid: String): InstrumentEntity? = dao.findByUuid(uuid)
    override suspend fun getBySymbol(symbol: String): InstrumentEntity? = dao.findBySymbol(symbol)
    override suspend fun exists(instrumentId: Long): Boolean = dao.exists(instrumentId)
    override fun observeAll(): Flow<List<InstrumentEntity>> = dao.observeAll()
    override fun observeByExchange(exchangeId: Long): Flow<List<InstrumentEntity>> = dao.observeByExchange(exchangeId)
    override fun observeByAssetClass(assetClass: String): Flow<List<InstrumentEntity>> = dao.observeByAssetClass(assetClass)
    override suspend fun getWithEvents(instrumentId: Long): InstrumentWithEvents? = dao.getWithEvents(instrumentId)
    override suspend fun getFullDetail(instrumentId: Long): InstrumentFullDetail? = dao.getFullDetail(instrumentId)
    override suspend fun softDelete(instrumentId: Long) = dao.softDelete(instrumentId)
}

class ContractRepositoryImpl(private val dao: ContractDao) : ContractRepository {
    override suspend fun upsert(contract: ContractEntity): Long =
        if (contract.contractId == 0L) dao.insert(contract) else { dao.update(contract); contract.contractId }
    override suspend fun upsertAll(contracts: List<ContractEntity>): List<Long> = dao.insertAll(contracts)
    override suspend fun getById(contractId: Long): ContractEntity? = dao.findById(contractId)
    override fun observeByInstrument(instrumentId: Long): Flow<List<ContractEntity>> = dao.observeByInstrument(instrumentId)
    override fun observeByInstrumentAndStatus(instrumentId: Long, status: ContractTradingStatus): Flow<List<ContractEntity>> =
        dao.observeByInstrumentAndStatus(instrumentId, status)
    override suspend fun getNearestActiveContract(instrumentId: Long): ContractEntity? = dao.getNearestActiveContract(instrumentId)
    override fun observeExpiringBetween(fromEpochMillis: Long, toEpochMillis: Long): Flow<List<ContractEntity>> =
        dao.observeExpiringBetween(fromEpochMillis, toEpochMillis)
    override suspend fun softDelete(contractId: Long) = dao.softDelete(contractId)
}

class HistoricalCandleRepositoryImpl(private val dao: HistoricalCandleDao) : HistoricalCandleRepository {
    override suspend fun insert(candle: HistoricalCandleEntity): Long = dao.insert(candle)
    override suspend fun insertAll(candles: List<HistoricalCandleEntity>): List<Long> = dao.insertAll(candles)
    override fun observeRange(instrumentId: Long, timeframe: Timeframe, fromMillis: Long, toMillis: Long): Flow<List<HistoricalCandleEntity>> =
        dao.observeRange(instrumentId, timeframe, fromMillis, toMillis)
    override suspend fun getLatest(instrumentId: Long, timeframe: Timeframe, limit: Int): List<HistoricalCandleEntity> =
        dao.getLatest(instrumentId, timeframe, limit)
    override suspend fun softDeleteByImportBatch(importBatchId: String) = dao.softDeleteByImportBatch(importBatchId)
}

class LiveMarketSnapshotRepositoryImpl(private val dao: LiveMarketSnapshotDao) : LiveMarketSnapshotRepository {
    override suspend fun upsert(snapshot: LiveMarketSnapshotEntity): Long = dao.upsert(snapshot)
    override fun observeByInstrument(instrumentId: Long): Flow<LiveMarketSnapshotEntity?> = dao.observeByInstrument(instrumentId)
}

class MarketEventRepositoryImpl(private val dao: MarketEventDao) : MarketEventRepository {
    override suspend fun recordEvent(event: MarketEventEntity): Long = dao.insert(event)
    override fun observeByInstrument(instrumentId: Long): Flow<List<MarketEventEntity>> = dao.observeByInstrument(instrumentId)
    override fun observeByType(eventType: String): Flow<List<MarketEventEntity>> = dao.observeByType(eventType)
}

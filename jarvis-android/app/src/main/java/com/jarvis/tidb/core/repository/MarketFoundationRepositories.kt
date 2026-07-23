package com.jarvis.tidb.core.repository

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
import kotlinx.coroutines.flow.Flow

interface ExchangeRepository {
    suspend fun upsert(exchange: ExchangeEntity): Long
    suspend fun getById(exchangeId: Long): ExchangeEntity?
    suspend fun getByUuid(uuid: String): ExchangeEntity?
    fun observeAll(): Flow<List<ExchangeEntity>>
    suspend fun softDelete(exchangeId: Long)
}

interface MarketSessionRepository {
    suspend fun upsert(session: MarketSessionEntity): Long
    suspend fun upsertAll(sessions: List<MarketSessionEntity>): List<Long>
    suspend fun getById(sessionId: Long): MarketSessionEntity?
    fun observeByExchange(exchangeId: Long): Flow<List<MarketSessionEntity>>
    suspend fun softDelete(sessionId: Long)
}

/** `exists()` is the entry point every other module (Signals, Analytics) calls before persisting a logical FK against `instrumentId`. */
interface InstrumentRepository {
    suspend fun upsert(instrument: InstrumentEntity): Long
    suspend fun getById(instrumentId: Long): InstrumentEntity?
    suspend fun getByUuid(uuid: String): InstrumentEntity?
    suspend fun getBySymbol(symbol: String): InstrumentEntity?
    suspend fun exists(instrumentId: Long): Boolean
    fun observeAll(): Flow<List<InstrumentEntity>>
    fun observeByExchange(exchangeId: Long): Flow<List<InstrumentEntity>>
    fun observeByAssetClass(assetClass: String): Flow<List<InstrumentEntity>>
    suspend fun getWithEvents(instrumentId: Long): InstrumentWithEvents?
    suspend fun getFullDetail(instrumentId: Long): InstrumentFullDetail?
    suspend fun softDelete(instrumentId: Long)
}

interface ContractRepository {
    suspend fun upsert(contract: ContractEntity): Long
    suspend fun upsertAll(contracts: List<ContractEntity>): List<Long>
    suspend fun getById(contractId: Long): ContractEntity?
    fun observeByInstrument(instrumentId: Long): Flow<List<ContractEntity>>
    fun observeByInstrumentAndStatus(instrumentId: Long, status: ContractTradingStatus = ContractTradingStatus.ACTIVE): Flow<List<ContractEntity>>
    suspend fun getNearestActiveContract(instrumentId: Long): ContractEntity?
    fun observeExpiringBetween(fromEpochMillis: Long, toEpochMillis: Long): Flow<List<ContractEntity>>
    suspend fun softDelete(contractId: Long)
}

interface HistoricalCandleRepository {
    suspend fun insert(candle: HistoricalCandleEntity): Long
    suspend fun insertAll(candles: List<HistoricalCandleEntity>): List<Long>
    fun observeRange(instrumentId: Long, timeframe: Timeframe, fromMillis: Long, toMillis: Long): Flow<List<HistoricalCandleEntity>>
    suspend fun getLatest(instrumentId: Long, timeframe: Timeframe, limit: Int = 1): List<HistoricalCandleEntity>
    suspend fun softDeleteByImportBatch(importBatchId: String)
}

interface LiveMarketSnapshotRepository {
    suspend fun upsert(snapshot: LiveMarketSnapshotEntity): Long
    fun observeByInstrument(instrumentId: Long): Flow<LiveMarketSnapshotEntity?>
}

interface MarketEventRepository {
    suspend fun recordEvent(event: MarketEventEntity): Long
    fun observeByInstrument(instrumentId: Long): Flow<List<MarketEventEntity>>
    fun observeByType(eventType: String): Flow<List<MarketEventEntity>>
}

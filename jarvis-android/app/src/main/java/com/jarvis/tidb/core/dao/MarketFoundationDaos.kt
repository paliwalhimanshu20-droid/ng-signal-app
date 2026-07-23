package com.jarvis.tidb.core.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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

@Dao
interface ExchangeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(exchange: ExchangeEntity): Long
    @Update
    suspend fun update(exchange: ExchangeEntity)
    @Query("SELECT * FROM exchanges WHERE exchangeId = :exchangeId")
    suspend fun findById(exchangeId: Long): ExchangeEntity?
    @Query("SELECT * FROM exchanges WHERE uuid = :uuid")
    suspend fun findByUuid(uuid: String): ExchangeEntity?
    @Query("SELECT * FROM exchanges WHERE isDeleted = 0 ORDER BY exchangeName ASC")
    fun observeAll(): Flow<List<ExchangeEntity>>
    @Query("UPDATE exchanges SET isDeleted = 1, deletedAt = :deletedAt WHERE exchangeId = :exchangeId")
    suspend fun softDelete(exchangeId: Long, deletedAt: Long = System.currentTimeMillis())
}

@Dao
interface MarketSessionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: MarketSessionEntity): Long
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(sessions: List<MarketSessionEntity>): List<Long>
    @Update
    suspend fun update(session: MarketSessionEntity)
    @Query("SELECT * FROM market_sessions WHERE sessionId = :sessionId")
    suspend fun findById(sessionId: Long): MarketSessionEntity?
    @Query("SELECT * FROM market_sessions WHERE uuid = :uuid")
    suspend fun findByUuid(uuid: String): MarketSessionEntity?
    @Query("SELECT * FROM market_sessions WHERE exchangeId = :exchangeId AND isDeleted = 0")
    fun observeByExchange(exchangeId: Long): Flow<List<MarketSessionEntity>>
    @Query("UPDATE market_sessions SET isDeleted = 1, deletedAt = :deletedAt WHERE sessionId = :sessionId")
    suspend fun softDelete(sessionId: Long, deletedAt: Long = System.currentTimeMillis())
}

@Dao
interface InstrumentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(instrument: InstrumentEntity): Long
    @Update
    suspend fun update(instrument: InstrumentEntity)
    @Query("SELECT * FROM instruments WHERE instrumentId = :instrumentId")
    suspend fun findById(instrumentId: Long): InstrumentEntity?
    @Query("SELECT * FROM instruments WHERE uuid = :uuid")
    suspend fun findByUuid(uuid: String): InstrumentEntity?
    @Query("SELECT * FROM instruments WHERE symbol = :symbol")
    suspend fun findBySymbol(symbol: String): InstrumentEntity?
    @Query("SELECT EXISTS(SELECT 1 FROM instruments WHERE instrumentId = :instrumentId AND isDeleted = 0)")
    suspend fun exists(instrumentId: Long): Boolean
    @Query("SELECT * FROM instruments WHERE isDeleted = 0 ORDER BY symbol ASC")
    fun observeAll(): Flow<List<InstrumentEntity>>
    @Query("SELECT * FROM instruments WHERE exchangeId = :exchangeId AND isDeleted = 0")
    fun observeByExchange(exchangeId: Long): Flow<List<InstrumentEntity>>
    @Query("SELECT * FROM instruments WHERE assetClass = :assetClass AND isDeleted = 0")
    fun observeByAssetClass(assetClass: String): Flow<List<InstrumentEntity>>
    @Transaction
    @Query("SELECT * FROM instruments WHERE instrumentId = :instrumentId")
    suspend fun getWithEvents(instrumentId: Long): InstrumentWithEvents?
    @Transaction
    @Query("SELECT * FROM instruments WHERE instrumentId = :instrumentId")
    suspend fun getFullDetail(instrumentId: Long): InstrumentFullDetail?
    @Query("UPDATE instruments SET isDeleted = 1, deletedAt = :deletedAt WHERE instrumentId = :instrumentId")
    suspend fun softDelete(instrumentId: Long, deletedAt: Long = System.currentTimeMillis())
}

@Dao
interface ContractDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(contract: ContractEntity): Long
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(contracts: List<ContractEntity>): List<Long>
    @Update
    suspend fun update(contract: ContractEntity)
    @Query("SELECT * FROM contracts WHERE contractId = :contractId")
    suspend fun findById(contractId: Long): ContractEntity?
    @Query("SELECT * FROM contracts WHERE uuid = :uuid")
    suspend fun findByUuid(uuid: String): ContractEntity?
    @Query("SELECT * FROM contracts WHERE instrumentId = :instrumentId AND isDeleted = 0")
    fun observeByInstrument(instrumentId: Long): Flow<List<ContractEntity>>
    @Query("SELECT * FROM contracts WHERE instrumentId = :instrumentId AND tradingStatus = :status AND isDeleted = 0")
    fun observeByInstrumentAndStatus(instrumentId: Long, status: ContractTradingStatus = ContractTradingStatus.ACTIVE): Flow<List<ContractEntity>>
    @Query("SELECT * FROM contracts WHERE instrumentId = :instrumentId AND tradingStatus = 'ACTIVE' AND isDeleted = 0 ORDER BY expiryDate ASC LIMIT 1")
    suspend fun getNearestActiveContract(instrumentId: Long): ContractEntity?
    @Query("SELECT * FROM contracts WHERE expiryDate BETWEEN :fromEpochMillis AND :toEpochMillis AND isDeleted = 0")
    fun observeExpiringBetween(fromEpochMillis: Long, toEpochMillis: Long): Flow<List<ContractEntity>>
    @Query("UPDATE contracts SET isDeleted = 1, deletedAt = :deletedAt WHERE contractId = :contractId")
    suspend fun softDelete(contractId: Long, deletedAt: Long = System.currentTimeMillis())
}

@Dao
interface HistoricalCandleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(candle: HistoricalCandleEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(candles: List<HistoricalCandleEntity>): List<Long>
    @Query("SELECT * FROM historical_candles WHERE instrumentId = :instrumentId AND timeframe = :timeframe AND timestamp BETWEEN :fromMillis AND :toMillis AND isDeleted = 0 ORDER BY timestamp ASC")
    fun observeRange(instrumentId: Long, timeframe: Timeframe, fromMillis: Long, toMillis: Long): Flow<List<HistoricalCandleEntity>>
    @Query("SELECT * FROM historical_candles WHERE instrumentId = :instrumentId AND timeframe = :timeframe AND isDeleted = 0 ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLatest(instrumentId: Long, timeframe: Timeframe, limit: Int = 1): List<HistoricalCandleEntity>
    @Query("UPDATE historical_candles SET isDeleted = 1, deletedAt = :deletedAt WHERE importBatchId = :importBatchId")
    suspend fun softDeleteByImportBatch(importBatchId: String, deletedAt: Long = System.currentTimeMillis())
}

@Dao
interface LiveMarketSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: LiveMarketSnapshotEntity): Long
    @Query("SELECT * FROM live_market_snapshots WHERE instrumentId = :instrumentId AND isDeleted = 0")
    fun observeByInstrument(instrumentId: Long): Flow<LiveMarketSnapshotEntity?>
}

@Dao
interface MarketEventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: MarketEventEntity): Long
    @Query("SELECT * FROM market_events WHERE instrumentId = :instrumentId AND isDeleted = 0 ORDER BY timestamp DESC")
    fun observeByInstrument(instrumentId: Long): Flow<List<MarketEventEntity>>
    @Query("SELECT * FROM market_events WHERE eventType = :eventType AND isDeleted = 0 ORDER BY timestamp DESC")
    fun observeByType(eventType: String): Flow<List<MarketEventEntity>>
}

package com.jarvis.tidb.core.repository

import com.jarvis.tidb.core.dao.HistoricalCandleDao
import com.jarvis.tidb.core.entity.HistoricalCandleEntity
import com.jarvis.tidb.core.entity.Timeframe
import kotlinx.coroutines.flow.Flow

interface HistoricalCandleRepository {
    suspend fun upsert(candle: HistoricalCandleEntity): Long
    suspend fun upsertAll(candles: List<HistoricalCandleEntity>): List<Long>
    suspend fun deleteAllForInstrumentTimeframe(instrumentId: Long, timeframe: Timeframe)
    suspend fun softDelete(candleId: Long, actor: String = "SYSTEM")
    suspend fun softDeleteByImportBatch(importBatchId: String, actor: String = "SYSTEM")
    suspend fun getByUuid(uuid: String): HistoricalCandleEntity?
    fun observeRange(
        instrumentId: Long,
        timeframe: Timeframe,
        fromEpochMillis: Long,
        toEpochMillis: Long
    ): Flow<List<HistoricalCandleEntity>>
    suspend fun getLatest(instrumentId: Long, timeframe: Timeframe, limit: Int): List<HistoricalCandleEntity>
    suspend fun getLatestSingle(instrumentId: Long, timeframe: Timeframe): HistoricalCandleEntity?
    suspend fun countForInstrumentTimeframe(instrumentId: Long, timeframe: Timeframe): Int
}

class HistoricalCandleRepositoryImpl(
    private val dao: HistoricalCandleDao
) : HistoricalCandleRepository {

    override suspend fun upsert(candle: HistoricalCandleEntity): Long = dao.insert(candle)

    override suspend fun upsertAll(candles: List<HistoricalCandleEntity>): List<Long> = dao.insertAll(candles)

    override suspend fun deleteAllForInstrumentTimeframe(instrumentId: Long, timeframe: Timeframe) =
        dao.deleteAllForInstrumentTimeframe(instrumentId, timeframe)

    override suspend fun softDelete(candleId: Long, actor: String) =
        dao.softDelete(candleId, System.currentTimeMillis(), actor)

    override suspend fun softDeleteByImportBatch(importBatchId: String, actor: String) =
        dao.softDeleteByImportBatch(importBatchId, System.currentTimeMillis(), actor)

    override suspend fun getByUuid(uuid: String): HistoricalCandleEntity? = dao.getByUuid(uuid)

    override fun observeRange(
        instrumentId: Long,
        timeframe: Timeframe,
        fromEpochMillis: Long,
        toEpochMillis: Long
    ): Flow<List<HistoricalCandleEntity>> =
        dao.observeRange(instrumentId, timeframe, fromEpochMillis, toEpochMillis)

    override suspend fun getLatest(
        instrumentId: Long,
        timeframe: Timeframe,
        limit: Int
    ): List<HistoricalCandleEntity> = dao.getLatest(instrumentId, timeframe, limit)

    override suspend fun getLatestSingle(instrumentId: Long, timeframe: Timeframe): HistoricalCandleEntity? =
        dao.getLatestSingle(instrumentId, timeframe)

    override suspend fun countForInstrumentTimeframe(instrumentId: Long, timeframe: Timeframe): Int =
        dao.countForInstrumentTimeframe(instrumentId, timeframe)
}

package com.jarvis.tidb.core.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jarvis.tidb.core.entity.HistoricalCandleEntity
import com.jarvis.tidb.core.entity.Timeframe
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoricalCandleDao {

    /**
     * REPLACE is intentional: re-importing/backfilling a candle for the same
     * (instrumentId, timeframe, timestamp) should overwrite, not duplicate, relying on the
     * unique composite index defined on [HistoricalCandleEntity].
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(candle: HistoricalCandleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(candles: List<HistoricalCandleEntity>): List<Long>

    /** Physical delete, preserved for admin/test use — prefer [softDelete] (Revision 1 §3). */
    @Delete
    suspend fun delete(candle: HistoricalCandleEntity)

    @Query(
        """
        UPDATE historical_candles
        SET isDeleted = 1, deletedAt = :now, updatedAt = :now, updatedBy = :actor, version = version + 1
        WHERE candleId = :candleId
        """
    )
    suspend fun softDelete(candleId: Long, now: Long, actor: String = "SYSTEM")

    @Query(
        """
        DELETE FROM historical_candles
        WHERE instrumentId = :instrumentId AND timeframe = :timeframe
        """
    )
    suspend fun deleteAllForInstrumentTimeframe(instrumentId: Long, timeframe: Timeframe)

    /**
     * Soft-deletes every row from a specific import batch — the intended cleanup path for a
     * bad backfill run (Revision 1 §5) instead of a hard delete.
     */
    @Query(
        """
        UPDATE historical_candles
        SET isDeleted = 1, deletedAt = :now, updatedAt = :now, updatedBy = :actor, version = version + 1
        WHERE importBatchId = :importBatchId
        """
    )
    suspend fun softDeleteByImportBatch(importBatchId: String, now: Long, actor: String = "SYSTEM")

    @Query("SELECT * FROM historical_candles WHERE uuid = :uuid AND isDeleted = 0 LIMIT 1")
    suspend fun getByUuid(uuid: String): HistoricalCandleEntity?

    /** Primary charting query: an ordered range scan hitting the composite index directly. */
    @Query(
        """
        SELECT * FROM historical_candles
        WHERE instrumentId = :instrumentId AND timeframe = :timeframe
          AND timestamp BETWEEN :fromEpochMillis AND :toEpochMillis
          AND isDeleted = 0
        ORDER BY timestamp ASC
        """
    )
    fun observeRange(
        instrumentId: Long,
        timeframe: Timeframe,
        fromEpochMillis: Long,
        toEpochMillis: Long
    ): Flow<List<HistoricalCandleEntity>>

    /** Most recent N candles, for "last 200 bars" style chart bootstrapping. */
    @Query(
        """
        SELECT * FROM historical_candles
        WHERE instrumentId = :instrumentId AND timeframe = :timeframe AND isDeleted = 0
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    suspend fun getLatest(
        instrumentId: Long,
        timeframe: Timeframe,
        limit: Int
    ): List<HistoricalCandleEntity>

    @Query(
        """
        SELECT * FROM historical_candles
        WHERE instrumentId = :instrumentId AND timeframe = :timeframe AND isDeleted = 0
        ORDER BY timestamp DESC LIMIT 1
        """
    )
    suspend fun getLatestSingle(instrumentId: Long, timeframe: Timeframe): HistoricalCandleEntity?

    @Query(
        """
        SELECT COUNT(*) FROM historical_candles
        WHERE instrumentId = :instrumentId AND timeframe = :timeframe AND isDeleted = 0
        """
    )
    suspend fun countForInstrumentTimeframe(instrumentId: Long, timeframe: Timeframe): Int

    @Query("SELECT COUNT(*) FROM historical_candles WHERE isDeleted = 0")
    suspend fun count(): Long
}

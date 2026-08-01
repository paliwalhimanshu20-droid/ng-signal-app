package com.jarvis.tidb.historical.candle.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jarvis.tidb.historical.candle.entity.CandleGapEntity
import com.jarvis.tidb.historical.candle.entity.CandleVersionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CandleVersionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(version: CandleVersionEntity): Long

    @Query("SELECT * FROM candle_versions WHERE candleId = :candleId ORDER BY versionNumber ASC")
    fun observeHistory(candleId: Long): Flow<List<CandleVersionEntity>>

    @Query("SELECT COALESCE(MAX(versionNumber), 0) FROM candle_versions WHERE candleId = :candleId")
    suspend fun latestVersionNumber(candleId: Long): Int
}

@Dao
interface CandleGapDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(gap: CandleGapEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(gaps: List<CandleGapEntity>): List<Long>

    @Update
    suspend fun update(gap: CandleGapEntity)

    @Query("SELECT * FROM candle_gaps WHERE gapId = :gapId")
    suspend fun findById(gapId: Long): CandleGapEntity?

    @Query(
        "SELECT * FROM candle_gaps WHERE instrumentId = :instrumentId AND timeframe = :timeframe AND status != 'IGNORED' ORDER BY gapStart ASC"
    )
    fun observeByInstrumentTimeframe(instrumentId: Long, timeframe: String): Flow<List<CandleGapEntity>>

    @Query("SELECT * FROM candle_gaps WHERE status = 'DETECTED' ORDER BY detectedAt ASC")
    fun observeUnresolved(): Flow<List<CandleGapEntity>>

    @Query("SELECT COUNT(*) FROM candle_gaps WHERE status = 'DETECTED'")
    fun observeUnresolvedCount(): Flow<Int>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM candle_gaps
            WHERE instrumentId = :instrumentId AND timeframe = :timeframe
              AND gapStart = :gapStart AND gapEnd = :gapEnd
        )
        """
    )
    suspend fun exists(instrumentId: Long, timeframe: String, gapStart: Long, gapEnd: Long): Boolean
}

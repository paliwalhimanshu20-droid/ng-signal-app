package com.jarvis.tidb.intelligence.regime.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jarvis.tidb.intelligence.regime.entity.MarketRegimeEntity
import com.jarvis.tidb.intelligence.regime.entity.RegimeObservationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MarketRegimeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(regime: MarketRegimeEntity): Long

    @Update
    suspend fun update(regime: MarketRegimeEntity)

    @Query("SELECT * FROM market_regimes WHERE regimeId = :regimeId")
    suspend fun findById(regimeId: Long): MarketRegimeEntity?

    @Query("SELECT * FROM market_regimes WHERE instrumentId = :instrumentId AND timeframe = :timeframe AND endTimestamp IS NULL LIMIT 1")
    suspend fun findActive(instrumentId: Long, timeframe: String): MarketRegimeEntity?

    @Query("SELECT * FROM market_regimes WHERE instrumentId = :instrumentId AND timeframe = :timeframe ORDER BY startTimestamp DESC")
    fun observeHistory(instrumentId: Long, timeframe: String): Flow<List<MarketRegimeEntity>>

    @Query("SELECT * FROM market_regimes WHERE regimeType = :regimeType ORDER BY startTimestamp DESC")
    fun observeByType(regimeType: String): Flow<List<MarketRegimeEntity>>
}

@Dao
interface RegimeObservationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(observation: RegimeObservationEntity): Long

    @Query("SELECT * FROM regime_observations WHERE regimeId = :regimeId ORDER BY observedAt DESC")
    fun observeForRegime(regimeId: Long): Flow<List<RegimeObservationEntity>>
}

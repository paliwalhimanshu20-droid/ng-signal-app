package com.jarvis.tidb.historical.dna.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jarvis.tidb.historical.dna.entity.GapBehaviorProfileEntity
import com.jarvis.tidb.historical.dna.entity.IndicatorBehaviorProfileEntity
import com.jarvis.tidb.historical.dna.entity.LiquidityProfileEntity
import com.jarvis.tidb.historical.dna.entity.SeasonalTendencyEntity
import com.jarvis.tidb.historical.dna.entity.SessionBehaviorProfileEntity
import com.jarvis.tidb.historical.dna.entity.StatisticalCharacteristicsEntity
import com.jarvis.tidb.historical.dna.entity.TrendPersistenceProfileEntity
import com.jarvis.tidb.historical.dna.entity.VolatilityProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VolatilityProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: VolatilityProfileEntity): Long
    @Query("SELECT * FROM dna_volatility_profiles WHERE instrumentId = :instrumentId AND timeframe = :timeframe LIMIT 1")
    suspend fun find(instrumentId: Long, timeframe: String): VolatilityProfileEntity?
    @Query("SELECT * FROM dna_volatility_profiles WHERE instrumentId = :instrumentId")
    fun observeForInstrument(instrumentId: Long): Flow<List<VolatilityProfileEntity>>
}

@Dao
interface SessionBehaviorProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: SessionBehaviorProfileEntity): Long
    @Query("SELECT * FROM dna_session_behavior_profiles WHERE instrumentId = :instrumentId LIMIT 1")
    suspend fun find(instrumentId: Long): SessionBehaviorProfileEntity?
    @Query("SELECT * FROM dna_session_behavior_profiles WHERE instrumentId = :instrumentId LIMIT 1")
    fun observe(instrumentId: Long): Flow<SessionBehaviorProfileEntity?>
}

@Dao
interface TrendPersistenceProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: TrendPersistenceProfileEntity): Long
    @Query("SELECT * FROM dna_trend_persistence_profiles WHERE instrumentId = :instrumentId AND timeframe = :timeframe LIMIT 1")
    suspend fun find(instrumentId: Long, timeframe: String): TrendPersistenceProfileEntity?
    @Query("SELECT * FROM dna_trend_persistence_profiles WHERE instrumentId = :instrumentId")
    fun observeForInstrument(instrumentId: Long): Flow<List<TrendPersistenceProfileEntity>>
}

@Dao
interface LiquidityProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: LiquidityProfileEntity): Long
    @Query("SELECT * FROM dna_liquidity_profiles WHERE instrumentId = :instrumentId LIMIT 1")
    suspend fun find(instrumentId: Long): LiquidityProfileEntity?
    @Query("SELECT * FROM dna_liquidity_profiles WHERE instrumentId = :instrumentId LIMIT 1")
    fun observe(instrumentId: Long): Flow<LiquidityProfileEntity?>
    @Query("SELECT * FROM dna_liquidity_profiles WHERE liquidityScore < :threshold ORDER BY liquidityScore ASC")
    fun observeBelowThreshold(threshold: Double): Flow<List<LiquidityProfileEntity>>
}

@Dao
interface GapBehaviorProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: GapBehaviorProfileEntity): Long
    @Query("SELECT * FROM dna_gap_behavior_profiles WHERE instrumentId = :instrumentId LIMIT 1")
    suspend fun find(instrumentId: Long): GapBehaviorProfileEntity?
    @Query("SELECT * FROM dna_gap_behavior_profiles WHERE instrumentId = :instrumentId LIMIT 1")
    fun observe(instrumentId: Long): Flow<GapBehaviorProfileEntity?>
}

@Dao
interface SeasonalTendencyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tendencies: List<SeasonalTendencyEntity>): List<Long>
    @Query("SELECT * FROM dna_seasonal_tendencies WHERE instrumentId = :instrumentId AND bucketType = :bucketType ORDER BY bucketValue ASC")
    fun observe(instrumentId: Long, bucketType: String): Flow<List<SeasonalTendencyEntity>>
}

@Dao
interface IndicatorBehaviorProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: IndicatorBehaviorProfileEntity): Long
    @Query("SELECT * FROM dna_indicator_behavior_profiles WHERE instrumentId = :instrumentId AND indicatorDefId = :indicatorDefId AND timeframe = :timeframe LIMIT 1")
    suspend fun find(instrumentId: Long, indicatorDefId: Long, timeframe: String): IndicatorBehaviorProfileEntity?
    @Query("SELECT * FROM dna_indicator_behavior_profiles WHERE instrumentId = :instrumentId")
    fun observeForInstrument(instrumentId: Long): Flow<List<IndicatorBehaviorProfileEntity>>
}

@Dao
interface StatisticalCharacteristicsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StatisticalCharacteristicsEntity): Long
    @Query("SELECT * FROM dna_statistical_characteristics WHERE instrumentId = :instrumentId AND timeframe = :timeframe LIMIT 1")
    suspend fun find(instrumentId: Long, timeframe: String): StatisticalCharacteristicsEntity?
    @Query("SELECT * FROM dna_statistical_characteristics WHERE instrumentId = :instrumentId")
    fun observeForInstrument(instrumentId: Long): Flow<List<StatisticalCharacteristicsEntity>>
}

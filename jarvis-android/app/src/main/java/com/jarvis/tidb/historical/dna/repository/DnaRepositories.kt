package com.jarvis.tidb.historical.dna.repository

import com.jarvis.tidb.historical.dna.entity.GapBehaviorProfileEntity
import com.jarvis.tidb.historical.dna.entity.IndicatorBehaviorProfileEntity
import com.jarvis.tidb.historical.dna.entity.LiquidityProfileEntity
import com.jarvis.tidb.historical.dna.entity.SeasonalTendencyEntity
import com.jarvis.tidb.historical.dna.entity.SessionBehaviorProfileEntity
import com.jarvis.tidb.historical.dna.entity.StatisticalCharacteristicsEntity
import com.jarvis.tidb.historical.dna.entity.TrendPersistenceProfileEntity
import com.jarvis.tidb.historical.dna.entity.VolatilityProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * One repository per DNA facet, all following the same upsert-by-natural-key shape. Grouped
 * behind a single [InstrumentDnaRepository] facade for callers that want "the whole profile"
 * without wiring eight dependencies individually.
 */
interface VolatilityProfileRepository {
    suspend fun upsert(profile: VolatilityProfileEntity): Long
    suspend fun get(instrumentId: Long, timeframe: String): VolatilityProfileEntity?
    fun observeForInstrument(instrumentId: Long): Flow<List<VolatilityProfileEntity>>
}

interface SessionBehaviorProfileRepository {
    suspend fun upsert(profile: SessionBehaviorProfileEntity): Long
    suspend fun get(instrumentId: Long): SessionBehaviorProfileEntity?
    fun observe(instrumentId: Long): Flow<SessionBehaviorProfileEntity?>
}

interface TrendPersistenceProfileRepository {
    suspend fun upsert(profile: TrendPersistenceProfileEntity): Long
    suspend fun get(instrumentId: Long, timeframe: String): TrendPersistenceProfileEntity?
    fun observeForInstrument(instrumentId: Long): Flow<List<TrendPersistenceProfileEntity>>
}

interface LiquidityProfileRepository {
    suspend fun upsert(profile: LiquidityProfileEntity): Long
    suspend fun get(instrumentId: Long): LiquidityProfileEntity?
    fun observe(instrumentId: Long): Flow<LiquidityProfileEntity?>
    fun observeBelowThreshold(threshold: Double): Flow<List<LiquidityProfileEntity>>
}

interface GapBehaviorProfileRepository {
    suspend fun upsert(profile: GapBehaviorProfileEntity): Long
    suspend fun get(instrumentId: Long): GapBehaviorProfileEntity?
    fun observe(instrumentId: Long): Flow<GapBehaviorProfileEntity?>
}

interface SeasonalTendencyRepository {
    suspend fun upsertAll(tendencies: List<SeasonalTendencyEntity>): List<Long>
    fun observe(instrumentId: Long, bucketType: String): Flow<List<SeasonalTendencyEntity>>
}

interface IndicatorBehaviorProfileRepository {
    suspend fun upsert(profile: IndicatorBehaviorProfileEntity): Long
    suspend fun get(instrumentId: Long, indicatorDefId: Long, timeframe: String): IndicatorBehaviorProfileEntity?
    fun observeForInstrument(instrumentId: Long): Flow<List<IndicatorBehaviorProfileEntity>>
}

interface StatisticalCharacteristicsRepository {
    suspend fun upsert(entity: StatisticalCharacteristicsEntity): Long
    suspend fun get(instrumentId: Long, timeframe: String): StatisticalCharacteristicsEntity?
    fun observeForInstrument(instrumentId: Long): Flow<List<StatisticalCharacteristicsEntity>>
}

package com.jarvis.tidb.historical.dna.repository.impl

import com.jarvis.tidb.historical.dna.dao.GapBehaviorProfileDao
import com.jarvis.tidb.historical.dna.dao.IndicatorBehaviorProfileDao
import com.jarvis.tidb.historical.dna.dao.LiquidityProfileDao
import com.jarvis.tidb.historical.dna.dao.SeasonalTendencyDao
import com.jarvis.tidb.historical.dna.dao.SessionBehaviorProfileDao
import com.jarvis.tidb.historical.dna.dao.StatisticalCharacteristicsDao
import com.jarvis.tidb.historical.dna.dao.TrendPersistenceProfileDao
import com.jarvis.tidb.historical.dna.dao.VolatilityProfileDao
import com.jarvis.tidb.historical.dna.entity.GapBehaviorProfileEntity
import com.jarvis.tidb.historical.dna.entity.IndicatorBehaviorProfileEntity
import com.jarvis.tidb.historical.dna.entity.LiquidityProfileEntity
import com.jarvis.tidb.historical.dna.entity.SeasonalTendencyEntity
import com.jarvis.tidb.historical.dna.entity.SessionBehaviorProfileEntity
import com.jarvis.tidb.historical.dna.entity.StatisticalCharacteristicsEntity
import com.jarvis.tidb.historical.dna.entity.TrendPersistenceProfileEntity
import com.jarvis.tidb.historical.dna.entity.VolatilityProfileEntity
import com.jarvis.tidb.historical.dna.repository.GapBehaviorProfileRepository
import com.jarvis.tidb.historical.dna.repository.IndicatorBehaviorProfileRepository
import com.jarvis.tidb.historical.dna.repository.LiquidityProfileRepository
import com.jarvis.tidb.historical.dna.repository.SeasonalTendencyRepository
import com.jarvis.tidb.historical.dna.repository.SessionBehaviorProfileRepository
import com.jarvis.tidb.historical.dna.repository.StatisticalCharacteristicsRepository
import com.jarvis.tidb.historical.dna.repository.TrendPersistenceProfileRepository
import com.jarvis.tidb.historical.dna.repository.VolatilityProfileRepository
import kotlinx.coroutines.flow.Flow

class VolatilityProfileRepositoryImpl(private val dao: VolatilityProfileDao) : VolatilityProfileRepository {
    override suspend fun upsert(profile: VolatilityProfileEntity): Long = dao.upsert(profile)
    override suspend fun get(instrumentId: Long, timeframe: String): VolatilityProfileEntity? = dao.find(instrumentId, timeframe)
    override fun observeForInstrument(instrumentId: Long): Flow<List<VolatilityProfileEntity>> = dao.observeForInstrument(instrumentId)
}

class SessionBehaviorProfileRepositoryImpl(private val dao: SessionBehaviorProfileDao) : SessionBehaviorProfileRepository {
    override suspend fun upsert(profile: SessionBehaviorProfileEntity): Long = dao.upsert(profile)
    override suspend fun get(instrumentId: Long): SessionBehaviorProfileEntity? = dao.find(instrumentId)
    override fun observe(instrumentId: Long): Flow<SessionBehaviorProfileEntity?> = dao.observe(instrumentId)
}

class TrendPersistenceProfileRepositoryImpl(private val dao: TrendPersistenceProfileDao) : TrendPersistenceProfileRepository {
    override suspend fun upsert(profile: TrendPersistenceProfileEntity): Long = dao.upsert(profile)
    override suspend fun get(instrumentId: Long, timeframe: String): TrendPersistenceProfileEntity? = dao.find(instrumentId, timeframe)
    override fun observeForInstrument(instrumentId: Long): Flow<List<TrendPersistenceProfileEntity>> = dao.observeForInstrument(instrumentId)
}

class LiquidityProfileRepositoryImpl(private val dao: LiquidityProfileDao) : LiquidityProfileRepository {
    override suspend fun upsert(profile: LiquidityProfileEntity): Long = dao.upsert(profile)
    override suspend fun get(instrumentId: Long): LiquidityProfileEntity? = dao.find(instrumentId)
    override fun observe(instrumentId: Long): Flow<LiquidityProfileEntity?> = dao.observe(instrumentId)
    override fun observeBelowThreshold(threshold: Double): Flow<List<LiquidityProfileEntity>> = dao.observeBelowThreshold(threshold)
}

class GapBehaviorProfileRepositoryImpl(private val dao: GapBehaviorProfileDao) : GapBehaviorProfileRepository {
    override suspend fun upsert(profile: GapBehaviorProfileEntity): Long = dao.upsert(profile)
    override suspend fun get(instrumentId: Long): GapBehaviorProfileEntity? = dao.find(instrumentId)
    override fun observe(instrumentId: Long): Flow<GapBehaviorProfileEntity?> = dao.observe(instrumentId)
}

class SeasonalTendencyRepositoryImpl(private val dao: SeasonalTendencyDao) : SeasonalTendencyRepository {
    override suspend fun upsertAll(tendencies: List<SeasonalTendencyEntity>): List<Long> = dao.upsertAll(tendencies)
    override fun observe(instrumentId: Long, bucketType: String): Flow<List<SeasonalTendencyEntity>> = dao.observe(instrumentId, bucketType)
}

class IndicatorBehaviorProfileRepositoryImpl(private val dao: IndicatorBehaviorProfileDao) : IndicatorBehaviorProfileRepository {
    override suspend fun upsert(profile: IndicatorBehaviorProfileEntity): Long = dao.upsert(profile)
    override suspend fun get(instrumentId: Long, indicatorDefId: Long, timeframe: String): IndicatorBehaviorProfileEntity? =
        dao.find(instrumentId, indicatorDefId, timeframe)
    override fun observeForInstrument(instrumentId: Long): Flow<List<IndicatorBehaviorProfileEntity>> = dao.observeForInstrument(instrumentId)
}

class StatisticalCharacteristicsRepositoryImpl(private val dao: StatisticalCharacteristicsDao) : StatisticalCharacteristicsRepository {
    override suspend fun upsert(entity: StatisticalCharacteristicsEntity): Long = dao.upsert(entity)
    override suspend fun get(instrumentId: Long, timeframe: String): StatisticalCharacteristicsEntity? = dao.find(instrumentId, timeframe)
    override fun observeForInstrument(instrumentId: Long): Flow<List<StatisticalCharacteristicsEntity>> = dao.observeForInstrument(instrumentId)
}

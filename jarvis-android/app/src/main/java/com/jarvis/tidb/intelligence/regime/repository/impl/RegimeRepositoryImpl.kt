package com.jarvis.tidb.intelligence.regime.repository.impl

import com.jarvis.tidb.intelligence.regime.dao.MarketRegimeDao
import com.jarvis.tidb.intelligence.regime.dao.RegimeObservationDao
import com.jarvis.tidb.intelligence.regime.entity.MarketRegimeEntity
import com.jarvis.tidb.intelligence.regime.entity.RegimeObservationEntity
import com.jarvis.tidb.intelligence.regime.entity.RegimeType
import com.jarvis.tidb.intelligence.regime.repository.RegimeRepository
import kotlinx.coroutines.flow.Flow

class RegimeRepositoryImpl(
    private val regimeDao: MarketRegimeDao,
    private val observationDao: RegimeObservationDao
) : RegimeRepository {

    override suspend fun openRegime(regime: MarketRegimeEntity): Long = regimeDao.insert(regime)

    override suspend fun getActiveRegime(instrumentId: Long, timeframe: String): MarketRegimeEntity? =
        regimeDao.findActive(instrumentId, timeframe)

    override suspend fun transitionRegime(
        instrumentId: Long,
        timeframe: String,
        endTimestamp: Long,
        next: MarketRegimeEntity
    ): Long {
        val active = regimeDao.findActive(instrumentId, timeframe)
        if (active != null) {
            regimeDao.update(active.copy(endTimestamp = endTimestamp, audit = active.audit.touched()))
        }
        return regimeDao.insert(next.copy(instrumentId = instrumentId, timeframe = timeframe))
    }

    override fun observeRegimeHistory(instrumentId: Long, timeframe: String): Flow<List<MarketRegimeEntity>> =
        regimeDao.observeHistory(instrumentId, timeframe)

    override fun observeRegimesByType(regimeType: RegimeType): Flow<List<MarketRegimeEntity>> =
        regimeDao.observeByType(regimeType.value)

    override suspend fun recordObservation(observation: RegimeObservationEntity): Long =
        observationDao.insert(observation)

    override fun observeObservationsForRegime(regimeId: Long): Flow<List<RegimeObservationEntity>> =
        observationDao.observeForRegime(regimeId)
}

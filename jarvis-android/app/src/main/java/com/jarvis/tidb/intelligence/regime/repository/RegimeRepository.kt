package com.jarvis.tidb.intelligence.regime.repository

import com.jarvis.tidb.intelligence.regime.entity.MarketRegimeEntity
import com.jarvis.tidb.intelligence.regime.entity.RegimeObservationEntity
import com.jarvis.tidb.intelligence.regime.entity.RegimeType
import kotlinx.coroutines.flow.Flow

interface RegimeRepository {
    suspend fun openRegime(regime: MarketRegimeEntity): Long
    suspend fun getActiveRegime(instrumentId: Long, timeframe: String): MarketRegimeEntity?

    /** Closes the currently active regime for [instrumentId]/[timeframe] (if any) at [endTimestamp], then opens [next] as the new active regime — an atomic transition. */
    suspend fun transitionRegime(instrumentId: Long, timeframe: String, endTimestamp: Long, next: MarketRegimeEntity): Long

    fun observeRegimeHistory(instrumentId: Long, timeframe: String): Flow<List<MarketRegimeEntity>>
    fun observeRegimesByType(regimeType: RegimeType): Flow<List<MarketRegimeEntity>>

    suspend fun recordObservation(observation: RegimeObservationEntity): Long
    fun observeObservationsForRegime(regimeId: Long): Flow<List<RegimeObservationEntity>>
}

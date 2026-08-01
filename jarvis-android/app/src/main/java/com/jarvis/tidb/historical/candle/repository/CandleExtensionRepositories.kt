package com.jarvis.tidb.historical.candle.repository

import com.jarvis.tidb.historical.candle.entity.CandleGapEntity
import com.jarvis.tidb.historical.candle.entity.CandleVersionEntity
import com.jarvis.tidb.historical.candle.entity.GapStatus
import kotlinx.coroutines.flow.Flow

interface CandleVersionRepository {
    /** Snapshots the given prior OHLCV state as the next version for [candleId], then returns the new version number. */
    suspend fun recordCorrection(
        candleId: Long,
        priorOpen: Double,
        priorHigh: Double,
        priorLow: Double,
        priorClose: Double,
        priorVolume: Long,
        priorOpenInterest: Long?,
        priorQualityScore: Double,
        reason: String,
        actor: String = "SYSTEM"
    ): Int

    fun observeHistory(candleId: Long): Flow<List<CandleVersionEntity>>
}

interface CandleGapRepository {
    suspend fun reportGap(gap: CandleGapEntity): Long
    suspend fun reportGaps(gaps: List<CandleGapEntity>): List<Long>
    suspend fun alreadyKnown(instrumentId: Long, timeframe: String, gapStart: Long, gapEnd: Long): Boolean
    fun observeByInstrumentTimeframe(instrumentId: Long, timeframe: String): Flow<List<CandleGapEntity>>
    fun observeUnresolved(): Flow<List<CandleGapEntity>>
    fun observeUnresolvedCount(): Flow<Int>
    suspend fun linkBackfillJob(gapId: Long, jobId: Long)
    suspend fun markResolved(gapId: Long, status: GapStatus = GapStatus.BACKFILLED)
}

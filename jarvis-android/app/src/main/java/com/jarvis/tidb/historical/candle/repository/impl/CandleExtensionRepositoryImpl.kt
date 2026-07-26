package com.jarvis.tidb.historical.candle.repository.impl

import com.jarvis.tidb.historical.candle.dao.CandleGapDao
import com.jarvis.tidb.historical.candle.dao.CandleVersionDao
import com.jarvis.tidb.historical.candle.entity.CandleGapEntity
import com.jarvis.tidb.historical.candle.entity.CandleVersionEntity
import com.jarvis.tidb.historical.candle.entity.GapStatus
import com.jarvis.tidb.historical.candle.repository.CandleGapRepository
import com.jarvis.tidb.historical.candle.repository.CandleVersionRepository
import kotlinx.coroutines.flow.Flow

class CandleVersionRepositoryImpl(private val dao: CandleVersionDao) : CandleVersionRepository {

    override suspend fun recordCorrection(
        candleId: Long,
        priorOpen: Double,
        priorHigh: Double,
        priorLow: Double,
        priorClose: Double,
        priorVolume: Long,
        priorOpenInterest: Long?,
        priorQualityScore: Double,
        reason: String,
        actor: String
    ): Int {
        val nextVersion = dao.latestVersionNumber(candleId) + 1
        dao.insert(
            CandleVersionEntity(
                candleId = candleId,
                versionNumber = nextVersion,
                open = priorOpen,
                high = priorHigh,
                low = priorLow,
                close = priorClose,
                volume = priorVolume,
                openInterest = priorOpenInterest,
                qualityScore = priorQualityScore,
                changeReason = reason,
                changedBy = actor
            )
        )
        return nextVersion
    }

    override fun observeHistory(candleId: Long): Flow<List<CandleVersionEntity>> = dao.observeHistory(candleId)
}

class CandleGapRepositoryImpl(private val dao: CandleGapDao) : CandleGapRepository {

    override suspend fun reportGap(gap: CandleGapEntity): Long = dao.insert(gap)

    override suspend fun reportGaps(gaps: List<CandleGapEntity>): List<Long> = dao.insertAll(gaps)

    override suspend fun alreadyKnown(instrumentId: Long, timeframe: String, gapStart: Long, gapEnd: Long): Boolean =
        dao.exists(instrumentId, timeframe, gapStart, gapEnd)

    override fun observeByInstrumentTimeframe(instrumentId: Long, timeframe: String): Flow<List<CandleGapEntity>> =
        dao.observeByInstrumentTimeframe(instrumentId, timeframe)

    override fun observeUnresolved(): Flow<List<CandleGapEntity>> = dao.observeUnresolved()

    override fun observeUnresolvedCount(): Flow<Int> = dao.observeUnresolvedCount()

    override suspend fun linkBackfillJob(gapId: Long, jobId: Long) {
        val gap = dao.findById(gapId) ?: return
        dao.update(gap.copy(status = GapStatus.BACKFILLING, backfillJobId = jobId, audit = gap.audit.touched()))
    }

    override suspend fun markResolved(gapId: Long, status: GapStatus) {
        val gap = dao.findById(gapId) ?: return
        dao.update(
            gap.copy(status = status, resolvedAt = System.currentTimeMillis(), audit = gap.audit.touched())
        )
    }
}

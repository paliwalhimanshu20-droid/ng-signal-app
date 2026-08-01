package com.jarvis.os.app.core.intelligence.localintent

import com.jarvis.tidb.analytics.entity.BacktestConfigurationEntity
import com.jarvis.tidb.analytics.entity.BacktestEntity
import com.jarvis.tidb.analytics.entity.BacktestResultEntity
import com.jarvis.tidb.analytics.entity.BacktestRunEntity
import com.jarvis.tidb.analytics.entity.BacktestRunWithDetails
import com.jarvis.tidb.analytics.entity.BacktestStatus
import com.jarvis.tidb.analytics.entity.BacktestTradeEntity
import com.jarvis.tidb.analytics.repository.BacktestRepository
import com.jarvis.tidb.core.entity.AssetClass
import com.jarvis.tidb.core.entity.InstrumentEntity
import com.jarvis.tidb.core.entity.InstrumentType
import com.jarvis.tidb.core.repository.InstrumentRepository
import com.jarvis.tidb.optimization.entity.OptimizationCombinationEntity
import com.jarvis.tidb.optimization.entity.OptimizationJobEntity
import com.jarvis.tidb.optimization.entity.OptimizationJobStatus
import com.jarvis.tidb.optimization.repository.OptimizationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Phase 3C, Section 5+9 -- Database State Awareness." */
class SystemStatusLocalIntentHandlerTest {

    private class FakeInstrumentRepository(private val all: List<InstrumentEntity>) : InstrumentRepository {
        override suspend fun upsert(instrument: InstrumentEntity) = 0L
        override suspend fun getById(instrumentId: Long) = all.firstOrNull { it.instrumentId == instrumentId }
        override suspend fun getByUuid(uuid: String) = all.firstOrNull { it.uuid == uuid }
        override suspend fun getBySymbol(symbol: String) = all.firstOrNull { it.symbol == symbol }
        override suspend fun exists(instrumentId: Long) = all.any { it.instrumentId == instrumentId }
        override fun observeAll(): Flow<List<InstrumentEntity>> = flowOf(all)
        override fun observeByExchange(exchangeId: Long): Flow<List<InstrumentEntity>> = flowOf(emptyList())
        override fun observeByAssetClass(assetClass: String): Flow<List<InstrumentEntity>> = flowOf(emptyList())
        override suspend fun getWithEvents(instrumentId: Long) = null
        override suspend fun getFullDetail(instrumentId: Long) = null
        override suspend fun softDelete(instrumentId: Long) = Unit
    }

    private class FakeOptimizationRepository(private val jobs: List<OptimizationJobEntity>) : OptimizationRepository {
        override suspend fun createJob(componentId: String, algorithmId: String, instrumentId: Long, timeframeValue: String, periodStart: Long, periodEnd: Long, budget: Int, randomSeed: Long?) = throw NotImplementedError()
        override suspend fun getJob(jobRowId: Long) = jobs.firstOrNull { it.rowId == jobRowId }
        override fun observeJob(jobRowId: Long): Flow<OptimizationJobEntity?> = flowOf(jobs.firstOrNull { it.rowId == jobRowId })
        override fun observeAllJobs(): Flow<List<OptimizationJobEntity>> = flowOf(jobs)
        override suspend fun findResumableJobs() = emptyList<OptimizationJobEntity>()
        override suspend fun pendingCombinations(jobRowId: Long) = emptyList<OptimizationCombinationEntity>()
        override suspend fun markJobRunning(jobRowId: Long) {}
        override suspend fun markJobCancelled(jobRowId: Long) {}
        override suspend fun markJobFailed(jobRowId: Long, errorMessage: String) {}
        override suspend fun markCombinationEvaluated(combinationRowId: Long, backtestRunRowId: Long?, backtestResultRowId: Long?) {}
        override suspend fun markCombinationFailed(combinationRowId: Long, errorMessage: String) {}
        override suspend fun rankedCombinations(jobRowId: Long) = emptyList<OptimizationCombinationEntity>()
        override suspend fun rankCombinations(jobRowId: Long, rankedRowIdsBestFirst: List<Long>) {}
    }

    private class FakeBacktestRepository(private val backtests: List<BacktestEntity> = emptyList()) : BacktestRepository {
        override suspend fun createBacktest(backtest: BacktestEntity) = 1L
        override suspend fun updateBacktest(backtest: BacktestEntity) {}
        override suspend fun getBacktest(rowId: Long) = backtests.firstOrNull { it.rowId == rowId }
        override fun observeBacktestsByStrategy(strategyId: String): Flow<List<BacktestEntity>> = flowOf(emptyList())
        override fun observeAllBacktests(): Flow<List<BacktestEntity>> = flowOf(backtests)
        override suspend fun addConfiguration(configuration: BacktestConfigurationEntity) = 1L
        override suspend fun latestConfiguration(backtestRowId: Long): BacktestConfigurationEntity? = null
        override suspend fun startRun(run: BacktestRunEntity) = 1L
        override suspend fun updateRun(run: BacktestRunEntity) {}
        override fun observeRunsByBacktest(backtestRowId: Long): Flow<List<BacktestRunEntity>> = flowOf(emptyList())
        override fun observeRunsByStatus(status: BacktestStatus): Flow<List<BacktestRunEntity>> = flowOf(emptyList())
        override suspend fun getRunWithDetails(runRowId: Long): BacktestRunWithDetails? = null
        override suspend fun recordGeneratedTrades(trades: List<BacktestTradeEntity>) = emptyList<Long>()
        override fun observeTradesByRun(runRowId: Long): Flow<List<BacktestTradeEntity>> = flowOf(emptyList())
        override suspend fun recordResult(result: BacktestResultEntity) = 1L
        override suspend fun getResultForRun(runRowId: Long): BacktestResultEntity? = null
        override fun observeResultsByBacktest(backtestRowId: Long): Flow<List<BacktestResultEntity>> = flowOf(emptyList())
    }

    private val naturalGas = InstrumentEntity(
        instrumentId = 1L, symbol = "NATURALGAS", displayName = "Natural Gas", exchangeId = 1L,
        assetClass = AssetClass.COMMODITY, instrumentType = InstrumentType.FUTURE,
        tickSize = 0.1, lotSize = 1250, multiplier = 1.0, quoteCurrency = "INR", tradingCurrency = "INR", tradingHours = "09:00-23:30",
    )

    @Test
    fun `system status reports real counts, not a bare no-data message`() = runTest {
        val job = OptimizationJobEntity(
            rowId = 1L, componentId = "INDICATOR:EMA", algorithmId = "GRID_SEARCH", instrumentId = 1L,
            timeframeValue = "1d", periodStart = 0L, periodEnd = 100L, budget = 400,
            statusValue = OptimizationJobStatus.COMPLETED.name, totalCombinations = 299, completedCombinations = 299,
        )
        val handler = SystemStatusLocalIntentHandler(
            FakeInstrumentRepository(listOf(naturalGas)), FakeOptimizationRepository(listOf(job)), FakeBacktestRepository(),
        )

        val answer = handler.tryHandle("What's the system status?")

        assertTrue(answer!!.response.contains("1 instrument"))
        assertTrue(answer.response.contains("26 indicator types"))
        assertTrue(answer.response.contains("1 job(s)"))
        assertTrue(answer.response.contains("299 of 299"))
        assertTrue("must honestly report no backtests when there are none, not fabricate results", answer.response.contains("no backtests recorded yet"))
    }

    @Test
    fun `an unrelated question never matches this handler`() = runTest {
        val handler = SystemStatusLocalIntentHandler(FakeInstrumentRepository(emptyList()), FakeOptimizationRepository(emptyList()), FakeBacktestRepository())

        assertNull(handler.tryHandle("What's the price of natural gas?"))
    }
}

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Phase 3C, Section 1+2 -- Evidence Validation Engine + Hallucination Guard." The central claim
 * under test: this handler NEVER returns null for a statistic-shaped question (which would let it
 * fall through to the AI path), and NEVER invents a number that isn't backed by a real stored
 * entity.
 */
class EvidenceValidationLocalIntentHandlerTest {

    private class FakeOptimizationRepository(
        private val jobs: List<OptimizationJobEntity> = emptyList(),
        private val rankedByJob: Map<Long, List<OptimizationCombinationEntity>> = emptyMap(),
    ) : OptimizationRepository {
        override suspend fun createJob(componentId: String, algorithmId: String, instrumentId: Long, timeframeValue: String, periodStart: Long, periodEnd: Long, budget: Int, randomSeed: Long?) = throw NotImplementedError()
        override suspend fun getJob(jobRowId: Long) = jobs.firstOrNull { it.rowId == jobRowId }
        override fun observeJob(jobRowId: Long): Flow<OptimizationJobEntity?> = flowOf(jobs.firstOrNull { it.rowId == jobRowId })
        override fun observeAllJobs(): Flow<List<OptimizationJobEntity>> = flowOf(jobs)
        override suspend fun findResumableJobs() = jobs.filter { it.statusValue in setOf("QUEUED", "RUNNING") }
        override suspend fun pendingCombinations(jobRowId: Long) = emptyList<OptimizationCombinationEntity>()
        override suspend fun markJobRunning(jobRowId: Long) {}
        override suspend fun markJobCancelled(jobRowId: Long) {}
        override suspend fun markJobFailed(jobRowId: Long, errorMessage: String) {}
        override suspend fun markCombinationEvaluated(combinationRowId: Long, backtestRunRowId: Long?, backtestResultRowId: Long?) {}
        override suspend fun markCombinationFailed(combinationRowId: Long, errorMessage: String) {}
        override suspend fun linkBacktest(jobRowId: Long, backtestRowId: Long) {}
        override suspend fun rankedCombinations(jobRowId: Long): List<OptimizationCombinationEntity> = rankedByJob[jobRowId].orEmpty()
        override suspend fun rankCombinations(jobRowId: Long, rankedRowIdsBestFirst: List<Long>) {}
        override suspend fun completedCombinations(jobRowId: Long): List<OptimizationCombinationEntity> = emptyList()
    }

    private class FakeBacktestRepository(
        private val backtests: List<BacktestEntity> = emptyList(),
        private val resultsByBacktest: Map<Long, List<BacktestResultEntity>> = emptyMap(),
    ) : BacktestRepository {
        override suspend fun createBacktest(backtest: BacktestEntity) = 1L
        override suspend fun updateBacktest(backtest: BacktestEntity) {}
        override suspend fun getBacktest(rowId: Long) = backtests.firstOrNull { it.rowId == rowId }
        override fun observeBacktestsByStrategy(strategyId: String): Flow<List<BacktestEntity>> = flowOf(backtests.filter { it.strategyId == strategyId })
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
        override fun observeResultsByBacktest(backtestRowId: Long): Flow<List<BacktestResultEntity>> = flowOf(resultsByBacktest[backtestRowId].orEmpty())
    }

    private class FakeInstrumentRepository(private val all: List<InstrumentEntity>) : InstrumentRepository {
        override suspend fun upsert(instrument: InstrumentEntity) = 0L
        override suspend fun getById(instrumentId: Long) = all.firstOrNull { it.instrumentId == instrumentId }
        override suspend fun getByUuid(uuid: String) = all.firstOrNull { it.uuid == uuid }
        override suspend fun getBySymbol(symbol: String) = all.firstOrNull { it.symbol == symbol }
        override suspend fun exists(instrumentId: Long) = all.any { it.instrumentId == instrumentId }
        override fun observeAll(): Flow<List<InstrumentEntity>> = flowOf(all)
        override fun observeByExchange(exchangeId: Long): Flow<List<InstrumentEntity>> = flowOf(all.filter { it.exchangeId == exchangeId })
        override fun observeByAssetClass(assetClass: String): Flow<List<InstrumentEntity>> = flowOf(all.filter { it.assetClass.value == assetClass })
        override suspend fun getWithEvents(instrumentId: Long) = null
        override suspend fun getFullDetail(instrumentId: Long) = null
        override suspend fun softDelete(instrumentId: Long) = Unit
    }

    private val naturalGas = InstrumentEntity(
        instrumentId = 1L, symbol = "NATURALGAS", displayName = "Natural Gas", exchangeId = 1L,
        assetClass = AssetClass.COMMODITY, instrumentType = InstrumentType.FUTURE,
        tickSize = 0.1, lotSize = 1250, multiplier = 1.0, quoteCurrency = "INR", tradingCurrency = "INR", tradingHours = "09:00-23:30",
    )

    @Test
    fun `a Sharpe ratio question with zero evidence anywhere explains honestly, never invents a number`() = runTest {
        val handler = EvidenceValidationLocalIntentHandler(
            FakeOptimizationRepository(), FakeBacktestRepository(), FakeInstrumentRepository(listOf(naturalGas)),
        )

        val answer = handler.tryHandle("What's the Sharpe ratio for natural gas?")

        assertTrue(answer != null)
        assertNoNumericFabrication(answer!!.response)
        assertTrue(answer.response.contains("don't have"))
        assertTrue(answer.response.contains("execution engine"))
    }

    @Test
    fun `a best-EMA question with a real ranked optimization job reports the real parameters, never a performance number`() = runTest {
        val job = OptimizationJobEntity(
            rowId = 1L, componentId = "INDICATOR:EMA", algorithmId = "GRID_SEARCH", instrumentId = 1L,
            timeframeValue = "1d", periodStart = 0L, periodEnd = 100L, budget = 400,
            statusValue = OptimizationJobStatus.COMPLETED.name, totalCombinations = 299,
        )
        val bestCombo = OptimizationCombinationEntity(
            rowId = 10L, jobRowId = 1L, combinationIndex = 0, parametersJson = """{"period":21.0}""", rank = 1,
        )
        val handler = EvidenceValidationLocalIntentHandler(
            FakeOptimizationRepository(jobs = listOf(job), rankedByJob = mapOf(1L to listOf(bestCombo))),
            FakeBacktestRepository(),
            FakeInstrumentRepository(listOf(naturalGas)),
        )

        val answer = handler.tryHandle("What's the best EMA for natural gas?")

        assertTrue(answer!!.response.contains("period"))
        assertTrue(answer.response.contains("21.0"))
        assertFalse("must never claim a win rate that was never computed", Regex("(?i)win rate:? \\d").containsMatchIn(answer.response))
        assertTrue(answer.response.contains("don't have backtest performance"))
    }

    @Test
    fun `a question with real stored backtest results reports those exact real numbers`() = runTest {
        val backtest = BacktestEntity(rowId = 5L, name = "EMA Crossover NG", strategyId = "EMA_CROSS", periodStart = 0L, periodEnd = 100L, instrumentIdsCsv = "1")
        val result = BacktestResultEntity(
            runRowId = 1L, totalTrades = 40, winningTrades = 24, losingTrades = 16, netProfit = 12000.0,
            winRate = 60.0, maxDrawdown = 2000.0, maxDrawdownPercent = 8.5, sharpeRatio = 1.42, profitFactor = 1.8,
            startingCapital = 100000.0, endingCapital = 112000.0,
        )
        val handler = EvidenceValidationLocalIntentHandler(
            FakeOptimizationRepository(), FakeBacktestRepository(backtests = listOf(backtest), resultsByBacktest = mapOf(5L to listOf(result))),
            FakeInstrumentRepository(listOf(naturalGas)),
        )

        val answer = handler.tryHandle("What's the win rate for natural gas?")

        assertTrue(answer!!.response.contains("60.0"))
        assertTrue(answer.response.contains("1.42"))
        assertTrue(answer.response.contains("real, stored evidence"))
    }

    @Test
    fun `an ordinary non-statistic question never matches this handler, leaving room for the AI path`() = runTest {
        val handler = EvidenceValidationLocalIntentHandler(FakeOptimizationRepository(), FakeBacktestRepository(), FakeInstrumentRepository(emptyList()))

        assertNull(handler.tryHandle("What's the weather like today?"))
    }

    @Test
    fun `a statistic-shaped question always resolves LOCAL_ONLY, regardless of evidence, never falling through`() = runTest {
        val handler = EvidenceValidationLocalIntentHandler(FakeOptimizationRepository(), FakeBacktestRepository(), FakeInstrumentRepository(emptyList()))

        val answer = handler.tryHandle("What's the best strategy overall?")

        assertTrue("a statistic-shaped question must always resolve, never return null", answer != null)
        assertTrue(answer!!.outcome == LocalIntentOutcome.LOCAL_ONLY)
    }

    private fun assertNoNumericFabrication(response: String) {
        assertFalse(Regex("(?i)sharpe (ratio )?(is|of|:) ?\\d").containsMatchIn(response))
    }
}

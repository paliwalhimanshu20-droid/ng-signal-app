package com.jarvis.tidb.historical.ingestion.status

import com.jarvis.tidb.analytics.entity.BacktestConfigurationEntity
import com.jarvis.tidb.analytics.entity.BacktestEntity
import com.jarvis.tidb.analytics.entity.BacktestResultEntity
import com.jarvis.tidb.analytics.entity.BacktestRunEntity
import com.jarvis.tidb.analytics.entity.BacktestRunWithDetails
import com.jarvis.tidb.analytics.entity.BacktestStatus
import com.jarvis.tidb.analytics.entity.BacktestTradeEntity
import com.jarvis.tidb.analytics.repository.BacktestRepository
import com.jarvis.tidb.core.entity.CandleSource
import com.jarvis.tidb.core.entity.HistoricalCandleEntity
import com.jarvis.tidb.core.entity.Timeframe
import com.jarvis.tidb.core.repository.HistoricalCandleRepository
import com.jarvis.tidb.historical.indicator.entity.IndicatorDefinitionEntity
import com.jarvis.tidb.historical.indicator.entity.IndicatorType
import com.jarvis.tidb.historical.indicator.entity.IndicatorValueEntity
import com.jarvis.tidb.historical.indicator.repository.IndicatorDefinitionRepository
import com.jarvis.tidb.historical.indicator.repository.IndicatorValueRepository
import com.jarvis.tidb.historical.ingestion.entity.IngestionEventType
import com.jarvis.tidb.historical.ingestion.entity.IngestionJobEntity
import com.jarvis.tidb.historical.ingestion.entity.IngestionJobLogEntity
import com.jarvis.tidb.historical.ingestion.entity.IngestionJobStatus
import com.jarvis.tidb.historical.ingestion.entity.IngestionJobType
import com.jarvis.tidb.historical.ingestion.repository.IngestionJobRepository
import com.jarvis.tidb.historical.quality.entity.CandleQualityReportEntity
import com.jarvis.tidb.historical.quality.entity.QualityIssueEntity
import com.jarvis.tidb.historical.quality.repository.QualityReportRepository
import com.jarvis.tidb.optimization.entity.OptimizationCombinationEntity
import com.jarvis.tidb.optimization.entity.OptimizationJobEntity
import com.jarvis.tidb.optimization.repository.OptimizationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Phase 4A Increment 2, Sections 1+3+7." Covers the empty, partial, and fully-ready states for READY_FOR_OPTIMIZATION -- the property under test with the most consequence if wrong. */
class DatasetStatusEngineTest {

    private class FakeCandleRepo(private val candles: List<HistoricalCandleEntity> = emptyList()) : HistoricalCandleRepository {
        override suspend fun insert(candle: HistoricalCandleEntity) = 0L
        override suspend fun insertAll(candles: List<HistoricalCandleEntity>) = emptyList<Long>()
        override fun observeRange(instrumentId: Long, timeframe: Timeframe, fromMillis: Long, toMillis: Long): Flow<List<HistoricalCandleEntity>> = flowOf(candles)
        override suspend fun getLatest(instrumentId: Long, timeframe: Timeframe, limit: Int) = candles.takeLast(limit)
        override suspend fun softDeleteByImportBatch(importBatchId: String) {}
    }

    private class FakeQualityRepo(private val report: CandleQualityReportEntity? = null) : QualityReportRepository {
        override suspend fun publishReport(report: CandleQualityReportEntity, issues: List<QualityIssueEntity>) = 1L
        override suspend fun getLatest(instrumentId: Long, timeframe: String) = report
        override fun observeByInstrument(instrumentId: Long): Flow<List<CandleQualityReportEntity>> = flowOf(emptyList())
        override fun observeBelowThreshold(threshold: Double): Flow<List<CandleQualityReportEntity>> = flowOf(emptyList())
        override fun observeIssues(reportId: Long): Flow<List<QualityIssueEntity>> = flowOf(emptyList())
        override fun observeUnresolvedCritical(): Flow<List<QualityIssueEntity>> = flowOf(emptyList())
        override suspend fun resolveIssue(issueId: Long) {}
    }

    private class FakeIndicatorDefinitionRepo(private val defined: Set<IndicatorType>) : IndicatorDefinitionRepository {
        override suspend fun define(definition: IndicatorDefinitionEntity) = 1L
        override suspend fun getById(id: Long): IndicatorDefinitionEntity? = null
        override suspend fun getLatestByName(name: String): IndicatorDefinitionEntity? {
            val type = IndicatorType.entries.firstOrNull { "${it.name}_DEFAULT" == name } ?: return null
            return if (type in defined) IndicatorDefinitionEntity(indicatorDefId = type.ordinal.toLong() + 1, name = name, indicatorType = type, paramsJson = "{}", outputLabels = "value") else null
        }
        override fun observeByType(type: String): Flow<List<IndicatorDefinitionEntity>> = flowOf(emptyList())
        override fun observeActive(): Flow<List<IndicatorDefinitionEntity>> = flowOf(emptyList())
        override suspend fun createNewVersion(definition: IndicatorDefinitionEntity, newParamsJson: String) = 1L
    }

    private class FakeIndicatorValueRepo(private val hasValuesForDefIds: Set<Long>) : IndicatorValueRepository {
        override suspend fun storeValues(values: List<IndicatorValueEntity>) = emptyList<Long>()
        override fun observeRange(indicatorDefId: Long, instrumentId: Long, timeframe: String, fromTs: Long, toTs: Long): Flow<List<IndicatorValueEntity>> = flowOf(emptyList())
        override suspend fun getLatest(indicatorDefId: Long, instrumentId: Long, timeframe: String, limit: Int) = emptyList<IndicatorValueEntity>()
        override suspend fun countInRange(indicatorDefId: Long, instrumentId: Long, timeframe: String, fromTs: Long, toTs: Long) =
            if (indicatorDefId in hasValuesForDefIds) 100 else 0
        override suspend fun discardVersion(indicatorDefId: Long, instrumentId: Long, timeframe: String, version: Int) {}
    }

    private class FakeOptimizationRepo : OptimizationRepository {
        override suspend fun createJob(componentId: String, algorithmId: String, instrumentId: Long, timeframeValue: String, periodStart: Long, periodEnd: Long, budget: Int, randomSeed: Long?) = throw NotImplementedError()
        override suspend fun getJob(jobRowId: Long): OptimizationJobEntity? = null
        override fun observeJob(jobRowId: Long): Flow<OptimizationJobEntity?> = flowOf(null)
        override fun observeAllJobs(): Flow<List<OptimizationJobEntity>> = flowOf(emptyList())
        override suspend fun findResumableJobs() = emptyList<OptimizationJobEntity>()
        override suspend fun pendingCombinations(jobRowId: Long) = emptyList<OptimizationCombinationEntity>()
        override suspend fun markJobRunning(jobRowId: Long) {}
        override suspend fun markJobCancelled(jobRowId: Long) {}
        override suspend fun markJobFailed(jobRowId: Long, errorMessage: String) {}
        override suspend fun markCombinationEvaluated(combinationRowId: Long, backtestRunRowId: Long?, backtestResultRowId: Long?) {}
        override suspend fun markCombinationFailed(combinationRowId: Long, errorMessage: String) {}
        override suspend fun linkBacktest(jobRowId: Long, backtestRowId: Long) {}
        override suspend fun rankedCombinations(jobRowId: Long) = emptyList<OptimizationCombinationEntity>()
        override suspend fun rankCombinations(jobRowId: Long, rankedRowIdsBestFirst: List<Long>) {}
        override suspend fun completedCombinations(jobRowId: Long) = emptyList<OptimizationCombinationEntity>()
    }

    private class FakeBacktestRepo : BacktestRepository {
        override suspend fun createBacktest(backtest: BacktestEntity) = 1L
        override suspend fun updateBacktest(backtest: BacktestEntity) {}
        override suspend fun getBacktest(rowId: Long): BacktestEntity? = null
        override fun observeBacktestsByStrategy(strategyId: String): Flow<List<BacktestEntity>> = flowOf(emptyList())
        override fun observeAllBacktests(): Flow<List<BacktestEntity>> = flowOf(emptyList())
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

    private class FakeIngestionJobRepo(private val lastSuccessful: IngestionJobEntity? = null) : IngestionJobRepository {
        override suspend fun createJob(job: IngestionJobEntity) = 1L
        override suspend fun getJob(jobId: Long): IngestionJobEntity? = null
        override fun observeJob(jobId: Long): Flow<IngestionJobEntity?> = flowOf(null)
        override fun observeActiveJobs(): Flow<List<IngestionJobEntity>> = flowOf(emptyList())
        override fun observeByInstrument(instrumentId: Long): Flow<List<IngestionJobEntity>> = flowOf(emptyList())
        override fun observeRecent(limit: Int): Flow<List<IngestionJobEntity>> = flowOf(emptyList())
        override fun observeActiveCount(): Flow<Int> = flowOf(0)
        override suspend fun getDueForRetry(now: Long) = emptyList<IngestionJobEntity>()
        override suspend fun getLastSuccessful(instrumentId: Long, timeframe: String) = lastSuccessful
        override suspend fun markStarted(jobId: Long, actor: String) {}
        override suspend fun recordProgress(jobId: Long, rowsFetched: Long, rowsInserted: Long, rowsSkipped: Long, progressPercent: Double, message: String?) {}
        override suspend fun markSucceeded(jobId: Long, actor: String) {}
        override suspend fun recordFailure(jobId: Long, error: String, nextRetryAt: Long?, actor: String) {}
        override suspend fun cancel(jobId: Long, reason: String, actor: String) {}
        override suspend fun appendLog(jobId: Long, attemptNumber: Int, eventType: IngestionEventType, message: String, detailsJson: String?) = 1L
        override fun observeLogs(jobId: Long): Flow<List<IngestionJobLogEntity>> = flowOf(emptyList())
        override suspend fun getErrorLogs(jobId: Long) = emptyList<IngestionJobLogEntity>()
    }

    private fun candle(ts: Long) = HistoricalCandleEntity(instrumentId = 1L, timeframe = Timeframe.D1, timestamp = ts, open = 1.0, high = 1.0, low = 1.0, close = 1.0, volume = 1L, source = CandleSource.HISTORICAL_IMPORT)

    @Test
    fun `an instrument with zero stored candles is never ready for optimization, regardless of anything else`() = runTest {
        val engine = DatasetStatusEngine(
            FakeCandleRepo(emptyList()), FakeQualityRepo(null), FakeIndicatorDefinitionRepo(IndicatorType.entries.toSet()),
            FakeIndicatorValueRepo((1L..26L).toSet()), FakeOptimizationRepo(), FakeBacktestRepo(), FakeIngestionJobRepo(),
        )

        val status = engine.statusFor(1L, Timeframe.D1)

        assertFalse(status.readyForOptimization)
        assertEquals(0, status.importedCandleCount)
        assertEquals(ValidationStatus.NOT_VALIDATED, status.validationStatus)
        assertEquals("NEVER_IMPORTED", status.importStatus)
    }

    @Test
    fun `candles exist but validation has not passed and indicators are incomplete -- not ready`() = runTest {
        val lowQualityReport = CandleQualityReportEntity(instrumentId = 1L, timeframe = "1d", periodStart = 0L, periodEnd = 100L, expectedCandleCount = 100, actualCandleCount = 50, qualityScore = 0.5)
        val engine = DatasetStatusEngine(
            FakeCandleRepo(listOf(candle(0L), candle(86_400_000L))),
            FakeQualityRepo(lowQualityReport),
            FakeIndicatorDefinitionRepo(setOf(IndicatorType.SMA, IndicatorType.EMA)),
            FakeIndicatorValueRepo(setOf(1L)),
            FakeOptimizationRepo(), FakeBacktestRepo(), FakeIngestionJobRepo(),
        )

        val status = engine.statusFor(1L, Timeframe.D1)

        assertFalse(status.readyForOptimization)
        assertEquals(ValidationStatus.FAIL, status.validationStatus)
        assertTrue(status.indicatorTypesCompleted < IndicatorType.entries.size)
    }

    @Test
    fun `candles exist, validation passes, and all 26 indicators are complete -- ready for optimization`() = runTest {
        val goodReport = CandleQualityReportEntity(instrumentId = 1L, timeframe = "1d", periodStart = 0L, periodEnd = 100L, expectedCandleCount = 2, actualCandleCount = 2, qualityScore = 1.0)
        val allDefIds = (1L..IndicatorType.entries.size.toLong()).toSet()
        val engine = DatasetStatusEngine(
            FakeCandleRepo(listOf(candle(0L), candle(86_400_000L))),
            FakeQualityRepo(goodReport),
            FakeIndicatorDefinitionRepo(IndicatorType.entries.toSet()),
            FakeIndicatorValueRepo(allDefIds),
            FakeOptimizationRepo(), FakeBacktestRepo(), FakeIngestionJobRepo(),
        )

        val status = engine.statusFor(1L, Timeframe.D1)

        assertTrue("all preconditions are real and met -- this must be ready", status.readyForOptimization)
        assertEquals(ValidationStatus.PASS, status.validationStatus)
        assertEquals(IndicatorType.entries.size, status.indicatorTypesCompleted)
        assertEquals(100.0, status.indicatorCompletionPercent, 0.0001)
    }

    @Test
    fun `a quality score just under the pass threshold is FAIL, not PASS`() = runTest {
        val borderlineReport = CandleQualityReportEntity(instrumentId = 1L, timeframe = "1d", periodStart = 0L, periodEnd = 100L, expectedCandleCount = 1, actualCandleCount = 1, qualityScore = 0.94)
        val engine = DatasetStatusEngine(
            FakeCandleRepo(listOf(candle(0L))), FakeQualityRepo(borderlineReport), FakeIndicatorDefinitionRepo(emptySet()),
            FakeIndicatorValueRepo(emptySet()), FakeOptimizationRepo(), FakeBacktestRepo(), FakeIngestionJobRepo(),
        )

        val status = engine.statusFor(1L, Timeframe.D1)

        assertEquals(ValidationStatus.FAIL, status.validationStatus)
    }

    @Test
    fun `import status reflects the real last successful job, not a guess`() = runTest {
        val job = IngestionJobEntity(jobId = 5L, providerId = 1L, instrumentId = 1L, timeframe = "1d", jobType = IngestionJobType.FULL_BACKFILL, status = IngestionJobStatus.SUCCEEDED, completedAt = 123456L)
        val engine = DatasetStatusEngine(
            FakeCandleRepo(listOf(candle(0L))), FakeQualityRepo(null), FakeIndicatorDefinitionRepo(emptySet()),
            FakeIndicatorValueRepo(emptySet()), FakeOptimizationRepo(), FakeBacktestRepo(), FakeIngestionJobRepo(job),
        )

        val status = engine.statusFor(1L, Timeframe.D1)

        assertEquals("SUCCEEDED", status.importStatus)
        assertEquals(123456L, status.lastImportTime)
    }
}

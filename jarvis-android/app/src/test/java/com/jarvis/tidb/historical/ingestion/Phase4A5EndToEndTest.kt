package com.jarvis.tidb.historical.ingestion

import com.jarvis.tidb.core.entity.HistoricalCandleEntity
import com.jarvis.tidb.core.entity.Timeframe
import com.jarvis.tidb.core.repository.HistoricalCandleRepository
import com.jarvis.tidb.historical.indicator.calc.AdxCalculator
import com.jarvis.tidb.historical.indicator.calc.AroonCalculator
import com.jarvis.tidb.historical.indicator.calc.AtrCalculator
import com.jarvis.tidb.historical.indicator.calc.BollingerBandsCalculator
import com.jarvis.tidb.historical.indicator.calc.CciCalculator
import com.jarvis.tidb.historical.indicator.calc.CmfCalculator
import com.jarvis.tidb.historical.indicator.calc.DmiCalculator
import com.jarvis.tidb.historical.indicator.calc.DonchianChannelCalculator
import com.jarvis.tidb.historical.indicator.calc.EmaCalculator
import com.jarvis.tidb.historical.indicator.calc.IchimokuCalculator
import com.jarvis.tidb.historical.indicator.calc.IndicatorCalculatorRegistry
import com.jarvis.tidb.historical.indicator.calc.KeltnerChannelCalculator
import com.jarvis.tidb.historical.indicator.calc.MacdCalculator
import com.jarvis.tidb.historical.indicator.calc.MfiCalculator
import com.jarvis.tidb.historical.indicator.calc.MomentumCalculator
import com.jarvis.tidb.historical.indicator.calc.ObvCalculator
import com.jarvis.tidb.historical.indicator.calc.ParabolicSarCalculator
import com.jarvis.tidb.historical.indicator.calc.RocCalculator
import com.jarvis.tidb.historical.indicator.calc.RsiCalculator
import com.jarvis.tidb.historical.indicator.calc.SmaCalculator
import com.jarvis.tidb.historical.indicator.calc.StochasticCalculator
import com.jarvis.tidb.historical.indicator.calc.SupertrendCalculator
import com.jarvis.tidb.historical.indicator.calc.TrixCalculator
import com.jarvis.tidb.historical.indicator.calc.VwapCalculator
import com.jarvis.tidb.historical.indicator.calc.VwmaCalculator
import com.jarvis.tidb.historical.indicator.calc.WilliamsRCalculator
import com.jarvis.tidb.historical.indicator.calc.WmaCalculator
import com.jarvis.tidb.historical.indicator.entity.IndicatorComputationRunEntity
import com.jarvis.tidb.historical.indicator.entity.IndicatorDefinitionEntity
import com.jarvis.tidb.historical.indicator.entity.IndicatorType
import com.jarvis.tidb.historical.indicator.entity.IndicatorValueEntity
import com.jarvis.tidb.historical.indicator.repository.IndicatorComputationRunRepository
import com.jarvis.tidb.historical.indicator.repository.IndicatorDefinitionRepository
import com.jarvis.tidb.historical.indicator.repository.IndicatorValueRepository
import com.jarvis.tidb.historical.ingestion.datasource.CsvDataSourceProvider
import com.jarvis.tidb.historical.ingestion.datasource.CsvPathResolver
import com.jarvis.tidb.historical.ingestion.entity.IngestionCheckpointEntity
import com.jarvis.tidb.historical.ingestion.entity.IngestionEventType
import com.jarvis.tidb.historical.ingestion.entity.IngestionJobEntity
import com.jarvis.tidb.historical.ingestion.entity.IngestionJobLogEntity
import com.jarvis.tidb.historical.ingestion.entity.IngestionJobStatus
import com.jarvis.tidb.historical.ingestion.pipeline.HistoricalDataImportPipeline
import com.jarvis.tidb.historical.ingestion.repository.IngestionCheckpointRepository
import com.jarvis.tidb.historical.ingestion.repository.IngestionJobRepository
import com.jarvis.tidb.historical.ingestion.status.DatasetStatusEngine
import com.jarvis.tidb.historical.ingestion.status.ValidationStatus
import com.jarvis.tidb.historical.quality.entity.CandleQualityReportEntity
import com.jarvis.tidb.historical.quality.entity.QualityIssueEntity
import com.jarvis.tidb.historical.quality.repository.QualityReportRepository
import com.jarvis.tidb.analytics.entity.BacktestConfigurationEntity
import com.jarvis.tidb.analytics.entity.BacktestEntity
import com.jarvis.tidb.analytics.entity.BacktestResultEntity
import com.jarvis.tidb.analytics.entity.BacktestRunEntity
import com.jarvis.tidb.analytics.entity.BacktestRunWithDetails
import com.jarvis.tidb.analytics.entity.BacktestStatus
import com.jarvis.tidb.analytics.entity.BacktestTradeEntity
import com.jarvis.tidb.analytics.repository.BacktestRepository
import com.jarvis.tidb.optimization.entity.OptimizationCombinationEntity
import com.jarvis.tidb.optimization.entity.OptimizationJobEntity
import com.jarvis.tidb.optimization.repository.OptimizationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * "Phase 4A.5 -- Natural Gas Historical Dataset Validation." Exercises the FULL, real 8-stage
 * chain (Provider -> Parser -> Validate -> Store -> Indicators -> Dataset Status -> Source
 * Verification -> READY_FOR_OPTIMIZATION) with a real `CsvDataSourceProvider` reading a real
 * file, a real `HistoricalDataImportPipeline`, and a real `DatasetStatusEngine` -- only the
 * repository layer underneath is faked (no Robolectric in this environment).
 *
 * The CSV is a clearly-labeled SYNTHETIC 300-day OHLCV sample
 * (`src/test/resources/naturalgas_synthetic_sample.csv`, seeded, deterministic) -- no real Natural
 * Gas market dataset is available in this environment (no network access, none bundled in the
 * repo). This proves the PIPELINE is correct; it is not, and should never be read as, validating
 * real market data. Swapping in a real CSV export later requires no code change.
 */
class Phase4A5EndToEndTest {

    private class FakeCandleRepo : HistoricalCandleRepository {
        val stored = mutableListOf<HistoricalCandleEntity>()
        override suspend fun insert(candle: HistoricalCandleEntity): Long { stored += candle; return stored.size.toLong() }
        override suspend fun insertAll(candles: List<HistoricalCandleEntity>): List<Long> { stored += candles; return candles.indices.map { it.toLong() } }
        override fun observeRange(instrumentId: Long, timeframe: Timeframe, fromMillis: Long, toMillis: Long): Flow<List<HistoricalCandleEntity>> =
            flowOf(stored.filter { it.instrumentId == instrumentId && it.timeframe == timeframe })
        override suspend fun getLatest(instrumentId: Long, timeframe: Timeframe, limit: Int) = stored.takeLast(limit)
        override suspend fun softDeleteByImportBatch(importBatchId: String) {}
    }

    private class FakeDefinitionRepo : IndicatorDefinitionRepository {
        private val byName = mutableMapOf<String, IndicatorDefinitionEntity>()
        private val seq = AtomicLong(1)
        override suspend fun define(definition: IndicatorDefinitionEntity): Long {
            val id = seq.getAndIncrement(); byName[definition.name] = definition.copy(indicatorDefId = id); return id
        }
        override suspend fun getById(id: Long) = byName.values.firstOrNull { it.indicatorDefId == id }
        override suspend fun getLatestByName(name: String) = byName[name]
        override fun observeByType(type: String): Flow<List<IndicatorDefinitionEntity>> = flowOf(emptyList())
        override fun observeActive(): Flow<List<IndicatorDefinitionEntity>> = flowOf(byName.values.toList())
        override suspend fun createNewVersion(definition: IndicatorDefinitionEntity, newParamsJson: String) = define(definition)
    }

    private class FakeValueRepo : IndicatorValueRepository {
        val stored = mutableListOf<IndicatorValueEntity>()
        override suspend fun storeValues(values: List<IndicatorValueEntity>): List<Long> { stored += values; return values.indices.map { it.toLong() } }
        override fun observeRange(indicatorDefId: Long, instrumentId: Long, timeframe: String, fromTs: Long, toTs: Long): Flow<List<IndicatorValueEntity>> = flowOf(emptyList())
        override suspend fun getLatest(indicatorDefId: Long, instrumentId: Long, timeframe: String, limit: Int) = emptyList<IndicatorValueEntity>()
        override suspend fun countInRange(indicatorDefId: Long, instrumentId: Long, timeframe: String, fromTs: Long, toTs: Long) =
            stored.count { it.indicatorDefId == indicatorDefId && it.instrumentId == instrumentId }
        override suspend fun discardVersion(indicatorDefId: Long, instrumentId: Long, timeframe: String, version: Int) {}
    }

    private class FakeRunRepo : IndicatorComputationRunRepository {
        var completed = 0
        override suspend fun startRun(run: IndicatorComputationRunEntity) = 1L
        override suspend fun completeRun(runId: Long, rowsComputed: Long) { completed++ }
        override suspend fun failRun(runId: Long, error: String) {}
        override fun observeActive(): Flow<List<IndicatorComputationRunEntity>> = flowOf(emptyList())
        override suspend fun getLastRun(indicatorDefId: Long, instrumentId: Long, timeframe: String): IndicatorComputationRunEntity? = null
    }

    private class FakeQualityRepo : QualityReportRepository {
        var lastReport: CandleQualityReportEntity? = null
        override suspend fun publishReport(report: CandleQualityReportEntity, issues: List<QualityIssueEntity>): Long { lastReport = report; return 1L }
        override suspend fun getLatest(instrumentId: Long, timeframe: String) = lastReport
        override fun observeByInstrument(instrumentId: Long): Flow<List<CandleQualityReportEntity>> = flowOf(emptyList())
        override fun observeBelowThreshold(threshold: Double): Flow<List<CandleQualityReportEntity>> = flowOf(emptyList())
        override fun observeIssues(reportId: Long): Flow<List<QualityIssueEntity>> = flowOf(emptyList())
        override fun observeUnresolvedCritical(): Flow<List<QualityIssueEntity>> = flowOf(emptyList())
        override suspend fun resolveIssue(issueId: Long) {}
    }

    private class FakeJobRepo : IngestionJobRepository {
        val jobs = mutableMapOf<Long, IngestionJobEntity>()
        private val seq = AtomicLong(1)
        override suspend fun createJob(job: IngestionJobEntity): Long { val id = seq.getAndIncrement(); jobs[id] = job.copy(jobId = id); return id }
        override suspend fun getJob(jobId: Long) = jobs[jobId]
        override fun observeJob(jobId: Long): Flow<IngestionJobEntity?> = flowOf(jobs[jobId])
        override fun observeActiveJobs(): Flow<List<IngestionJobEntity>> = flowOf(emptyList())
        override fun observeByInstrument(instrumentId: Long): Flow<List<IngestionJobEntity>> = flowOf(emptyList())
        override fun observeRecent(limit: Int): Flow<List<IngestionJobEntity>> = flowOf(emptyList())
        override fun observeActiveCount(): Flow<Int> = flowOf(0)
        override suspend fun getDueForRetry(now: Long) = emptyList<IngestionJobEntity>()
        override suspend fun getLastSuccessful(instrumentId: Long, timeframe: String) = jobs.values.lastOrNull { it.status == IngestionJobStatus.SUCCEEDED }
        override suspend fun markStarted(jobId: Long, actor: String) { jobs[jobId] = jobs.getValue(jobId).copy(status = IngestionJobStatus.RUNNING) }
        override suspend fun recordProgress(jobId: Long, rowsFetched: Long, rowsInserted: Long, rowsSkipped: Long, progressPercent: Double, message: String?) {}
        override suspend fun markSucceeded(jobId: Long, actor: String) { jobs[jobId] = jobs.getValue(jobId).copy(status = IngestionJobStatus.SUCCEEDED, completedAt = 999L) }
        override suspend fun recordFailure(jobId: Long, error: String, nextRetryAt: Long?, actor: String) { jobs[jobId] = jobs.getValue(jobId).copy(status = IngestionJobStatus.FAILED, lastError = error) }
        override suspend fun cancel(jobId: Long, reason: String, actor: String) {}
        override suspend fun appendLog(jobId: Long, attemptNumber: Int, eventType: IngestionEventType, message: String, detailsJson: String?) = 1L
        override fun observeLogs(jobId: Long): Flow<List<IngestionJobLogEntity>> = flowOf(emptyList())
        override suspend fun getErrorLogs(jobId: Long) = emptyList<IngestionJobLogEntity>()
    }

    private class FakeCheckpointRepo : IngestionCheckpointRepository {
        override suspend fun getCheckpoint(providerId: Long, instrumentId: Long, timeframe: String): IngestionCheckpointEntity? = null
        override suspend fun advanceCheckpoint(providerId: Long, instrumentId: Long, timeframe: String, lastSuccessfulTimestamp: Long, cursorToken: String?) {}
        override fun observeForInstrument(instrumentId: Long): Flow<List<IngestionCheckpointEntity>> = flowOf(emptyList())
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

    private fun allCalculators() = setOf(
        SmaCalculator(), EmaCalculator(), WmaCalculator(), VwmaCalculator(), VwapCalculator(),
        RsiCalculator(), MacdCalculator(), CciCalculator(), RocCalculator(), MomentumCalculator(),
        WilliamsRCalculator(), StochasticCalculator(), AtrCalculator(), BollingerBandsCalculator(),
        SupertrendCalculator(), KeltnerChannelCalculator(), DonchianChannelCalculator(),
        AdxCalculator(), DmiCalculator(), AroonCalculator(), TrixCalculator(),
        ParabolicSarCalculator(), IchimokuCalculator(), ObvCalculator(), CmfCalculator(), MfiCalculator(),
    )

    @Test
    fun `full pipeline against the synthetic Natural Gas sample -- import through READY_FOR_OPTIMIZATION`() = runTest {
        val csvFile = File("src/test/resources/naturalgas_synthetic_sample.csv")
        assertTrue("synthetic sample CSV must exist", csvFile.exists())

        val candleRepo = FakeCandleRepo()
        val definitions = FakeDefinitionRepo()
        val values = FakeValueRepo()
        val runs = FakeRunRepo()
        val quality = FakeQualityRepo()
        val jobs = FakeJobRepo()
        val checkpoints = FakeCheckpointRepo()
        val optimizationRepo = FakeOptimizationRepo()
        val backtestRepo = FakeBacktestRepo()

        val registry = IndicatorCalculatorRegistry(allCalculators())
        val provider = CsvDataSourceProvider(CsvPathResolver { _, _ -> csvFile.absolutePath })
        val pipeline = HistoricalDataImportPipeline(candleRepo, registry, definitions, values, runs, quality, jobs, checkpoints)
        val statusEngine = DatasetStatusEngine(candleRepo, quality, definitions, values, optimizationRepo, backtestRepo, jobs)

        val naturalGasId = 1L
        val timeframe = Timeframe.D1

        val result = pipeline.runImport(provider, providerId = 1L, instrumentId = naturalGasId, timeframe = timeframe, periodStart = 0L, periodEnd = Long.MAX_VALUE)

        assertTrue("import must succeed", result.succeeded)
        assertEquals(300, result.candlesFetched)
        assertEquals(300, result.candlesStored)
        assertEquals(300, candleRepo.stored.size)

        assertNotNull("a quality report must have been published", quality.lastReport)
        assertEquals(0, quality.lastReport!!.duplicateCount)
        assertEquals(0, quality.lastReport!!.ohlcViolationCount)

        assertEquals(26, runs.completed)
        assertTrue("real indicator values must be stored, not zero", values.stored.isNotEmpty())
        for (type in IndicatorType.entries) {
            assertNotNull("a definition must exist for $type", definitions.getLatestByName("${type.name}_DEFAULT"))
        }

        val status = statusEngine.statusFor(naturalGasId, timeframe)
        assertEquals(300, status.importedCandleCount)
        assertEquals(ValidationStatus.PASS, status.validationStatus)
        assertEquals(26, status.indicatorTypesCompleted)
        assertEquals(100.0, status.indicatorCompletionPercent, 0.0001)
        assertEquals("SUCCEEDED", status.importStatus)
        assertNotNull(status.earliestCandle)
        assertNotNull(status.latestCandle)

        assertTrue("all real preconditions are met -- must be ready", status.readyForOptimization)
    }

    @Test
    fun `READY_FOR_OPTIMIZATION correctly flips to false when indicators are incomplete -- computed live, not cached`() = runTest {
        val csvFile = File("src/test/resources/naturalgas_synthetic_sample.csv")
        val candleRepo = FakeCandleRepo()
        val definitions = FakeDefinitionRepo()
        val values = FakeValueRepo()
        val quality = FakeQualityRepo()
        val jobs = FakeJobRepo()

        val partialRegistry = IndicatorCalculatorRegistry(setOf(SmaCalculator(), EmaCalculator(), RsiCalculator()))
        val provider = CsvDataSourceProvider(CsvPathResolver { _, _ -> csvFile.absolutePath })
        val pipeline = HistoricalDataImportPipeline(candleRepo, partialRegistry, definitions, values, FakeRunRepo(), quality, jobs, FakeCheckpointRepo())
        val statusEngine = DatasetStatusEngine(candleRepo, quality, definitions, values, FakeOptimizationRepo(), FakeBacktestRepo(), jobs)

        pipeline.runImport(provider, providerId = 1L, instrumentId = 2L, timeframe = Timeframe.D1, periodStart = 0L, periodEnd = Long.MAX_VALUE)
        val status = statusEngine.statusFor(2L, Timeframe.D1)

        assertEquals(ValidationStatus.PASS, status.validationStatus)
        assertTrue(status.indicatorTypesCompleted < 26)
        assertFalse("must NOT be ready -- indicators incomplete, live-computed, never cached as true", status.readyForOptimization)
    }
}

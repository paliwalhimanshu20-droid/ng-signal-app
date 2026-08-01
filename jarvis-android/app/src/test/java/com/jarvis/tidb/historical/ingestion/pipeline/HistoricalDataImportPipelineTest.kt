package com.jarvis.tidb.historical.ingestion.pipeline

import com.jarvis.tidb.core.entity.HistoricalCandleEntity
import com.jarvis.tidb.core.entity.Timeframe
import com.jarvis.tidb.core.repository.HistoricalCandleRepository
import com.jarvis.tidb.historical.indicator.calc.IndicatorCalculatorRegistry
import com.jarvis.tidb.historical.indicator.calc.SmaCalculator
import com.jarvis.tidb.historical.indicator.entity.IndicatorComputationRunEntity
import com.jarvis.tidb.historical.indicator.entity.IndicatorDefinitionEntity
import com.jarvis.tidb.historical.indicator.entity.IndicatorValueEntity
import com.jarvis.tidb.historical.indicator.repository.IndicatorComputationRunRepository
import com.jarvis.tidb.historical.indicator.repository.IndicatorDefinitionRepository
import com.jarvis.tidb.historical.indicator.repository.IndicatorValueRepository
import com.jarvis.tidb.historical.ingestion.datasource.DataSourceProvider
import com.jarvis.tidb.historical.ingestion.datasource.RawCandle
import com.jarvis.tidb.historical.ingestion.entity.IngestionCheckpointEntity
import com.jarvis.tidb.historical.ingestion.entity.IngestionEventType
import com.jarvis.tidb.historical.ingestion.entity.IngestionJobEntity
import com.jarvis.tidb.historical.ingestion.entity.IngestionJobLogEntity
import com.jarvis.tidb.historical.ingestion.entity.IngestionJobStatus
import com.jarvis.tidb.historical.ingestion.repository.IngestionCheckpointRepository
import com.jarvis.tidb.historical.ingestion.repository.IngestionJobRepository
import com.jarvis.tidb.historical.quality.entity.CandleQualityReportEntity
import com.jarvis.tidb.historical.quality.entity.QualityIssueEntity
import com.jarvis.tidb.historical.quality.repository.QualityReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

/**
 * "Phase 4A, Section 2+5+6+8." Real orchestration end-to-end against in-memory fakes -- verifies
 * candles actually land in [FakeHistoricalCandleRepository], a real quality report is actually
 * published, the ingestion job actually transitions to SUCCEEDED, and indicators actually get
 * computed and stored -- not just that the method returns without throwing.
 */
class HistoricalDataImportPipelineTest {

    private class FakeDataSourceProvider(private val candles: List<RawCandle>) : DataSourceProvider {
        override val providerCode = "TEST"
        override suspend fun fetchCandles(instrumentId: Long, timeframe: Timeframe, from: Long, to: Long) = candles
    }

    private class FakeHistoricalCandleRepository : HistoricalCandleRepository {
        val stored = mutableListOf<HistoricalCandleEntity>()
        override suspend fun insert(candle: HistoricalCandleEntity): Long { stored += candle; return stored.size.toLong() }
        override suspend fun insertAll(candles: List<HistoricalCandleEntity>): List<Long> { stored += candles; return candles.indices.map { it.toLong() } }
        override fun observeRange(instrumentId: Long, timeframe: Timeframe, fromMillis: Long, toMillis: Long): Flow<List<HistoricalCandleEntity>> =
            flowOf(stored.filter { it.instrumentId == instrumentId && it.timeframe == timeframe })
        override suspend fun getLatest(instrumentId: Long, timeframe: Timeframe, limit: Int) = stored.takeLast(limit)
        override suspend fun softDeleteByImportBatch(importBatchId: String) {}
    }

    private class FakeIndicatorDefinitionRepository : IndicatorDefinitionRepository {
        private val byName = mutableMapOf<String, IndicatorDefinitionEntity>()
        private val seq = AtomicLong(1)
        override suspend fun define(definition: IndicatorDefinitionEntity): Long {
            val id = seq.getAndIncrement()
            byName[definition.name] = definition.copy(indicatorDefId = id)
            return id
        }
        override suspend fun getById(id: Long) = byName.values.firstOrNull { it.indicatorDefId == id }
        override suspend fun getLatestByName(name: String) = byName[name]
        override fun observeByType(type: String): Flow<List<IndicatorDefinitionEntity>> = flowOf(byName.values.filter { it.indicatorType.value == type })
        override fun observeActive(): Flow<List<IndicatorDefinitionEntity>> = flowOf(byName.values.toList())
        override suspend fun createNewVersion(definition: IndicatorDefinitionEntity, newParamsJson: String) = define(definition)
    }

    private class FakeIndicatorValueRepository : IndicatorValueRepository {
        val stored = mutableListOf<IndicatorValueEntity>()
        override suspend fun storeValues(values: List<IndicatorValueEntity>): List<Long> { stored += values; return values.indices.map { it.toLong() } }
        override fun observeRange(indicatorDefId: Long, instrumentId: Long, timeframe: String, fromTs: Long, toTs: Long): Flow<List<IndicatorValueEntity>> = flowOf(emptyList())
        override suspend fun getLatest(indicatorDefId: Long, instrumentId: Long, timeframe: String, limit: Int) = emptyList<IndicatorValueEntity>()
        override suspend fun countInRange(indicatorDefId: Long, instrumentId: Long, timeframe: String, fromTs: Long, toTs: Long) = 0
        override suspend fun discardVersion(indicatorDefId: Long, instrumentId: Long, timeframe: String, version: Int) {}
    }

    private class FakeIndicatorComputationRunRepository : IndicatorComputationRunRepository {
        var startedCount = 0
        var completedCount = 0
        var failedCount = 0
        override suspend fun startRun(run: IndicatorComputationRunEntity): Long { startedCount++; return startedCount.toLong() }
        override suspend fun completeRun(runId: Long, rowsComputed: Long) { completedCount++ }
        override suspend fun failRun(runId: Long, error: String) { failedCount++ }
        override fun observeActive(): Flow<List<IndicatorComputationRunEntity>> = flowOf(emptyList())
        override suspend fun getLastRun(indicatorDefId: Long, instrumentId: Long, timeframe: String): IndicatorComputationRunEntity? = null
    }

    private class FakeQualityReportRepository : QualityReportRepository {
        var publishedReports = 0
        var lastReport: CandleQualityReportEntity? = null
        override suspend fun publishReport(report: CandleQualityReportEntity, issues: List<QualityIssueEntity>): Long {
            publishedReports++
            lastReport = report
            return publishedReports.toLong()
        }
        override suspend fun getLatest(instrumentId: Long, timeframe: String) = lastReport
        override fun observeByInstrument(instrumentId: Long): Flow<List<CandleQualityReportEntity>> = flowOf(emptyList())
        override fun observeBelowThreshold(threshold: Double): Flow<List<CandleQualityReportEntity>> = flowOf(emptyList())
        override fun observeIssues(reportId: Long): Flow<List<QualityIssueEntity>> = flowOf(emptyList())
        override fun observeUnresolvedCritical(): Flow<List<QualityIssueEntity>> = flowOf(emptyList())
        override suspend fun resolveIssue(issueId: Long) {}
    }

    private class FakeIngestionJobRepository : IngestionJobRepository {
        val jobs = mutableMapOf<Long, IngestionJobEntity>()
        private val seq = AtomicLong(1)
        override suspend fun createJob(job: IngestionJobEntity): Long {
            val id = seq.getAndIncrement()
            jobs[id] = job.copy(jobId = id)
            return id
        }
        override suspend fun getJob(jobId: Long) = jobs[jobId]
        override fun observeJob(jobId: Long): Flow<IngestionJobEntity?> = flowOf(jobs[jobId])
        override fun observeActiveJobs(): Flow<List<IngestionJobEntity>> = flowOf(emptyList())
        override fun observeByInstrument(instrumentId: Long): Flow<List<IngestionJobEntity>> = flowOf(emptyList())
        override fun observeRecent(limit: Int): Flow<List<IngestionJobEntity>> = flowOf(emptyList())
        override fun observeActiveCount(): Flow<Int> = flowOf(0)
        override suspend fun getDueForRetry(now: Long) = emptyList<IngestionJobEntity>()
        override suspend fun getLastSuccessful(instrumentId: Long, timeframe: String): IngestionJobEntity? = null
        override suspend fun markStarted(jobId: Long, actor: String) {
            jobs[jobId] = jobs.getValue(jobId).copy(status = IngestionJobStatus.RUNNING)
        }
        override suspend fun recordProgress(jobId: Long, rowsFetched: Long, rowsInserted: Long, rowsSkipped: Long, progressPercent: Double, message: String?) {
            jobs[jobId] = jobs.getValue(jobId).copy(rowsFetched = rowsFetched, rowsInserted = rowsInserted, rowsSkipped = rowsSkipped, progressPercent = progressPercent)
        }
        override suspend fun markSucceeded(jobId: Long, actor: String) {
            jobs[jobId] = jobs.getValue(jobId).copy(status = IngestionJobStatus.SUCCEEDED)
        }
        override suspend fun recordFailure(jobId: Long, error: String, nextRetryAt: Long?, actor: String) {
            jobs[jobId] = jobs.getValue(jobId).copy(status = IngestionJobStatus.FAILED, lastError = error)
        }
        override suspend fun cancel(jobId: Long, reason: String, actor: String) {}
        override suspend fun appendLog(jobId: Long, attemptNumber: Int, eventType: IngestionEventType, message: String, detailsJson: String?) = 1L
        override fun observeLogs(jobId: Long): Flow<List<IngestionJobLogEntity>> = flowOf(emptyList())
        override suspend fun getErrorLogs(jobId: Long) = emptyList<IngestionJobLogEntity>()
    }

    private class FakeIngestionCheckpointRepository : IngestionCheckpointRepository {
        var advancedTo: Long? = null
        override suspend fun getCheckpoint(providerId: Long, instrumentId: Long, timeframe: String): IngestionCheckpointEntity? = null
        override suspend fun advanceCheckpoint(providerId: Long, instrumentId: Long, timeframe: String, lastSuccessfulTimestamp: Long, cursorToken: String?) {
            advancedTo = lastSuccessfulTimestamp
        }
        override fun observeForInstrument(instrumentId: Long): Flow<List<IngestionCheckpointEntity>> = flowOf(emptyList())
    }

    @Test
    fun `a successful import stores candles, publishes a quality report, computes indicators, and marks the job succeeded`() = runTest {
        val candleRepo = FakeHistoricalCandleRepository()
        val definitions = FakeIndicatorDefinitionRepository()
        val values = FakeIndicatorValueRepository()
        val runs = FakeIndicatorComputationRunRepository()
        val quality = FakeQualityReportRepository()
        val jobs = FakeIngestionJobRepository()
        val checkpoints = FakeIngestionCheckpointRepository()

        val registry = IndicatorCalculatorRegistry(setOf(SmaCalculator()))
        val pipeline = HistoricalDataImportPipeline(candleRepo, registry, definitions, values, runs, quality, jobs, checkpoints)

        val rawCandles = (0 until 20).map { i -> RawCandle(timestamp = i * 60_000L, open = 100.0 + i, high = 101.0 + i, low = 99.0 + i, close = 100.5 + i, volume = 1000L) }
        val provider = FakeDataSourceProvider(rawCandles)

        val result = pipeline.runImport(provider, providerId = 1L, instrumentId = 42L, timeframe = Timeframe.M1, periodStart = 0L, periodEnd = 20 * 60_000L)

        assertTrue("import must succeed", result.succeeded)
        assertEquals(20, result.candlesStored)
        assertEquals(20, candleRepo.stored.size)
        assertEquals(1, quality.publishedReports)
        assertEquals(1.0, quality.lastReport!!.qualityScore, 0.0001)
        assertEquals(IngestionJobStatus.SUCCEEDED, jobs.jobs.getValue(result.jobId).status)
        assertEquals(19 * 60_000L, checkpoints.advancedTo)
        assertTrue("SMA should have produced real stored values, not zero", values.stored.isNotEmpty())
        assertEquals(1, runs.completedCount) // only SmaCalculator registered in this test's registry
    }

    @Test
    fun `a provider failure is recorded as a job failure, not thrown to the caller`() = runTest {
        val candleRepo = FakeHistoricalCandleRepository()
        val jobs = FakeIngestionJobRepository()
        val failingProvider = object : DataSourceProvider {
            override val providerCode = "FAILS"
            override suspend fun fetchCandles(instrumentId: Long, timeframe: Timeframe, from: Long, to: Long): List<RawCandle> =
                throw RuntimeException("simulated network failure")
        }
        val pipeline = HistoricalDataImportPipeline(
            candleRepo, IndicatorCalculatorRegistry(emptySet()), FakeIndicatorDefinitionRepository(),
            FakeIndicatorValueRepository(), FakeIndicatorComputationRunRepository(), FakeQualityReportRepository(),
            jobs, FakeIngestionCheckpointRepository(),
        )

        val result = pipeline.runImport(failingProvider, providerId = 1L, instrumentId = 1L, timeframe = Timeframe.D1, periodStart = 0L, periodEnd = 1L)

        assertTrue(!result.succeeded)
        assertEquals("simulated network failure", result.error)
        assertEquals(IngestionJobStatus.FAILED, jobs.jobs.getValue(result.jobId).status)
        assertTrue(candleRepo.stored.isEmpty())
    }
}

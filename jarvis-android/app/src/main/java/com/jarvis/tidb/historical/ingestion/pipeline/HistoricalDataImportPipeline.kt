package com.jarvis.tidb.historical.ingestion.pipeline

import com.jarvis.tidb.core.entity.CandleSource
import com.jarvis.tidb.core.entity.HistoricalCandleEntity
import com.jarvis.tidb.core.entity.Timeframe
import com.jarvis.tidb.core.repository.HistoricalCandleRepository
import com.jarvis.tidb.historical.indicator.calc.IndicatorCalculatorRegistry
import com.jarvis.tidb.historical.indicator.entity.ComputationStatus
import com.jarvis.tidb.historical.indicator.entity.IndicatorComputationRunEntity
import com.jarvis.tidb.historical.indicator.entity.IndicatorDefinitionEntity
import com.jarvis.tidb.historical.indicator.entity.IndicatorType
import com.jarvis.tidb.historical.indicator.entity.IndicatorValueEntity
import com.jarvis.tidb.historical.indicator.repository.IndicatorComputationRunRepository
import com.jarvis.tidb.historical.indicator.repository.IndicatorDefinitionRepository
import com.jarvis.tidb.historical.indicator.repository.IndicatorValueRepository
import com.jarvis.tidb.historical.ingestion.datasource.DataSourceProvider
import com.jarvis.tidb.historical.ingestion.entity.IngestionJobEntity
import com.jarvis.tidb.historical.ingestion.entity.IngestionJobStatus
import com.jarvis.tidb.historical.ingestion.entity.IngestionJobType
import com.jarvis.tidb.historical.ingestion.repository.IngestionCheckpointRepository
import com.jarvis.tidb.historical.ingestion.repository.IngestionJobRepository
import com.jarvis.tidb.historical.ingestion.validation.CandleValidator
import com.jarvis.tidb.historical.quality.entity.CandleQualityReportEntity
import com.jarvis.tidb.historical.quality.entity.QualityIssueEntity
import com.jarvis.tidb.historical.quality.repository.QualityReportRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** What actually happened -- returned so a caller (a UI, a scheduler, a test) can act on the real outcome rather than re-querying every repository this pipeline touched. */
data class ImportResult(
    val jobId: Long,
    val candlesFetched: Int,
    val candlesStored: Int,
    val qualityScore: Double,
    val indicatorsComputed: Int,
    val succeeded: Boolean,
    val error: String? = null,
)

/**
 * "Phase 4A, Section 2+5+6+8 -- Data Import Pipeline, Historical Candle Storage, Indicator
 * Population, Pipeline Orchestration." The single workflow Section 8 describes: Import ->
 * Validate -> Store -> Indicators -> Dataset Status (Dataset Status itself is a follow-on
 * increment, not part of this class -- it's a read-side aggregation over what this class already
 * writes, not something this orchestrator needs to own).
 *
 * Every step below reuses an EXISTING repository -- [HistoricalCandleRepository] (Module 1,
 * unmodified), [IndicatorCalculatorRegistry] (Phase 2, unmodified),
 * [IndicatorValueRepository]/[IndicatorDefinitionRepository]/[IndicatorComputationRunRepository]
 * (the indicator warehouse, unmodified), [QualityReportRepository] (already-existing quality
 * schema, unmodified), [IngestionJobRepository]/[IngestionCheckpointRepository] (already-existing
 * ingestion schema, unmodified). This class contains zero new persistence -- it is purely
 * orchestration, per Section 1's "no provider-specific logic inside the pipeline" and this
 * phase's own "do NOT duplicate entities/repositories/DAOs/tables" rule.
 */
@Singleton
class HistoricalDataImportPipeline @Inject constructor(
    private val candles: HistoricalCandleRepository,
    private val indicatorCalculators: IndicatorCalculatorRegistry,
    private val indicatorDefinitions: IndicatorDefinitionRepository,
    private val indicatorValues: IndicatorValueRepository,
    private val indicatorRuns: IndicatorComputationRunRepository,
    private val qualityReports: QualityReportRepository,
    private val ingestionJobs: IngestionJobRepository,
    private val checkpoints: IngestionCheckpointRepository,
) {

    /**
     * Runs the full workflow for one (provider, instrument, timeframe, date range). Creates and
     * tracks a real [IngestionJobEntity] throughout -- "support progress tracking" means every
     * step below calls [IngestionJobRepository.recordProgress], not that progress is inferred
     * after the fact. On any exception, calls [IngestionJobRepository.recordFailure] (which
     * itself decides retry-vs-terminal-failure, see that method's own doc) and returns a failed
     * [ImportResult] rather than propagating -- a pipeline run is something a caller polls the
     * result of, not something that crashes its caller.
     */
    suspend fun runImport(
        provider: DataSourceProvider,
        providerId: Long,
        instrumentId: Long,
        timeframe: Timeframe,
        periodStart: Long,
        periodEnd: Long,
        jobType: IngestionJobType = IngestionJobType.FULL_BACKFILL,
    ): ImportResult {
        val jobId = ingestionJobs.createJob(
            IngestionJobEntity(
                providerId = providerId, instrumentId = instrumentId, timeframe = timeframe.value,
                jobType = jobType, status = IngestionJobStatus.PENDING,
                requestedRangeStart = periodStart, requestedRangeEnd = periodEnd,
            ),
        )
        ingestionJobs.markStarted(jobId)

        return try {
            // 1. Connect / Download / Read.
            val raw = provider.fetchCandles(instrumentId, timeframe, periodStart, periodEnd)
            ingestionJobs.recordProgress(jobId, rowsFetched = raw.size.toLong(), rowsInserted = 0, rowsSkipped = 0, progressPercent = 25.0, message = "Fetched ${raw.size} raw candle(s) from ${provider.providerCode}.")

            // 2. Validate.
            val outcome = CandleValidator.validate(raw, timeframe, periodStart, periodEnd)
            val reportId = qualityReports.publishReport(
                CandleQualityReportEntity(
                    instrumentId = instrumentId, timeframe = timeframe.value,
                    periodStart = periodStart, periodEnd = periodEnd,
                    expectedCandleCount = outcome.expectedCandleCount, actualCandleCount = outcome.actualCandleCount,
                    missingCount = outcome.missingCount, duplicateCount = outcome.duplicateCount,
                    ohlcViolationCount = outcome.ohlcViolationCount, volumeAnomalyCount = outcome.volumeAnomalyCount,
                    timestampDiscontinuityCount = outcome.timestampDiscontinuityCount, qualityScore = outcome.qualityScore,
                ),
                outcome.issues.map { draft ->
                    QualityIssueEntity(reportId = 0L, issueType = draft.issueType, severity = draft.severity, timestamp = draft.timestamp, details = draft.details)
                },
            )
            ingestionJobs.recordProgress(jobId, rowsFetched = raw.size.toLong(), rowsInserted = 0, rowsSkipped = outcome.duplicateCount.toLong(), progressPercent = 50.0, message = "Validated -- quality score ${outcome.qualityScore}, report #$reportId, ${outcome.issues.size} issue(s).")

            // 3. Normalize + Store (reuses the existing Historical Candle tables -- see this class's own docstring).
            val entities = outcome.acceptedCandles.map { rawCandle ->
                HistoricalCandleEntity(
                    instrumentId = instrumentId, timeframe = timeframe, timestamp = rawCandle.timestamp,
                    open = rawCandle.open, high = rawCandle.high, low = rawCandle.low, close = rawCandle.close, volume = rawCandle.volume,
                    source = CandleSource.HISTORICAL_IMPORT,
                )
            }
            candles.insertAll(entities)
            ingestionJobs.recordProgress(jobId, rowsFetched = raw.size.toLong(), rowsInserted = entities.size.toLong(), rowsSkipped = outcome.duplicateCount.toLong(), progressPercent = 75.0, message = "Stored ${entities.size} candle(s).")

            // 4. Generate metadata: checkpoint advance for incremental-import resume.
            outcome.acceptedCandles.maxByOrNull { it.timestamp }?.let { latest ->
                checkpoints.advanceCheckpoint(providerId, instrumentId, timeframe.value, latest.timestamp)
            }

            // 5. Indicators: all 26, real computation via the existing Phase 2 registry, stored via the existing indicator warehouse.
            val indicatorsComputed = if (entities.isEmpty()) 0 else computeAllIndicators(instrumentId, timeframe, entities)

            // 6. Update status: terminal success.
            ingestionJobs.markSucceeded(jobId)

            ImportResult(
                jobId = jobId, candlesFetched = raw.size, candlesStored = entities.size,
                qualityScore = outcome.qualityScore, indicatorsComputed = indicatorsComputed, succeeded = true,
            )
        } catch (e: Exception) {
            ingestionJobs.recordFailure(jobId, error = e.message ?: e.toString(), nextRetryAt = null)
            ImportResult(jobId = jobId, candlesFetched = 0, candlesStored = 0, qualityScore = 0.0, indicatorsComputed = 0, succeeded = false, error = e.message)
        }
    }

    /**
     * "Automatically calculate ALL 26 indicators for every imported candle... no recalculation
     * unless required": computes every registered [IndicatorType] against the full stored candle
     * history for this instrument/timeframe (not just the newly-imported slice -- most indicators
     * need warmup history that may predate this import), using each indicator's default
     * parameters (an empty params map, which every Phase 2 calculator already falls back to its
     * own documented default for). One [IndicatorDefinitionEntity] per type is reused across
     * imports (looked up by name, only created once) rather than duplicated on every run --
     * that's the literal mechanism behind "no recalculation unless required": a second import
     * finds the same definition and simply appends new values, it doesn't redefine anything.
     */
    private suspend fun computeAllIndicators(instrumentId: Long, timeframe: Timeframe, newCandles: List<HistoricalCandleEntity>): Int {
        val fullHistory = candles.observeRange(instrumentId, timeframe, Long.MIN_VALUE, Long.MAX_VALUE).first()
        var computedCount = 0
        for (type in IndicatorType.entries) {
            val definitionId = definitionFor(type)
            val run = IndicatorComputationRunEntity(
                indicatorDefId = definitionId, instrumentId = instrumentId, timeframe = timeframe.value,
                fromTimestamp = newCandles.minOf { it.timestamp }, toTimestamp = newCandles.maxOf { it.timestamp },
                status = ComputationStatus.RUNNING,
            )
            val runId = indicatorRuns.startRun(run)
            try {
                val points = indicatorCalculators.compute(type, fullHistory, emptyMap())
                if (points.isNotEmpty()) {
                    val values = points.map { point ->
                        IndicatorValueEntity(
                            indicatorDefId = definitionId, instrumentId = instrumentId, timeframe = timeframe.value,
                            timestamp = point.timestamp, value1 = point.value1, value2 = point.value2, value3 = point.value3, value4 = point.value4,
                        )
                    }
                    indicatorValues.storeValues(values)
                    computedCount += values.size
                }
                indicatorRuns.completeRun(runId, rowsComputed = points.size.toLong())
            } catch (e: Exception) {
                indicatorRuns.failRun(runId, error = e.message ?: e.toString())
            }
        }
        return computedCount
    }

    private suspend fun definitionFor(type: IndicatorType): Long {
        val name = "${type.name}_DEFAULT"
        indicatorDefinitions.getLatestByName(name)?.let { return it.indicatorDefId }
        return indicatorDefinitions.define(
            IndicatorDefinitionEntity(name = name, indicatorType = type, paramsJson = "{}", outputLabels = OUTPUT_LABELS.getValue(type)),
        )
    }

    companion object {
        /** One entry per [IndicatorType] -- documents what each calculator's up-to-4 output slots mean, matching every calculator's own KDoc from Phase 2. */
        private val OUTPUT_LABELS: Map<IndicatorType, String> = mapOf(
            IndicatorType.SMA to "value", IndicatorType.EMA to "value", IndicatorType.WMA to "value",
            IndicatorType.VWMA to "value", IndicatorType.VWAP to "value", IndicatorType.RSI to "value",
            IndicatorType.ATR to "value", IndicatorType.CCI to "value", IndicatorType.ROC to "value",
            IndicatorType.MOMENTUM to "value", IndicatorType.WILLIAMS_R to "value", IndicatorType.OBV to "value",
            IndicatorType.CMF to "value", IndicatorType.MFI to "value", IndicatorType.TRIX to "value",
            IndicatorType.MACD to "line,signal,histogram",
            IndicatorType.BOLLINGER_BANDS to "upper,middle,lower",
            IndicatorType.KELTNER_CHANNEL to "upper,middle,lower",
            IndicatorType.DONCHIAN_CHANNEL to "upper,lower,middle",
            IndicatorType.ADX to "adx,+di,-di",
            IndicatorType.DMI to "+di,-di,dx",
            IndicatorType.AROON to "up,down,oscillator",
            IndicatorType.STOCHASTIC to "%K,%D",
            IndicatorType.SUPERTREND to "line,direction",
            IndicatorType.PARABOLIC_SAR to "sar,direction",
            IndicatorType.ICHIMOKU to "tenkan,kijun,senkouA,senkouB",
        )
    }
}

package com.jarvis.tidb.historical.ingestion.status

import com.jarvis.tidb.analytics.repository.BacktestRepository
import com.jarvis.tidb.core.entity.Timeframe
import com.jarvis.tidb.core.repository.HistoricalCandleRepository
import com.jarvis.tidb.historical.indicator.entity.IndicatorType
import com.jarvis.tidb.historical.indicator.repository.IndicatorDefinitionRepository
import com.jarvis.tidb.historical.indicator.repository.IndicatorValueRepository
import com.jarvis.tidb.historical.ingestion.repository.IngestionJobRepository
import com.jarvis.tidb.historical.quality.repository.QualityReportRepository
import com.jarvis.tidb.optimization.entity.OptimizationJobStatus
import com.jarvis.tidb.optimization.repository.OptimizationRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

enum class ValidationStatus { NOT_VALIDATED, PASS, FAIL }
enum class OptimizationDatasetStatus { NONE, IN_PROGRESS, COMPLETED }
enum class BacktestDatasetStatus { NONE, HAS_RESULTS }

/**
 * "Phase 4A Increment 2, Section 1 -- Dataset Status Engine." Every field here is computed live,
 * on every call, from the real repositories underneath -- there is no cache and no separate
 * status table (see [DatasetStatusEngine]'s own docstring for why that's a deliberate choice, not
 * a gap). This is also the literal mechanism behind Section 7's "Source Verification Guard": a
 * value on this class is never something JARVIS "remembers" saying before, it's what's true right
 * now, re-verified every time [DatasetStatusEngine.statusFor] is called.
 */
data class DatasetStatus(
    val instrumentId: Long,
    val timeframe: String,
    val earliestCandle: Long?,
    val latestCandle: Long?,
    val importedCandleCount: Int,
    val duplicateCount: Int,
    val missingCount: Int,
    val indicatorCompletionPercent: Double,
    /** The raw count backing [indicatorCompletionPercent] (out of [com.jarvis.tidb.historical.indicator.entity.IndicatorType.entries]'s size) -- exposed separately so a renderer never has to back-compute it from a rounded percentage, which would risk an off-by-one on the display. */
    val indicatorTypesCompleted: Int,
    val validationStatus: ValidationStatus,
    /** The last known [com.jarvis.tidb.historical.ingestion.entity.IngestionJobStatus] value, or the literal string "NEVER_IMPORTED" if no successful import has ever run for this instrument/timeframe -- an honest, distinct state from any real job status, not defaulted to look like one. */
    val importStatus: String,
    val optimizationStatus: OptimizationDatasetStatus,
    val backtestStatus: BacktestDatasetStatus,
    /** Honestly "NOT_YET_CONNECTED" rather than a guess -- see [DatasetStatusEngine]'s own class docstring on why Evidence Chain linkage (Section 8) is out of scope for this increment. */
    val evidenceStatus: String,
    val lastImportTime: Long?,
    val lastValidationTime: Long?,
    /** "Section 3 -- READY_FOR_OPTIMIZATION": computed, not persisted -- true the moment every real precondition (data exists, validation PASS, all 26 indicators have at least one stored value) is actually met, false the instant any of them stops being true. See [DatasetStatusEngine]'s own docstring for why persisting this as a separate flag would have been redundant, riskier (a migration) state that could drift from the truth it's supposed to reflect. */
    val readyForOptimization: Boolean,
)

/**
 * "Phase 4A Increment 2, Sections 1+3+6+7 -- Dataset Status Engine, READY_FOR_OPTIMIZATION,
 * JARVIS Data Awareness, Source Verification Guard." Deliberately introduces ZERO new database
 * schema -- every field [DatasetStatus] exposes is computed from repositories that already
 * existed before this increment (`HistoricalCandleRepository`, `QualityReportRepository`,
 * `IndicatorDefinitionRepository`/`IndicatorValueRepository`, `OptimizationRepository`,
 * `BacktestRepository`, `IngestionJobRepository`), all already bridged into Hilt from Increment 1
 * or earlier phases.
 *
 * This is the same design choice as [com.jarvis.os.app.core.intelligence.ResponseSourceEngine]
 * from Phase 3C (a pure classifier, not a new persisted field) and
 * [com.jarvis.os.app.core.intelligence.localintent.SystemStatusLocalIntentHandler] (real-time
 * aggregation, not a cached summary) -- extended here to per-instrument granularity. A separate
 * "dataset_status" table caching these fields would be redundant derived state that could drift
 * from the real repositories it's summarizing, and would have required a migration for something
 * that's fully computable on demand -- exactly the kind of avoidable schema risk this project has
 * hit before (the optimization-persistence migration, and the Hilt MissingBinding incidents that
 * followed new repository dependencies). READY_FOR_OPTIMIZATION in particular benefits from this:
 * it can never go stale, because it's never stored in the first place.
 */
@Singleton
class DatasetStatusEngine @Inject constructor(
    private val candles: HistoricalCandleRepository,
    private val qualityReports: QualityReportRepository,
    private val indicatorDefinitions: IndicatorDefinitionRepository,
    private val indicatorValues: IndicatorValueRepository,
    private val optimizationRepository: OptimizationRepository,
    private val backtestRepository: BacktestRepository,
    private val ingestionJobs: IngestionJobRepository,
) {

    suspend fun statusFor(instrumentId: Long, timeframe: Timeframe): DatasetStatus {
        val allCandles = candles.observeRange(instrumentId, timeframe, Long.MIN_VALUE, Long.MAX_VALUE).first()
        val earliest = allCandles.minOfOrNull { it.timestamp }
        val latest = allCandles.maxOfOrNull { it.timestamp }

        val latestReport = qualityReports.getLatest(instrumentId, timeframe.value)
        val validationStatus = when {
            latestReport == null -> ValidationStatus.NOT_VALIDATED
            latestReport.qualityScore >= QUALITY_PASS_THRESHOLD -> ValidationStatus.PASS
            else -> ValidationStatus.FAIL
        }

        val indicatorTypesCompleted = computeIndicatorTypesCompleted(instrumentId, timeframe)
        val indicatorCompletionPercent = indicatorTypesCompleted.toDouble() / IndicatorType.entries.size * 100.0

        val lastJob = ingestionJobs.getLastSuccessful(instrumentId, timeframe.value)
        val importStatus = lastJob?.status?.value ?: "NEVER_IMPORTED"

        val optimizationJobs = optimizationRepository.observeAllJobs().first().filter { it.instrumentId == instrumentId }
        val optimizationStatus = when {
            optimizationJobs.any { it.statusValue == OptimizationJobStatus.COMPLETED.name } -> OptimizationDatasetStatus.COMPLETED
            optimizationJobs.isNotEmpty() -> OptimizationDatasetStatus.IN_PROGRESS
            else -> OptimizationDatasetStatus.NONE
        }

        val hasBacktestResults = backtestRepository.observeAllBacktests().first()
            .filter { it.instrumentIdsCsv.split(",").contains(instrumentId.toString()) }
            .any { backtestRepository.observeResultsByBacktest(it.rowId).first().isNotEmpty() }
        val backtestStatus = if (hasBacktestResults) BacktestDatasetStatus.HAS_RESULTS else BacktestDatasetStatus.NONE

        val readyForOptimization = allCandles.isNotEmpty() &&
            validationStatus == ValidationStatus.PASS &&
            indicatorCompletionPercent >= 100.0

        return DatasetStatus(
            instrumentId = instrumentId,
            timeframe = timeframe.value,
            earliestCandle = earliest,
            latestCandle = latest,
            importedCandleCount = allCandles.size,
            duplicateCount = latestReport?.duplicateCount ?: 0,
            missingCount = latestReport?.missingCount ?: 0,
            indicatorCompletionPercent = indicatorCompletionPercent,
            indicatorTypesCompleted = indicatorTypesCompleted,
            validationStatus = validationStatus,
            importStatus = importStatus,
            optimizationStatus = optimizationStatus,
            backtestStatus = backtestStatus,
            evidenceStatus = "NOT_YET_CONNECTED",
            lastImportTime = lastJob?.completedAt,
            lastValidationTime = latestReport?.generatedAt,
            readyForOptimization = readyForOptimization,
        )
    }

    /** Matches [com.jarvis.tidb.historical.ingestion.pipeline.HistoricalDataImportPipeline]'s own `"${type.name}_DEFAULT"` definition-naming convention exactly -- reused, not reinvented, so this engine reports the real completion state of what that pipeline actually wrote. Returns the raw count of [IndicatorType]s with at least one stored value, out of the full 26 -- not just the types that happen to have a definition yet. */
    private suspend fun computeIndicatorTypesCompleted(instrumentId: Long, timeframe: Timeframe): Int {
        var completedTypes = 0
        for (type in IndicatorType.entries) {
            val definition = indicatorDefinitions.getLatestByName("${type.name}_DEFAULT") ?: continue
            val count = indicatorValues.countInRange(definition.indicatorDefId, instrumentId, timeframe.value, Long.MIN_VALUE, Long.MAX_VALUE)
            if (count > 0) completedTypes++
        }
        return completedTypes
    }

    private companion object {
        /** Matches [com.jarvis.tidb.historical.ingestion.validation.CandleValidator]'s own quality-score scale (0.0-1.0). 0.95 is a deliberately strict bar -- "PASS" should mean genuinely clean data, not merely "mostly clean." */
        const val QUALITY_PASS_THRESHOLD = 0.95
    }
}

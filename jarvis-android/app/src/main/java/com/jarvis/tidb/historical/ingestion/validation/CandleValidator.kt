package com.jarvis.tidb.historical.ingestion.validation

import com.jarvis.tidb.core.entity.Timeframe
import com.jarvis.tidb.historical.ingestion.datasource.RawCandle
import com.jarvis.tidb.historical.quality.entity.IssueSeverity
import com.jarvis.tidb.historical.quality.entity.QualityIssueType

/** One issue found during validation, not yet a [com.jarvis.tidb.historical.quality.entity.QualityIssueEntity] row -- that entity needs a real `reportId` this class has no way to know, so [com.jarvis.tidb.historical.ingestion.pipeline.HistoricalDataImportPipeline] is what turns these into persisted rows once a report exists to attach them to. */
data class QualityIssueDraft(
    val issueType: QualityIssueType,
    val severity: IssueSeverity,
    val timestamp: Long?,
    val details: String,
)

data class ValidationOutcome(
    /** Duplicates removed, sorted ascending by timestamp -- exactly what's safe to hand to [com.jarvis.tidb.core.repository.HistoricalCandleRepository.insertAll]. Candles that individually fail an OHLC/volume check are still included here (see this class's own docstring on why rejection is a caller decision, not this validator's). */
    val acceptedCandles: List<RawCandle>,
    val issues: List<QualityIssueDraft>,
    val expectedCandleCount: Int,
    val actualCandleCount: Int,
    val duplicateCount: Int,
    val missingCount: Int,
    val ohlcViolationCount: Int,
    val volumeAnomalyCount: Int,
    val timestampDiscontinuityCount: Int,
) {
    /** "Reject corrupted data": the spec's own phrasing, but corrupted here means malformed beyond use (that's [com.jarvis.tidb.historical.ingestion.datasource.CsvDataSourceProvider] silently dropping unparseable lines at the source) -- an OHLC violation on an otherwise well-formed candle is a data-quality WARNING to record and surface (Section 3's own "generate validation reports"), not a reason to discard real historical data a human may still want to inspect. [qualityScore] is what downstream consumers (Section 7's future Dataset Status Engine) use to know how much to trust this import, in [0.0, 1.0]. */
    val qualityScore: Double
        get() = if (expectedCandleCount == 0) 1.0 else (1.0 - (issues.size.toDouble() / expectedCandleCount.coerceAtLeast(1))).coerceIn(0.0, 1.0)
}

/**
 * "Phase 4A, Section 3 -- Data Validation." Pure, dependency-free logic -- no repository, no
 * database -- so it's trivially testable and reusable regardless of which
 * [com.jarvis.tidb.historical.ingestion.datasource.DataSourceProvider] produced the raw candles.
 */
object CandleValidator {

    fun validate(rawCandles: List<RawCandle>, timeframe: Timeframe, periodStart: Long, periodEnd: Long): ValidationOutcome {
        val issues = mutableListOf<QualityIssueDraft>()

        // Duplicates: same timestamp appearing more than once. Keep the first occurrence.
        val seenTimestamps = mutableSetOf<Long>()
        val deduped = mutableListOf<RawCandle>()
        var duplicateCount = 0
        for (candle in rawCandles.sortedBy { it.timestamp }) {
            if (!seenTimestamps.add(candle.timestamp)) {
                duplicateCount++
                issues += QualityIssueDraft(QualityIssueType.DUPLICATE_CANDLE, IssueSeverity.WARNING, candle.timestamp, "Duplicate candle at timestamp ${candle.timestamp}, later occurrence discarded.")
                continue
            }
            deduped += candle
        }

        // OHLC consistency: high must be the max, low must be the min, of {open, high, low, close}.
        var ohlcViolationCount = 0
        for (candle in deduped) {
            val isInconsistent = candle.high < candle.low ||
                candle.high < candle.open || candle.high < candle.close ||
                candle.low > candle.open || candle.low > candle.close
            if (isInconsistent) {
                ohlcViolationCount++
                issues += QualityIssueDraft(
                    QualityIssueType.OHLC_INCONSISTENT, IssueSeverity.CRITICAL, candle.timestamp,
                    "OHLC inconsistent at ${candle.timestamp}: open=${candle.open} high=${candle.high} low=${candle.low} close=${candle.close}.",
                )
            }
        }

        // Negative volume.
        var volumeAnomalyCount = 0
        for (candle in deduped) {
            if (candle.volume < 0) {
                volumeAnomalyCount++
                issues += QualityIssueDraft(QualityIssueType.VOLUME_ANOMALY, IssueSeverity.WARNING, candle.timestamp, "Negative volume ${candle.volume} at timestamp ${candle.timestamp}.")
            }
        }

        // Gap detection: consecutive candles further apart than one timeframe interval.
        val intervalMillis = intervalMillisFor(timeframe)
        var missingCount = 0
        var timestampDiscontinuityCount = 0
        for (i in 1 until deduped.size) {
            val gap = deduped[i].timestamp - deduped[i - 1].timestamp
            if (gap > intervalMillis) {
                val missed = (gap / intervalMillis - 1).toInt().coerceAtLeast(0)
                missingCount += missed
                timestampDiscontinuityCount++
                issues += QualityIssueDraft(
                    QualityIssueType.MISSING_CANDLE, IssueSeverity.INFO, deduped[i - 1].timestamp,
                    "Gap of $missed missing candle(s) between ${deduped[i - 1].timestamp} and ${deduped[i].timestamp}.",
                )
            } else if (gap < 0) {
                timestampDiscontinuityCount++
                issues += QualityIssueDraft(QualityIssueType.TIMESTAMP_DISCONTINUITY, IssueSeverity.CRITICAL, deduped[i].timestamp, "Out-of-order timestamp at ${deduped[i].timestamp}.")
            }
        }

        val expectedCandleCount = if (intervalMillis <= 0 || periodEnd <= periodStart) deduped.size else ((periodEnd - periodStart) / intervalMillis).toInt().coerceAtLeast(deduped.size)

        return ValidationOutcome(
            acceptedCandles = deduped,
            issues = issues,
            expectedCandleCount = expectedCandleCount,
            actualCandleCount = deduped.size,
            duplicateCount = duplicateCount,
            missingCount = missingCount,
            ohlcViolationCount = ohlcViolationCount,
            volumeAnomalyCount = volumeAnomalyCount,
            timestampDiscontinuityCount = timestampDiscontinuityCount,
        )
    }

    /** Approximate for W1/MN1 (calendar weeks/months aren't fixed-length) -- documented, not silently treated as exact; gap detection for those two timeframes is inherently a looser signal than for fixed-interval ones. */
    fun intervalMillisFor(timeframe: Timeframe): Long = when (timeframe) {
        Timeframe.M1 -> 60_000L
        Timeframe.M3 -> 180_000L
        Timeframe.M5 -> 300_000L
        Timeframe.M10 -> 600_000L
        Timeframe.M15 -> 900_000L
        Timeframe.M30 -> 1_800_000L
        Timeframe.M45 -> 2_700_000L
        Timeframe.H1 -> 3_600_000L
        Timeframe.H2 -> 7_200_000L
        Timeframe.H4 -> 14_400_000L
        Timeframe.D1 -> 86_400_000L
        Timeframe.W1 -> 604_800_000L
        Timeframe.MN1 -> 2_592_000_000L // 30 days, approximate
    }
}

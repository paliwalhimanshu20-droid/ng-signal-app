package com.jarvis.tidb.historical.ingestion.validation

import com.jarvis.tidb.core.entity.Timeframe
import com.jarvis.tidb.historical.ingestion.datasource.RawCandle
import com.jarvis.tidb.historical.quality.entity.QualityIssueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Phase 4A, Section 3 -- Data Validation." Pure logic, no fakes needed. */
class CandleValidatorTest {

    private fun candle(ts: Long, o: Double = 100.0, h: Double = 101.0, l: Double = 99.0, c: Double = 100.5, v: Long = 1000L) =
        RawCandle(ts, o, h, l, c, v)

    @Test
    fun `clean data produces zero issues and a perfect quality score`() {
        val candles = (0 until 5).map { i -> candle(ts = i * 60_000L) }
        val outcome = CandleValidator.validate(candles, Timeframe.M1, periodStart = 0L, periodEnd = 5 * 60_000L)

        assertEquals(0, outcome.duplicateCount)
        assertEquals(0, outcome.ohlcViolationCount)
        assertEquals(0, outcome.volumeAnomalyCount)
        assertEquals(0, outcome.missingCount)
        assertTrue(outcome.issues.isEmpty())
        assertEquals(1.0, outcome.qualityScore, 0.0001)
    }

    @Test
    fun `a duplicate timestamp is detected and only the first occurrence is kept`() {
        val candles = listOf(candle(ts = 0L), candle(ts = 60_000L), candle(ts = 60_000L, o = 999.0))
        val outcome = CandleValidator.validate(candles, Timeframe.M1, periodStart = 0L, periodEnd = 120_000L)

        assertEquals(1, outcome.duplicateCount)
        assertEquals(2, outcome.acceptedCandles.size)
        assertEquals(100.0, outcome.acceptedCandles.last { it.timestamp == 60_000L }.open, 0.0001)
        assertTrue(outcome.issues.any { it.issueType == QualityIssueType.DUPLICATE_CANDLE })
    }

    @Test
    fun `high below low is an OHLC violation`() {
        val candles = listOf(candle(ts = 0L, h = 98.0, l = 99.0))
        val outcome = CandleValidator.validate(candles, Timeframe.M1, periodStart = 0L, periodEnd = 60_000L)

        assertEquals(1, outcome.ohlcViolationCount)
        assertTrue(outcome.issues.any { it.issueType == QualityIssueType.OHLC_INCONSISTENT })
    }

    @Test
    fun `close above high is an OHLC violation`() {
        val candles = listOf(candle(ts = 0L, h = 101.0, c = 105.0))
        val outcome = CandleValidator.validate(candles, Timeframe.M1, periodStart = 0L, periodEnd = 60_000L)

        assertEquals(1, outcome.ohlcViolationCount)
    }

    @Test
    fun `negative volume is flagged`() {
        val candles = listOf(candle(ts = 0L, v = -50L))
        val outcome = CandleValidator.validate(candles, Timeframe.M1, periodStart = 0L, periodEnd = 60_000L)

        assertEquals(1, outcome.volumeAnomalyCount)
        assertTrue(outcome.issues.any { it.issueType == QualityIssueType.VOLUME_ANOMALY })
    }

    @Test
    fun `a gap larger than one interval is detected as missing candles`() {
        val candles = listOf(candle(ts = 0L), candle(ts = 180_000L))
        val outcome = CandleValidator.validate(candles, Timeframe.M1, periodStart = 0L, periodEnd = 180_000L)

        assertEquals(2, outcome.missingCount)
        assertTrue(outcome.issues.any { it.issueType == QualityIssueType.MISSING_CANDLE })
    }

    @Test
    fun `consecutive candles at exactly one interval apart are not flagged as a gap`() {
        val candles = listOf(candle(ts = 0L), candle(ts = 60_000L), candle(ts = 120_000L))
        val outcome = CandleValidator.validate(candles, Timeframe.M1, periodStart = 0L, periodEnd = 120_000L)

        assertEquals(0, outcome.missingCount)
    }

    @Test
    fun `intervalMillisFor returns the correct millisecond interval for every timeframe`() {
        assertEquals(60_000L, CandleValidator.intervalMillisFor(Timeframe.M1))
        assertEquals(300_000L, CandleValidator.intervalMillisFor(Timeframe.M5))
        assertEquals(3_600_000L, CandleValidator.intervalMillisFor(Timeframe.H1))
        assertEquals(86_400_000L, CandleValidator.intervalMillisFor(Timeframe.D1))
    }
}

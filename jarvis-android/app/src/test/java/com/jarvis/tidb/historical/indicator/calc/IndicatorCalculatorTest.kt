package com.jarvis.tidb.historical.indicator.calc

import com.jarvis.tidb.core.entity.CandleSource
import com.jarvis.tidb.core.entity.HistoricalCandleEntity
import com.jarvis.tidb.core.entity.Timeframe
import com.jarvis.tidb.historical.indicator.entity.IndicatorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Universal Indicator Engine" Phase 2: hand-verified expected values for a representative
 * subset of the 26 calculators (one per output shape -- single-output, multi-output, and the
 * 4-slot Ichimoku exception -- rather than all 26 exhaustively), plus a completeness check that
 * every [IndicatorType] has exactly one registered [IndicatorCalculator].
 */
class IndicatorCalculatorTest {

    /** Minimal fixture builder -- only the fields each test actually varies are parameters, everything else is a fixed, valid default. */
    private fun candle(timestamp: Long, open: Double, high: Double, low: Double, close: Double, volume: Long = 1000L) =
        HistoricalCandleEntity(
            instrumentId = 1L,
            timeframe = Timeframe.D1,
            timestamp = timestamp,
            open = open,
            high = high,
            low = low,
            close = close,
            volume = volume,
            source = CandleSource.HISTORICAL_IMPORT,
        )

    private fun closesSeries(closes: List<Double>): List<HistoricalCandleEntity> =
        closes.mapIndexed { i, c -> candle(timestamp = i.toLong(), open = c, high = c, low = c, close = c) }

    @Test
    fun `SMA of a simple ascending series matches hand-computed averages`() {
        val candles = closesSeries((1..10).map { it.toDouble() })
        val result = SmaCalculator().compute(candles, mapOf("period" to 3.0))

        assertEquals(8, result.size) // 10 candles, period 3 -> first output at index 2
        assertEquals(2.0, result.first().value1, 0.0001) // avg(1,2,3)
        assertEquals(9.0, result.last().value1, 0.0001) // avg(8,9,10)
    }

    @Test
    fun `EMA of a constant-increment series matches the textbook seed-then-smooth formula`() {
        val candles = closesSeries(listOf(1.0, 2.0, 3.0, 4.0, 5.0))
        val result = EmaCalculator().compute(candles, mapOf("period" to 3.0))

        // seed = SMA(1,2,3) = 2.0; multiplier = 2/(3+1) = 0.5
        // next: (4-2)*0.5+2 = 3.0; next: (5-3)*0.5+3 = 4.0
        assertEquals(listOf(2.0, 3.0, 4.0), result.map { it.value1 })
    }

    @Test
    fun `RSI is exactly 100 for a strictly monotonic increasing series with no losses`() {
        val candles = closesSeries((1..15).map { it.toDouble() })
        val result = RsiCalculator().compute(candles, mapOf("period" to 14.0))

        assertEquals(1, result.size)
        assertEquals(100.0, result.first().value1, 0.0001)
    }

    @Test
    fun `ATR is constant when every candle has the same true range`() {
        // high-low = 2 for every candle; close stays at the midpoint so no gap ever widens the range.
        val candles = (0 until 6).map { i -> candle(timestamp = i.toLong(), open = 100.0, high = 101.0, low = 99.0, close = 100.0) }
        val result = AtrCalculator().compute(candles, mapOf("period" to 3.0))

        assertTrue(result.isNotEmpty())
        result.forEach { assertEquals(2.0, it.value1, 0.0001) }
    }

    @Test
    fun `Bollinger Bands collapse to a flat line when price never moves`() {
        val candles = closesSeries(List(10) { 100.0 })
        val result = BollingerBandsCalculator().compute(candles, mapOf("period" to 5.0, "stdDevMultiplier" to 2.0))

        assertTrue(result.isNotEmpty())
        result.forEach { point ->
            assertEquals(100.0, point.value1!!, 0.0001) // upper
            assertEquals(100.0, point.value2!!, 0.0001) // middle
            assertEquals(100.0, point.value3!!, 0.0001) // lower
        }
    }

    @Test
    fun `MACD produces a 3-output histogram equal to line minus signal`() {
        val candles = closesSeries((1..40).map { it.toDouble() })
        val result = MacdCalculator().compute(candles, mapOf("fast" to 12.0, "slow" to 26.0, "signal" to 9.0))

        assertTrue(result.isNotEmpty())
        result.forEach { point ->
            assertEquals(point.value1!! - point.value2!!, point.value3!!, 0.0001)
        }
    }

    @Test
    fun `Ichimoku's 4 stored slots match hand-computed midpoints, Chikou is never a 5th slot`() {
        val candles = listOf(
            candle(timestamp = 0, open = 5.0, high = 10.0, low = 0.0, close = 5.0),
            candle(timestamp = 1, open = 7.0, high = 12.0, low = 2.0, close = 7.0),
            candle(timestamp = 2, open = 6.0, high = 8.0, low = 4.0, close = 6.0),
            candle(timestamp = 3, open = 10.0, high = 14.0, low = 6.0, close = 10.0),
        )
        val result = IchimokuCalculator().compute(candles, mapOf("tenkanPeriod" to 2.0, "kijunPeriod" to 3.0, "senkouBPeriod" to 4.0))

        assertEquals(1, result.size)
        val point = result.first()
        assertEquals(9.0, point.value1!!, 0.0001) // Tenkan: (14+4)/2 over last 2 candles
        assertEquals(8.0, point.value2!!, 0.0001) // Kijun: (14+2)/2 over last 3 candles
        assertEquals(8.5, point.value3!!, 0.0001) // Senkou A: (Tenkan+Kijun)/2
        assertEquals(7.0, point.value4!!, 0.0001) // Senkou B: (14+0)/2 over all 4 candles
    }

    @Test
    fun `every IndicatorType has exactly one registered calculator`() {
        val allCalculators: Set<IndicatorCalculator> = setOf(
            SmaCalculator(), EmaCalculator(), WmaCalculator(), VwmaCalculator(), VwapCalculator(),
            RsiCalculator(), MacdCalculator(), CciCalculator(), RocCalculator(), MomentumCalculator(),
            WilliamsRCalculator(), StochasticCalculator(), AtrCalculator(), BollingerBandsCalculator(),
            SupertrendCalculator(), KeltnerChannelCalculator(), DonchianChannelCalculator(),
            AdxCalculator(), DmiCalculator(), AroonCalculator(), TrixCalculator(),
            ParabolicSarCalculator(), IchimokuCalculator(), ObvCalculator(), CmfCalculator(), MfiCalculator(),
        )
        val registry = IndicatorCalculatorRegistry(allCalculators)

        assertEquals(IndicatorType.entries.toSet(), registry.supportedTypes)
        assertEquals(26, IndicatorType.entries.size)
    }

    @Test
    fun `computing an unregistered type throws rather than silently returning nothing`() {
        val registry = IndicatorCalculatorRegistry(setOf(SmaCalculator()))
        val candles = closesSeries((1..5).map { it.toDouble() })

        try {
            registry.compute(IndicatorType.EMA, candles, emptyMap())
            throw AssertionError("expected IllegalStateException for an unregistered type")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("EMA"))
        }
    }
}

package com.jarvis.tidb.historical.indicator.calc

import com.jarvis.tidb.core.entity.HistoricalCandleEntity
import com.jarvis.tidb.historical.indicator.entity.IndicatorType
import javax.inject.Inject
import javax.inject.Singleton

/** params: `period` (default 14). value1 = RSI via Wilder's smoothing of average gain/loss. */
@Singleton
class RsiCalculator @Inject constructor() : IndicatorCalculator {
    override val type = IndicatorType.RSI
    override fun compute(candles: List<HistoricalCandleEntity>, params: Map<String, Double>): List<IndicatorPoint> {
        val period = IndicatorMath.requirePeriod(params)
        if (candles.size < period + 1) return emptyList()
        val gains = mutableListOf<Double>()
        val losses = mutableListOf<Double>()
        for (i in 1 until candles.size) {
            val change = candles[i].close - candles[i - 1].close
            gains += if (change > 0) change else 0.0
            losses += if (change < 0) -change else 0.0
        }
        val avgGains = IndicatorMath.wilderSmooth(gains, period)
        val avgLosses = IndicatorMath.wilderSmooth(losses, period)
        return avgGains.indices.map { i ->
            val rs = if (avgLosses[i] == 0.0) Double.MAX_VALUE else avgGains[i] / avgLosses[i]
            val rsi = if (avgLosses[i] == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + rs))
            // candles index: gains/losses[i] corresponds to candles[i+1]; wilderSmooth's output index i
            // corresponds to gains/losses index (period - 1 + i), i.e. candles index (period + i).
            IndicatorPoint(candles[period + i].timestamp, rsi)
        }
    }
}

/**
 * params: `fast` (default 12), `slow` (default 26), `signal` (default 9). value1 = MACD line
 * (EMA(fast) - EMA(slow)), value2 = signal line (EMA(signal) of the MACD line), value3 = histogram
 * (value1 - value2).
 */
@Singleton
class MacdCalculator @Inject constructor() : IndicatorCalculator {
    override val type = IndicatorType.MACD
    override fun compute(candles: List<HistoricalCandleEntity>, params: Map<String, Double>): List<IndicatorPoint> {
        val fast = (params["fast"] ?: 12.0).toInt()
        val slow = (params["slow"] ?: 26.0).toInt()
        val signal = (params["signal"] ?: 9.0).toInt()
        val closes = candles.map { it.close }
        val fastEma = IndicatorMath.ema(closes, fast)
        val slowEma = IndicatorMath.ema(closes, slow)
        if (fastEma.isEmpty() || slowEma.isEmpty()) return emptyList()
        // fastEma starts at candle index (fast - 1), slowEma at (slow - 1); align both to slow's start.
        val offset = slow - fast
        val macdLine = (0 until slowEma.size).map { i -> fastEma[i + offset] - slowEma[i] }
        val signalLine = IndicatorMath.ema(macdLine, signal)
        if (signalLine.isEmpty()) return emptyList()
        val macdStartCandleIndex = slow - 1 // index into `candles` where macdLine[0] applies
        val signalStartInMacd = signal - 1 // index into macdLine where signalLine[0] applies
        return signalLine.indices.map { i ->
            val macdIndex = signalStartInMacd + i
            val candleIndex = macdStartCandleIndex + macdIndex
            val macdValue = macdLine[macdIndex]
            val signalValue = signalLine[i]
            IndicatorPoint(candles[candleIndex].timestamp, macdValue, signalValue, macdValue - signalValue)
        }
    }
}

/** params: `period` (default 20). value1 = Commodity Channel Index: (typicalPrice - SMA(typicalPrice)) / (0.015 * meanAbsoluteDeviation). */
@Singleton
class CciCalculator @Inject constructor() : IndicatorCalculator {
    override val type = IndicatorType.CCI
    override fun compute(candles: List<HistoricalCandleEntity>, params: Map<String, Double>): List<IndicatorPoint> {
        val period = IndicatorMath.requirePeriod(params, default = 20.0)
        if (candles.size < period) return emptyList()
        val typicalPrices = candles.map { IndicatorMath.typicalPrice(it) }
        val out = mutableListOf<IndicatorPoint>()
        for (end in period - 1 until candles.size) {
            val window = typicalPrices.subList(end - period + 1, end + 1)
            val sma = window.average()
            val meanDeviation = window.sumOf { kotlin.math.abs(it - sma) } / period
            val cci = if (meanDeviation == 0.0) 0.0 else (typicalPrices[end] - sma) / (0.015 * meanDeviation)
            out += IndicatorPoint(candles[end].timestamp, cci)
        }
        return out
    }
}

/** params: `period` (default 12). value1 = Rate of Change: (close - close[period ago]) / close[period ago] * 100. */
@Singleton
class RocCalculator @Inject constructor() : IndicatorCalculator {
    override val type = IndicatorType.ROC
    override fun compute(candles: List<HistoricalCandleEntity>, params: Map<String, Double>): List<IndicatorPoint> {
        val period = IndicatorMath.requirePeriod(params, default = 12.0)
        if (candles.size <= period) return emptyList()
        return (period until candles.size).map { i ->
            val prior = candles[i - period].close
            val roc = if (prior == 0.0) 0.0 else (candles[i].close - prior) / prior * 100.0
            IndicatorPoint(candles[i].timestamp, roc)
        }
    }
}

/** params: `period` (default 10). value1 = Momentum: close - close[period ago]. */
@Singleton
class MomentumCalculator @Inject constructor() : IndicatorCalculator {
    override val type = IndicatorType.MOMENTUM
    override fun compute(candles: List<HistoricalCandleEntity>, params: Map<String, Double>): List<IndicatorPoint> {
        val period = IndicatorMath.requirePeriod(params, default = 10.0)
        if (candles.size <= period) return emptyList()
        return (period until candles.size).map { i ->
            IndicatorPoint(candles[i].timestamp, candles[i].close - candles[i - period].close)
        }
    }
}

/** params: `period` (default 14). value1 = Williams %R: (highestHigh - close) / (highestHigh - lowestLow) * -100. */
@Singleton
class WilliamsRCalculator @Inject constructor() : IndicatorCalculator {
    override val type = IndicatorType.WILLIAMS_R
    override fun compute(candles: List<HistoricalCandleEntity>, params: Map<String, Double>): List<IndicatorPoint> {
        val period = IndicatorMath.requirePeriod(params)
        if (candles.size < period) return emptyList()
        val out = mutableListOf<IndicatorPoint>()
        for (end in period - 1 until candles.size) {
            val window = candles.subList(end - period + 1, end + 1)
            val highestHigh = window.maxOf { it.high }
            val lowestLow = window.minOf { it.low }
            val range = highestHigh - lowestLow
            val value = if (range == 0.0) 0.0 else (highestHigh - candles[end].close) / range * -100.0
            out += IndicatorPoint(candles[end].timestamp, value)
        }
        return out
    }
}

/** params: `kPeriod` (default 14), `dPeriod` (default 3), `slowing` (default 3). value1 = %K (slowed), value2 = %D (SMA of %K over dPeriod). */
@Singleton
class StochasticCalculator @Inject constructor() : IndicatorCalculator {
    override val type = IndicatorType.STOCHASTIC
    override fun compute(candles: List<HistoricalCandleEntity>, params: Map<String, Double>): List<IndicatorPoint> {
        val kPeriod = (params["kPeriod"] ?: 14.0).toInt()
        val dPeriod = (params["dPeriod"] ?: 3.0).toInt()
        val slowing = (params["slowing"] ?: 3.0).toInt().coerceAtLeast(1)
        if (candles.size < kPeriod) return emptyList()

        val rawK = mutableListOf<Double>()
        val rawKTimestamps = mutableListOf<Long>()
        for (end in kPeriod - 1 until candles.size) {
            val window = candles.subList(end - kPeriod + 1, end + 1)
            val highestHigh = window.maxOf { it.high }
            val lowestLow = window.minOf { it.low }
            val range = highestHigh - lowestLow
            rawK += if (range == 0.0) 0.0 else (candles[end].close - lowestLow) / range * 100.0
            rawKTimestamps += candles[end].timestamp
        }
        if (rawK.size < slowing) return emptyList()
        // "Slowed" %K is itself an SMA of raw %K over `slowing` -- standard "slow stochastic" definition.
        val slowedK = mutableListOf<Double>()
        for (end in slowing - 1 until rawK.size) {
            slowedK += rawK.subList(end - slowing + 1, end + 1).average()
        }
        if (slowedK.size < dPeriod) return emptyList()
        val out = mutableListOf<IndicatorPoint>()
        for (end in dPeriod - 1 until slowedK.size) {
            val d = slowedK.subList(end - dPeriod + 1, end + 1).average()
            val timestampIndex = (slowing - 1) + end
            out += IndicatorPoint(rawKTimestamps[timestampIndex], slowedK[end], d)
        }
        return out
    }
}

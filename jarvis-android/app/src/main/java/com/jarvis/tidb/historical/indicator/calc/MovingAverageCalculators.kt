package com.jarvis.tidb.historical.indicator.calc

import com.jarvis.tidb.core.entity.HistoricalCandleEntity
import com.jarvis.tidb.historical.indicator.entity.IndicatorType
import javax.inject.Inject
import javax.inject.Singleton

/** params: `period` (default 14). value1 = SMA of `close` over `period`. */
@Singleton
class SmaCalculator @Inject constructor() : IndicatorCalculator {
    override val type = IndicatorType.SMA
    override fun compute(candles: List<HistoricalCandleEntity>, params: Map<String, Double>): List<IndicatorPoint> {
        val period = IndicatorMath.requirePeriod(params)
        if (candles.size < period) return emptyList()
        val out = mutableListOf<IndicatorPoint>()
        var sum = candles.take(period).sumOf { it.close }
        out += IndicatorPoint(candles[period - 1].timestamp, sum / period)
        for (i in period until candles.size) {
            sum += candles[i].close - candles[i - period].close
            out += IndicatorPoint(candles[i].timestamp, sum / period)
        }
        return out
    }
}

/** params: `period` (default 14). value1 = EMA of `close` over `period`, seeded with the SMA of the first `period` closes. */
@Singleton
class EmaCalculator @Inject constructor() : IndicatorCalculator {
    override val type = IndicatorType.EMA
    override fun compute(candles: List<HistoricalCandleEntity>, params: Map<String, Double>): List<IndicatorPoint> {
        val period = IndicatorMath.requirePeriod(params)
        val closes = candles.map { it.close }
        val emaValues = IndicatorMath.ema(closes, period)
        if (emaValues.isEmpty()) return emptyList()
        return emaValues.indices.map { i -> IndicatorPoint(candles[period - 1 + i].timestamp, emaValues[i]) }
    }
}

/** params: `period` (default 14). value1 = WMA of `close`, weights 1..period with the most recent candle weighted highest. */
@Singleton
class WmaCalculator @Inject constructor() : IndicatorCalculator {
    override val type = IndicatorType.WMA
    override fun compute(candles: List<HistoricalCandleEntity>, params: Map<String, Double>): List<IndicatorPoint> {
        val period = IndicatorMath.requirePeriod(params)
        if (candles.size < period) return emptyList()
        val weightSum = period * (period + 1) / 2.0
        val out = mutableListOf<IndicatorPoint>()
        for (end in period - 1 until candles.size) {
            var weighted = 0.0
            for (w in 1..period) {
                weighted += candles[end - period + w].close * w
            }
            out += IndicatorPoint(candles[end].timestamp, weighted / weightSum)
        }
        return out
    }
}

/** params: `period` (default 14). value1 = Volume-Weighted Moving Average: sum(close*volume) / sum(volume) over `period`. */
@Singleton
class VwmaCalculator @Inject constructor() : IndicatorCalculator {
    override val type = IndicatorType.VWMA
    override fun compute(candles: List<HistoricalCandleEntity>, params: Map<String, Double>): List<IndicatorPoint> {
        val period = IndicatorMath.requirePeriod(params)
        if (candles.size < period) return emptyList()
        val out = mutableListOf<IndicatorPoint>()
        for (end in period - 1 until candles.size) {
            val window = candles.subList(end - period + 1, end + 1)
            val volumeSum = window.sumOf { it.volume.toDouble() }
            val value = if (volumeSum == 0.0) window.map { it.close }.average() else window.sumOf { it.close * it.volume } / volumeSum
            out += IndicatorPoint(candles[end].timestamp, value)
        }
        return out
    }
}

/**
 * VWAP: cumulative(typicalPrice * volume) / cumulative(volume). No `period` param -- VWAP is a
 * running cumulative measure, conventionally reset at the start of each session. This
 * implementation treats the entire supplied `candles` list as one cumulative window (the caller
 * is responsible for passing a single session's candles if session-reset VWAP is what's wanted --
 * [com.jarvis.tidb.core.entity.MarketSessionEntity] already models sessions for exactly this, but
 * slicing by session is a caller/orchestration concern, not this calculator's).
 */
@Singleton
class VwapCalculator @Inject constructor() : IndicatorCalculator {
    override val type = IndicatorType.VWAP
    override fun compute(candles: List<HistoricalCandleEntity>, params: Map<String, Double>): List<IndicatorPoint> {
        if (candles.isEmpty()) return emptyList()
        var cumulativeTpv = 0.0
        var cumulativeVolume = 0.0
        return candles.map { c ->
            val tp = IndicatorMath.typicalPrice(c)
            cumulativeTpv += tp * c.volume
            cumulativeVolume += c.volume
            val value = if (cumulativeVolume == 0.0) tp else cumulativeTpv / cumulativeVolume
            IndicatorPoint(c.timestamp, value)
        }
    }
}

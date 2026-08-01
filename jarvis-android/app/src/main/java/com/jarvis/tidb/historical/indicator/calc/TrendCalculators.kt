package com.jarvis.tidb.historical.indicator.calc

import com.jarvis.tidb.core.entity.HistoricalCandleEntity
import com.jarvis.tidb.historical.indicator.entity.IndicatorType
import javax.inject.Inject
import javax.inject.Singleton

/** Directional movement math shared by [DmiCalculator] and [AdxCalculator] -- both are Wilder's DMI system at different smoothing stages, not two unrelated indicators, so the +DM/-DM/TR series is computed once here rather than duplicated. */
private object DirectionalMovement {
    data class Series(val plusDi: List<Double>, val minusDi: List<Double>, val dx: List<Double>, val startCandleIndex: Int)

    fun compute(candles: List<HistoricalCandleEntity>, period: Int): Series? {
        if (candles.size < period + 1) return null
        val plusDm = mutableListOf<Double>()
        val minusDm = mutableListOf<Double>()
        val trueRanges = mutableListOf<Double>()
        for (i in 1 until candles.size) {
            val upMove = candles[i].high - candles[i - 1].high
            val downMove = candles[i - 1].low - candles[i].low
            plusDm += if (upMove > downMove && upMove > 0) upMove else 0.0
            minusDm += if (downMove > upMove && downMove > 0) downMove else 0.0
            trueRanges += IndicatorMath.trueRange(candles[i], candles[i - 1].close)
        }
        val smoothedPlusDm = IndicatorMath.wilderSmooth(plusDm, period)
        val smoothedMinusDm = IndicatorMath.wilderSmooth(minusDm, period)
        val smoothedTr = IndicatorMath.wilderSmooth(trueRanges, period)
        if (smoothedTr.isEmpty()) return null
        val plusDi = smoothedPlusDm.indices.map { i -> if (smoothedTr[i] == 0.0) 0.0 else 100.0 * smoothedPlusDm[i] / smoothedTr[i] }
        val minusDi = smoothedMinusDm.indices.map { i -> if (smoothedTr[i] == 0.0) 0.0 else 100.0 * smoothedMinusDm[i] / smoothedTr[i] }
        val dx = plusDi.indices.map { i ->
            val sum = plusDi[i] + minusDi[i]
            if (sum == 0.0) 0.0 else 100.0 * kotlin.math.abs(plusDi[i] - minusDi[i]) / sum
        }
        // plusDm[k] corresponds to candles[k+1]; wilderSmooth output index i corresponds to
        // plusDm index (period - 1 + i), i.e. candles index (period + i).
        return Series(plusDi, minusDi, dx, startCandleIndex = period)
    }
}

/** params: `period` (default 14). value1 = +DI, value2 = -DI, value3 = DX (the un-smoothed directional index -- ADX is DX further Wilder-smoothed, see [AdxCalculator]). */
@Singleton
class DmiCalculator @Inject constructor() : IndicatorCalculator {
    override val type = IndicatorType.DMI
    override fun compute(candles: List<HistoricalCandleEntity>, params: Map<String, Double>): List<IndicatorPoint> {
        val period = IndicatorMath.requirePeriod(params)
        val series = DirectionalMovement.compute(candles, period) ?: return emptyList()
        return series.dx.indices.map { i ->
            IndicatorPoint(candles[series.startCandleIndex + i].timestamp, series.plusDi[i], series.minusDi[i], series.dx[i])
        }
    }
}

/** params: `period` (default 14). value1 = ADX (Wilder-smoothed average of DX over `period`), value2 = +DI, value3 = -DI at the same point. */
@Singleton
class AdxCalculator @Inject constructor() : IndicatorCalculator {
    override val type = IndicatorType.ADX
    override fun compute(candles: List<HistoricalCandleEntity>, params: Map<String, Double>): List<IndicatorPoint> {
        val period = IndicatorMath.requirePeriod(params)
        val series = DirectionalMovement.compute(candles, period) ?: return emptyList()
        val adxValues = IndicatorMath.wilderSmooth(series.dx, period)
        if (adxValues.isEmpty()) return emptyList()
        // series.dx index i corresponds to candles index (series.startCandleIndex + i); adxValues
        // index j corresponds to series.dx index (period - 1 + j).
        return adxValues.indices.map { j ->
            val dxIndex = period - 1 + j
            val candleIndex = series.startCandleIndex + dxIndex
            IndicatorPoint(candles[candleIndex].timestamp, adxValues[j], series.plusDi[dxIndex], series.minusDi[dxIndex])
        }
    }
}

/** params: `period` (default 25). value1 = Aroon Up, value2 = Aroon Down, value3 = Aroon Oscillator (up - down). Window is `period + 1` candles, the conventional Aroon lookback (including the current bar). */
@Singleton
class AroonCalculator @Inject constructor() : IndicatorCalculator {
    override val type = IndicatorType.AROON
    override fun compute(candles: List<HistoricalCandleEntity>, params: Map<String, Double>): List<IndicatorPoint> {
        val period = IndicatorMath.requirePeriod(params, default = 25.0)
        val windowSize = period + 1
        if (candles.size < windowSize) return emptyList()
        val out = mutableListOf<IndicatorPoint>()
        for (end in windowSize - 1 until candles.size) {
            val window = candles.subList(end - windowSize + 1, end + 1)
            val highestIdx = window.indices.maxBy { window[it].high }
            val lowestIdx = window.indices.minBy { window[it].low }
            val periodsSinceHigh = (windowSize - 1) - highestIdx
            val periodsSinceLow = (windowSize - 1) - lowestIdx
            val up = 100.0 * (period - periodsSinceHigh) / period
            val down = 100.0 * (period - periodsSinceLow) / period
            out += IndicatorPoint(candles[end].timestamp, up, down, up - down)
        }
        return out
    }
}

/** params: `period` (default 15). value1 = TRIX: the percentage rate-of-change of a triple-smoothed EMA of `close`. */
@Singleton
class TrixCalculator @Inject constructor() : IndicatorCalculator {
    override val type = IndicatorType.TRIX
    override fun compute(candles: List<HistoricalCandleEntity>, params: Map<String, Double>): List<IndicatorPoint> {
        val period = IndicatorMath.requirePeriod(params, default = 15.0)
        val ema1 = IndicatorMath.ema(candles.map { it.close }, period)
        if (ema1.isEmpty()) return emptyList()
        val ema2 = IndicatorMath.ema(ema1, period)
        if (ema2.isEmpty()) return emptyList()
        val ema3 = IndicatorMath.ema(ema2, period)
        if (ema3.size < 2) return emptyList()

        // Each EMA pass consumes (period - 1) more leading values than its input.
        val startCandleIndex = (period - 1) * 3
        val out = mutableListOf<IndicatorPoint>()
        for (i in 1 until ema3.size) {
            val prior = ema3[i - 1]
            val trix = if (prior == 0.0) 0.0 else (ema3[i] - prior) / prior * 100.0
            out += IndicatorPoint(candles[startCandleIndex + i].timestamp, trix)
        }
        return out
    }
}

/**
 * params: `step` (default 0.02), `maxStep` (default 0.2). value1 = SAR, value2 = trend direction
 * (1.0 = uptrend, -1.0 = downtrend). Standard Wells Wilder parabolic SAR: starts assuming an
 * uptrend from the first two candles, flips whenever price crosses the current SAR.
 */
@Singleton
class ParabolicSarCalculator @Inject constructor() : IndicatorCalculator {
    override val type = IndicatorType.PARABOLIC_SAR
    override fun compute(candles: List<HistoricalCandleEntity>, params: Map<String, Double>): List<IndicatorPoint> {
        val step = params["step"] ?: 0.02
        val maxStep = params["maxStep"] ?: 0.2
        if (candles.size < 2) return emptyList()

        var uptrend = candles[1].close >= candles[0].close
        var af = step
        var ep = if (uptrend) candles[0].high else candles[0].low
        var sar = if (uptrend) candles[0].low else candles[0].high

        val out = mutableListOf<IndicatorPoint>()
        out += IndicatorPoint(candles[0].timestamp, sar, if (uptrend) 1.0 else -1.0)

        for (i in 1 until candles.size) {
            var nextSar = sar + af * (ep - sar)
            val candle = candles[i]
            val prev = candles[i - 1]

            if (uptrend) {
                nextSar = minOf(nextSar, prev.low, if (i >= 2) candles[i - 2].low else prev.low)
                if (candle.low < nextSar) {
                    uptrend = false
                    nextSar = ep
                    ep = candle.low
                    af = step
                } else if (candle.high > ep) {
                    ep = candle.high
                    af = minOf(af + step, maxStep)
                }
            } else {
                nextSar = maxOf(nextSar, prev.high, if (i >= 2) candles[i - 2].high else prev.high)
                if (candle.high > nextSar) {
                    uptrend = true
                    nextSar = ep
                    ep = candle.high
                    af = step
                } else if (candle.low < ep) {
                    ep = candle.low
                    af = minOf(af + step, maxStep)
                }
            }

            sar = nextSar
            out += IndicatorPoint(candle.timestamp, sar, if (uptrend) 1.0 else -1.0)
        }
        return out
    }
}

/**
 * params: `tenkanPeriod` (default 9), `kijunPeriod` (default 26), `senkouBPeriod` (default 52).
 * value1 = Tenkan-sen, value2 = Kijun-sen, value3 = Senkou Span A ((Tenkan+Kijun)/2), value4 =
 * Senkou Span B. Values are reported at their natural computation index, NOT displaced forward by
 * `kijunPeriod` the way Ichimoku is conventionally charted -- see [IndicatorType]'s own class
 * docstring on why Chikou Span (the other displaced line) isn't stored at all here; the same
 * reasoning applies to why displacement is left to the consumer/chart layer rather than baked
 * into stored timestamps that wouldn't match any real candle.
 */
@Singleton
class IchimokuCalculator @Inject constructor() : IndicatorCalculator {
    override val type = IndicatorType.ICHIMOKU
    override fun compute(candles: List<HistoricalCandleEntity>, params: Map<String, Double>): List<IndicatorPoint> {
        val tenkanPeriod = (params["tenkanPeriod"] ?: 9.0).toInt().coerceAtLeast(1)
        val kijunPeriod = (params["kijunPeriod"] ?: 26.0).toInt().coerceAtLeast(1)
        val senkouBPeriod = (params["senkouBPeriod"] ?: 52.0).toInt().coerceAtLeast(1)
        val maxPeriod = maxOf(tenkanPeriod, kijunPeriod, senkouBPeriod)
        if (candles.size < maxPeriod) return emptyList()

        fun midpoint(end: Int, period: Int): Double {
            val window = candles.subList(end - period + 1, end + 1)
            return (window.maxOf { it.high } + window.minOf { it.low }) / 2.0
        }

        val out = mutableListOf<IndicatorPoint>()
        for (end in maxPeriod - 1 until candles.size) {
            val tenkan = midpoint(end, tenkanPeriod)
            val kijun = midpoint(end, kijunPeriod)
            val senkouA = (tenkan + kijun) / 2.0
            val senkouB = midpoint(end, senkouBPeriod)
            out += IndicatorPoint(candles[end].timestamp, tenkan, kijun, senkouA, senkouB)
        }
        return out
    }
}

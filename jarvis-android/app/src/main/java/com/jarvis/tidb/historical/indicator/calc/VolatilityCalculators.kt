package com.jarvis.tidb.historical.indicator.calc

import com.jarvis.tidb.core.entity.HistoricalCandleEntity
import com.jarvis.tidb.historical.indicator.entity.IndicatorType
import javax.inject.Inject
import javax.inject.Singleton

/** params: `period` (default 14). value1 = Average True Range via Wilder's smoothing. */
@Singleton
class AtrCalculator @Inject constructor() : IndicatorCalculator {
    override val type = IndicatorType.ATR
    override fun compute(candles: List<HistoricalCandleEntity>, params: Map<String, Double>): List<IndicatorPoint> {
        val period = IndicatorMath.requirePeriod(params)
        if (candles.size < period + 1) return emptyList()
        val trueRanges = trueRangeSeries(candles)
        val atrValues = IndicatorMath.wilderSmooth(trueRanges, period)
        // trueRanges[i] corresponds to candles[i+1]; wilderSmooth output index i corresponds to
        // trueRanges index (period - 1 + i), i.e. candles index (period + i).
        return atrValues.indices.map { i -> IndicatorPoint(candles[period + i].timestamp, atrValues[i]) }
    }

    companion object {
        /** True range for every candle after the first, against the prior candle's close. Shared with SupertrendCalculator so both stay self-contained per calculator, per this package's own "no cross-calculator dependency required" design. */
        fun trueRangeSeries(candles: List<HistoricalCandleEntity>): List<Double> =
            (1 until candles.size).map { i -> IndicatorMath.trueRange(candles[i], candles[i - 1].close) }
    }
}

/** params: `period` (default 20), `stdDevMultiplier` (default 2.0). value1 = upper band, value2 = middle band (SMA), value3 = lower band. */
@Singleton
class BollingerBandsCalculator @Inject constructor() : IndicatorCalculator {
    override val type = IndicatorType.BOLLINGER_BANDS
    override fun compute(candles: List<HistoricalCandleEntity>, params: Map<String, Double>): List<IndicatorPoint> {
        val period = IndicatorMath.requirePeriod(params, default = 20.0)
        val multiplier = params["stdDevMultiplier"] ?: 2.0
        if (candles.size < period) return emptyList()
        val closes = candles.map { it.close }
        val out = mutableListOf<IndicatorPoint>()
        for (end in period - 1 until candles.size) {
            val window = closes.subList(end - period + 1, end + 1)
            val mean = window.average()
            val sd = IndicatorMath.stdDev(window, mean)
            out += IndicatorPoint(candles[end].timestamp, mean + multiplier * sd, mean, mean - multiplier * sd)
        }
        return out
    }
}

/**
 * params: `atrPeriod` (default 10), `multiplier` (default 3.0). value1 = Supertrend line,
 * value2 = trend direction (1.0 = uptrend/line acts as support, -1.0 = downtrend/line acts as
 * resistance) -- the standard Supertrend flip algorithm: start in an assumed uptrend, flip
 * whenever price crosses the current band.
 */
@Singleton
class SupertrendCalculator @Inject constructor() : IndicatorCalculator {
    override val type = IndicatorType.SUPERTREND
    override fun compute(candles: List<HistoricalCandleEntity>, params: Map<String, Double>): List<IndicatorPoint> {
        val atrPeriod = (params["atrPeriod"] ?: 10.0).toInt().coerceAtLeast(1)
        val multiplier = params["multiplier"] ?: 3.0
        if (candles.size < atrPeriod + 1) return emptyList()

        val trueRanges = AtrCalculator.trueRangeSeries(candles)
        val atrValues = IndicatorMath.wilderSmooth(trueRanges, atrPeriod)
        if (atrValues.isEmpty()) return emptyList()
        val startCandleIndex = atrPeriod // first index in `candles` with an ATR value (see AtrCalculator's own offset comment)

        var prevUpperBand = Double.NaN
        var prevLowerBand = Double.NaN
        var prevSupertrend = Double.NaN
        var uptrend = true
        val out = mutableListOf<IndicatorPoint>()

        for (i in atrValues.indices) {
            val candleIndex = startCandleIndex + i
            val candle = candles[candleIndex]
            val mid = (candle.high + candle.low) / 2.0
            val atr = atrValues[i]
            var basicUpper = mid + multiplier * atr
            var basicLower = mid - multiplier * atr

            if (i > 0) {
                val prevClose = candles[candleIndex - 1].close
                basicUpper = if (basicUpper < prevUpperBand || prevClose > prevUpperBand) basicUpper else prevUpperBand
                basicLower = if (basicLower > prevLowerBand || prevClose < prevLowerBand) basicLower else prevLowerBand
            }

            uptrend = when {
                i == 0 -> true
                uptrend && candle.close < basicLower -> false
                !uptrend && candle.close > basicUpper -> true
                else -> uptrend
            }

            val supertrend = if (uptrend) basicLower else basicUpper
            out += IndicatorPoint(candle.timestamp, supertrend, if (uptrend) 1.0 else -1.0)

            prevUpperBand = basicUpper
            prevLowerBand = basicLower
            prevSupertrend = supertrend
        }
        return out
    }
}

/**
 * params: `emaPeriod` (default 20), `atrPeriod` (default 10), `atrMultiplier` (default 2.0).
 * value1 = upper band (EMA + multiplier*ATR), value2 = middle band (EMA), value3 = lower band
 * (EMA - multiplier*ATR).
 */
@Singleton
class KeltnerChannelCalculator @Inject constructor() : IndicatorCalculator {
    override val type = IndicatorType.KELTNER_CHANNEL
    override fun compute(candles: List<HistoricalCandleEntity>, params: Map<String, Double>): List<IndicatorPoint> {
        val emaPeriod = (params["emaPeriod"] ?: 20.0).toInt().coerceAtLeast(1)
        val atrPeriod = (params["atrPeriod"] ?: 10.0).toInt().coerceAtLeast(1)
        val multiplier = params["atrMultiplier"] ?: 2.0

        val emaValues = IndicatorMath.ema(candles.map { it.close }, emaPeriod)
        val trueRanges = AtrCalculator.trueRangeSeries(candles)
        val atrValues = IndicatorMath.wilderSmooth(trueRanges, atrPeriod)
        if (emaValues.isEmpty() || atrValues.isEmpty()) return emptyList()

        val emaStart = emaPeriod - 1
        val atrStart = atrPeriod
        val start = maxOf(emaStart, atrStart)
        val out = mutableListOf<IndicatorPoint>()
        var candleIndex = start
        while (candleIndex < candles.size) {
            val emaIdx = candleIndex - emaStart
            val atrIdx = candleIndex - atrStart
            if (emaIdx >= emaValues.size || atrIdx >= atrValues.size) break
            val ema = emaValues[emaIdx]
            val atr = atrValues[atrIdx]
            out += IndicatorPoint(candles[candleIndex].timestamp, ema + multiplier * atr, ema, ema - multiplier * atr)
            candleIndex++
        }
        return out
    }
}

/** params: `period` (default 20). value1 = upper (highest high over period), value2 = lower (lowest low over period), value3 = middle ((upper+lower)/2). */
@Singleton
class DonchianChannelCalculator @Inject constructor() : IndicatorCalculator {
    override val type = IndicatorType.DONCHIAN_CHANNEL
    override fun compute(candles: List<HistoricalCandleEntity>, params: Map<String, Double>): List<IndicatorPoint> {
        val period = IndicatorMath.requirePeriod(params, default = 20.0)
        if (candles.size < period) return emptyList()
        val out = mutableListOf<IndicatorPoint>()
        for (end in period - 1 until candles.size) {
            val window = candles.subList(end - period + 1, end + 1)
            val upper = window.maxOf { it.high }
            val lower = window.minOf { it.low }
            out += IndicatorPoint(candles[end].timestamp, upper, lower, (upper + lower) / 2.0)
        }
        return out
    }
}

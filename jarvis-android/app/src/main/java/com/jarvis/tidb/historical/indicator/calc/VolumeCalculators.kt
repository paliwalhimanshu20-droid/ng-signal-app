package com.jarvis.tidb.historical.indicator.calc

import com.jarvis.tidb.core.entity.HistoricalCandleEntity
import com.jarvis.tidb.historical.indicator.entity.IndicatorType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * No params. value1 = On-Balance Volume: a running cumulative total, +volume on an up close,
 * -volume on a down close, unchanged on a flat close. The cumulative total is relative to the
 * start of the supplied `candles` list, not an absolute all-time total -- OBV is only ever
 * meaningful as a trend of its own shape, not an absolute level, so this matches how every
 * charting platform actually uses it.
 */
@Singleton
class ObvCalculator @Inject constructor() : IndicatorCalculator {
    override val type = IndicatorType.OBV
    override fun compute(candles: List<HistoricalCandleEntity>, params: Map<String, Double>): List<IndicatorPoint> {
        if (candles.isEmpty()) return emptyList()
        var obv = 0.0
        val out = mutableListOf(IndicatorPoint(candles[0].timestamp, obv))
        for (i in 1 until candles.size) {
            obv += when {
                candles[i].close > candles[i - 1].close -> candles[i].volume.toDouble()
                candles[i].close < candles[i - 1].close -> -candles[i].volume.toDouble()
                else -> 0.0
            }
            out += IndicatorPoint(candles[i].timestamp, obv)
        }
        return out
    }
}

/** params: `period` (default 20). value1 = Chaikin Money Flow: sum(moneyFlowVolume) / sum(volume) over `period`, where moneyFlowVolume = ((close-low)-(high-close))/(high-low) * volume. */
@Singleton
class CmfCalculator @Inject constructor() : IndicatorCalculator {
    override val type = IndicatorType.CMF
    override fun compute(candles: List<HistoricalCandleEntity>, params: Map<String, Double>): List<IndicatorPoint> {
        val period = IndicatorMath.requirePeriod(params, default = 20.0)
        if (candles.size < period) return emptyList()
        val moneyFlowVolume = candles.map { c ->
            val range = c.high - c.low
            val multiplier = if (range == 0.0) 0.0 else ((c.close - c.low) - (c.high - c.close)) / range
            multiplier * c.volume
        }
        val out = mutableListOf<IndicatorPoint>()
        for (end in period - 1 until candles.size) {
            val mfvSum = moneyFlowVolume.subList(end - period + 1, end + 1).sum()
            val volumeSum = candles.subList(end - period + 1, end + 1).sumOf { it.volume.toDouble() }
            val cmf = if (volumeSum == 0.0) 0.0 else mfvSum / volumeSum
            out += IndicatorPoint(candles[end].timestamp, cmf)
        }
        return out
    }
}

/** params: `period` (default 14). value1 = Money Flow Index: 100 - 100/(1 + positiveMoneyFlow/negativeMoneyFlow), using typical price * volume as raw money flow. */
@Singleton
class MfiCalculator @Inject constructor() : IndicatorCalculator {
    override val type = IndicatorType.MFI
    override fun compute(candles: List<HistoricalCandleEntity>, params: Map<String, Double>): List<IndicatorPoint> {
        val period = IndicatorMath.requirePeriod(params)
        if (candles.size < period + 1) return emptyList()
        val typicalPrices = candles.map { IndicatorMath.typicalPrice(it) }
        val rawMoneyFlow = candles.indices.map { i -> typicalPrices[i] * candles[i].volume }

        val out = mutableListOf<IndicatorPoint>()
        for (end in period until candles.size) {
            var positive = 0.0
            var negative = 0.0
            for (i in (end - period + 1)..end) {
                when {
                    typicalPrices[i] > typicalPrices[i - 1] -> positive += rawMoneyFlow[i]
                    typicalPrices[i] < typicalPrices[i - 1] -> negative += rawMoneyFlow[i]
                }
            }
            val mfi = when {
                negative == 0.0 && positive == 0.0 -> 50.0
                negative == 0.0 -> 100.0
                else -> 100.0 - 100.0 / (1.0 + positive / negative)
            }
            out += IndicatorPoint(candles[end].timestamp, mfi)
        }
        return out
    }
}

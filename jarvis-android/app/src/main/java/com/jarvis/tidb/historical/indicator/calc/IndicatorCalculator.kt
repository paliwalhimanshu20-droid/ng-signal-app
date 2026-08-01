package com.jarvis.tidb.historical.indicator.calc

import com.jarvis.tidb.core.entity.HistoricalCandleEntity
import com.jarvis.tidb.historical.indicator.entity.IndicatorType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Universal Indicator Engine" Phase 2: the calculation layer the storage warehouse
 * ([com.jarvis.tidb.historical.indicator.repository.IndicatorValueRepository]) was always
 * designed around but never had -- that repository's own docstring is explicit that it "never
 * recomputes on read, only returns what a computation run already wrote," which presupposes a
 * computation step that, before this file, did not exist anywhere in the codebase for any of the
 * 10 original indicator types either.
 *
 * One output row per input candle it can produce a value for (an indicator needing `period`
 * candles of warmup naturally produces its first output at index `period - 1`, not before --
 * callers should expect a shorter, not equal-length, output list for warmup-requiring indicators
 * rather than treating a short list as an error).
 */
data class IndicatorPoint(
    val timestamp: Long,
    val value1: Double,
    val value2: Double? = null,
    val value3: Double? = null,
    val value4: Double? = null,
)

/**
 * One implementation per [IndicatorType]. `params` keys are indicator-specific (documented on
 * each implementation below) and intentionally `Map<String, Double>` rather than a typed data
 * class per indicator -- this is what lets [IndicatorCalculatorRegistry] and, later, the
 * "Universal Parameter Optimization Engine" (Phase 3) treat every indicator uniformly: a
 * calculator is discovered by [type], its parameters are named doubles it interprets itself, and
 * neither this interface nor the registry needs to change when a new indicator is added --
 * exactly the "architecture must allow adding new indicators without changing existing code"
 * requirement. `candles` must be pre-sorted ascending by timestamp for one (instrument,
 * timeframe) -- every implementation below relies on that ordering and does not re-sort.
 */
interface IndicatorCalculator {
    val type: IndicatorType
    fun compute(candles: List<HistoricalCandleEntity>, params: Map<String, Double>): List<IndicatorPoint>
}

/**
 * Discovers every bound [IndicatorCalculator] by [IndicatorCalculator.type] -- see
 * [com.jarvis.tidb.di.IndicatorCalculatorModule] for the `@Binds @IntoSet` wiring, the same
 * swap-point pattern already established by
 * [com.jarvis.os.app.core.intelligence.localintent.LocalIntentRouter] and
 * [com.jarvis.os.app.core.chat.ChatProvider]/AiRouter elsewhere in this codebase: a new indicator
 * is (1) implement [IndicatorCalculator], (2) add one `@Binds @IntoSet` line -- this class never
 * needs to change.
 */
@Singleton
class IndicatorCalculatorRegistry @Inject constructor(
    calculators: Set<@JvmSuppressWildcards IndicatorCalculator>,
) {
    private val byType: Map<IndicatorType, IndicatorCalculator> = calculators.associateBy { it.type }

    fun forType(type: IndicatorType): IndicatorCalculator? = byType[type]

    fun compute(type: IndicatorType, candles: List<HistoricalCandleEntity>, params: Map<String, Double>): List<IndicatorPoint> =
        forType(type)?.compute(candles, params)
            ?: throw IllegalStateException("No IndicatorCalculator registered for $type -- add one and bind it in IndicatorCalculatorModule.")

    val supportedTypes: Set<IndicatorType> get() = byType.keys
}

/** Shared math every calculator below builds on -- kept here once rather than reimplemented per indicator. */
internal object IndicatorMath {
    fun typicalPrice(c: HistoricalCandleEntity): Double = (c.high + c.low + c.close) / 3.0

    fun trueRange(current: HistoricalCandleEntity, previousClose: Double?): Double {
        if (previousClose == null) return current.high - current.low
        return maxOf(current.high - current.low, kotlin.math.abs(current.high - previousClose), kotlin.math.abs(current.low - previousClose))
    }

    /** Wilder's smoothing (used by RSI/ATR/ADX/DMI): first value is a simple average of the first `period` inputs, every value after is `(prev * (period - 1) + next) / period`. */
    fun wilderSmooth(values: List<Double>, period: Int): List<Double> {
        if (values.size < period) return emptyList()
        val out = mutableListOf(values.take(period).average())
        for (i in period until values.size) {
            out += (out.last() * (period - 1) + values[i]) / period
        }
        return out
    }

    /** Standard EMA (used directly by EMA/MACD/Keltner/TRIX, and indirectly by wilderSmooth's cousins). Seeded with the SMA of the first `period` values, as is conventional. */
    fun ema(values: List<Double>, period: Int): List<Double> {
        if (values.size < period) return emptyList()
        val multiplier = 2.0 / (period + 1)
        val out = mutableListOf(values.take(period).average())
        for (i in period until values.size) {
            out += (values[i] - out.last()) * multiplier + out.last()
        }
        return out
    }

    fun stdDev(values: List<Double>, mean: Double): Double =
        kotlin.math.sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)

    fun requirePeriod(params: Map<String, Double>, key: String = "period", default: Double = 14.0): Int =
        (params[key] ?: default).toInt().coerceAtLeast(1)
}

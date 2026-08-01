package com.jarvis.tidb.optimization.searchspace

import com.jarvis.tidb.historical.indicator.entity.IndicatorType
import javax.inject.Inject
import javax.inject.Singleton

/** Namespaced componentId convention -- "INDICATOR:EMA", not just "EMA" -- so Module 4's future entry-rule/exit-rule/filter components (e.g. "EXIT:TRAILING_STOP") can register into the same [SearchSpaceRegistry] without ever colliding with an indicator name. */
object IndicatorComponentId {
    fun of(type: IndicatorType): String = "INDICATOR:${type.name}"
}

/**
 * One [SearchSpaceProvider] per [IndicatorType], directly extending Phase 2's 26 calculators.
 * Every range below uses ONLY the parameter keys the matching
 * [com.jarvis.tidb.historical.indicator.calc.IndicatorCalculator] implementation actually reads
 * (see each calculator's own KDoc "params:" line) -- deliberately excluding parameters the spec's
 * illustrative examples mention but no calculator currently consumes (RSI's overbought/oversold
 * thresholds, Bollinger's "moving average type", Supertrend's "ATR method"): those are downstream
 * signal-generation / entry-rule concerns, not indicator-computation parameters, and belong to
 * Module 4 (Strategy Composition) once it exists to give them real meaning. Registering a
 * search-space dimension nothing reads would be exactly the kind of decorative, non-functional
 * parameter this project has been explicit about not wanting.
 */
@Singleton class SmaSearchSpaceProvider @Inject constructor() : SearchSpaceProvider {
    override val componentId = IndicatorComponentId.of(IndicatorType.SMA)
    override fun searchSpace() = SearchSpace(componentId, listOf(ParameterSpec.ContinuousRange("period", 2.0, 300.0, 1.0)))
}

@Singleton class EmaSearchSpaceProvider @Inject constructor() : SearchSpaceProvider {
    override val componentId = IndicatorComponentId.of(IndicatorType.EMA)
    /** Matches the spec's own explicit example for EMA: "Any period, 2-300". */
    override fun searchSpace() = SearchSpace(componentId, listOf(ParameterSpec.ContinuousRange("period", 2.0, 300.0, 1.0)))
}

@Singleton class WmaSearchSpaceProvider @Inject constructor() : SearchSpaceProvider {
    override val componentId = IndicatorComponentId.of(IndicatorType.WMA)
    override fun searchSpace() = SearchSpace(componentId, listOf(ParameterSpec.ContinuousRange("period", 2.0, 300.0, 1.0)))
}

@Singleton class VwmaSearchSpaceProvider @Inject constructor() : SearchSpaceProvider {
    override val componentId = IndicatorComponentId.of(IndicatorType.VWMA)
    override fun searchSpace() = SearchSpace(componentId, listOf(ParameterSpec.ContinuousRange("period", 2.0, 300.0, 1.0)))
}

@Singleton class VwapSearchSpaceProvider @Inject constructor() : SearchSpaceProvider {
    override val componentId = IndicatorComponentId.of(IndicatorType.VWAP)
    /** VWAP takes no parameters -- see VwapCalculator's own KDoc -- so this is a zero-dimension search space, a legitimate (if trivial) member of the framework. */
    override fun searchSpace() = SearchSpace(componentId, emptyList())
}

@Singleton class RsiSearchSpaceProvider @Inject constructor() : SearchSpaceProvider {
    override val componentId = IndicatorComponentId.of(IndicatorType.RSI)
    override fun searchSpace() = SearchSpace(componentId, listOf(ParameterSpec.ContinuousRange("period", 2.0, 50.0, 1.0)))
}

@Singleton class MacdSearchSpaceProvider @Inject constructor() : SearchSpaceProvider {
    override val componentId = IndicatorComponentId.of(IndicatorType.MACD)
    override fun searchSpace() = SearchSpace(
        componentId,
        listOf(
            ParameterSpec.ContinuousRange("fast", 5.0, 20.0, 1.0),
            ParameterSpec.ContinuousRange("slow", 20.0, 50.0, 1.0),
            ParameterSpec.ContinuousRange("signal", 5.0, 15.0, 1.0),
        ),
    )
}

@Singleton class CciSearchSpaceProvider @Inject constructor() : SearchSpaceProvider {
    override val componentId = IndicatorComponentId.of(IndicatorType.CCI)
    override fun searchSpace() = SearchSpace(componentId, listOf(ParameterSpec.ContinuousRange("period", 5.0, 50.0, 1.0)))
}

@Singleton class RocSearchSpaceProvider @Inject constructor() : SearchSpaceProvider {
    override val componentId = IndicatorComponentId.of(IndicatorType.ROC)
    override fun searchSpace() = SearchSpace(componentId, listOf(ParameterSpec.ContinuousRange("period", 2.0, 50.0, 1.0)))
}

@Singleton class MomentumSearchSpaceProvider @Inject constructor() : SearchSpaceProvider {
    override val componentId = IndicatorComponentId.of(IndicatorType.MOMENTUM)
    override fun searchSpace() = SearchSpace(componentId, listOf(ParameterSpec.ContinuousRange("period", 2.0, 50.0, 1.0)))
}

@Singleton class WilliamsRSearchSpaceProvider @Inject constructor() : SearchSpaceProvider {
    override val componentId = IndicatorComponentId.of(IndicatorType.WILLIAMS_R)
    override fun searchSpace() = SearchSpace(componentId, listOf(ParameterSpec.ContinuousRange("period", 2.0, 50.0, 1.0)))
}

@Singleton class StochasticSearchSpaceProvider @Inject constructor() : SearchSpaceProvider {
    override val componentId = IndicatorComponentId.of(IndicatorType.STOCHASTIC)
    override fun searchSpace() = SearchSpace(
        componentId,
        listOf(
            ParameterSpec.ContinuousRange("kPeriod", 5.0, 30.0, 1.0),
            ParameterSpec.ContinuousRange("dPeriod", 2.0, 10.0, 1.0),
            ParameterSpec.ContinuousRange("slowing", 1.0, 10.0, 1.0),
        ),
    )
}

@Singleton class AtrSearchSpaceProvider @Inject constructor() : SearchSpaceProvider {
    override val componentId = IndicatorComponentId.of(IndicatorType.ATR)
    override fun searchSpace() = SearchSpace(componentId, listOf(ParameterSpec.ContinuousRange("period", 2.0, 50.0, 1.0)))
}

@Singleton class BollingerBandsSearchSpaceProvider @Inject constructor() : SearchSpaceProvider {
    override val componentId = IndicatorComponentId.of(IndicatorType.BOLLINGER_BANDS)
    override fun searchSpace() = SearchSpace(
        componentId,
        listOf(
            ParameterSpec.ContinuousRange("period", 5.0, 50.0, 1.0),
            ParameterSpec.ContinuousRange("stdDevMultiplier", 1.0, 4.0, 0.5),
        ),
    )
}

@Singleton class SupertrendSearchSpaceProvider @Inject constructor() : SearchSpaceProvider {
    override val componentId = IndicatorComponentId.of(IndicatorType.SUPERTREND)
    override fun searchSpace() = SearchSpace(
        componentId,
        listOf(
            ParameterSpec.ContinuousRange("atrPeriod", 5.0, 30.0, 1.0),
            ParameterSpec.ContinuousRange("multiplier", 1.0, 6.0, 0.5),
        ),
    )
}

@Singleton class KeltnerChannelSearchSpaceProvider @Inject constructor() : SearchSpaceProvider {
    override val componentId = IndicatorComponentId.of(IndicatorType.KELTNER_CHANNEL)
    override fun searchSpace() = SearchSpace(
        componentId,
        listOf(
            ParameterSpec.ContinuousRange("emaPeriod", 5.0, 50.0, 1.0),
            ParameterSpec.ContinuousRange("atrPeriod", 5.0, 30.0, 1.0),
            ParameterSpec.ContinuousRange("atrMultiplier", 1.0, 4.0, 0.5),
        ),
    )
}

@Singleton class DonchianChannelSearchSpaceProvider @Inject constructor() : SearchSpaceProvider {
    override val componentId = IndicatorComponentId.of(IndicatorType.DONCHIAN_CHANNEL)
    override fun searchSpace() = SearchSpace(componentId, listOf(ParameterSpec.ContinuousRange("period", 5.0, 60.0, 1.0)))
}

@Singleton class AdxSearchSpaceProvider @Inject constructor() : SearchSpaceProvider {
    override val componentId = IndicatorComponentId.of(IndicatorType.ADX)
    override fun searchSpace() = SearchSpace(componentId, listOf(ParameterSpec.ContinuousRange("period", 5.0, 50.0, 1.0)))
}

@Singleton class DmiSearchSpaceProvider @Inject constructor() : SearchSpaceProvider {
    override val componentId = IndicatorComponentId.of(IndicatorType.DMI)
    override fun searchSpace() = SearchSpace(componentId, listOf(ParameterSpec.ContinuousRange("period", 5.0, 50.0, 1.0)))
}

@Singleton class AroonSearchSpaceProvider @Inject constructor() : SearchSpaceProvider {
    override val componentId = IndicatorComponentId.of(IndicatorType.AROON)
    override fun searchSpace() = SearchSpace(componentId, listOf(ParameterSpec.ContinuousRange("period", 5.0, 50.0, 1.0)))
}

@Singleton class TrixSearchSpaceProvider @Inject constructor() : SearchSpaceProvider {
    override val componentId = IndicatorComponentId.of(IndicatorType.TRIX)
    override fun searchSpace() = SearchSpace(componentId, listOf(ParameterSpec.ContinuousRange("period", 5.0, 50.0, 1.0)))
}

@Singleton class ParabolicSarSearchSpaceProvider @Inject constructor() : SearchSpaceProvider {
    override val componentId = IndicatorComponentId.of(IndicatorType.PARABOLIC_SAR)
    /** Conventionally tuned over a small explicit set, not a fine continuous sweep -- see [ParameterSpec.DiscreteChoices]'s own doc. */
    override fun searchSpace() = SearchSpace(
        componentId,
        listOf(
            ParameterSpec.DiscreteChoices("step", listOf(0.01, 0.02, 0.03, 0.04, 0.05)),
            ParameterSpec.DiscreteChoices("maxStep", listOf(0.1, 0.15, 0.2, 0.25, 0.3)),
        ),
    )
}

@Singleton class IchimokuSearchSpaceProvider @Inject constructor() : SearchSpaceProvider {
    override val componentId = IndicatorComponentId.of(IndicatorType.ICHIMOKU)
    override fun searchSpace() = SearchSpace(
        componentId,
        listOf(
            ParameterSpec.ContinuousRange("tenkanPeriod", 5.0, 20.0, 1.0),
            ParameterSpec.ContinuousRange("kijunPeriod", 15.0, 40.0, 1.0),
            ParameterSpec.ContinuousRange("senkouBPeriod", 30.0, 80.0, 2.0),
        ),
    )
}

@Singleton class ObvSearchSpaceProvider @Inject constructor() : SearchSpaceProvider {
    override val componentId = IndicatorComponentId.of(IndicatorType.OBV)
    /** OBV takes no parameters -- see ObvCalculator's own KDoc. */
    override fun searchSpace() = SearchSpace(componentId, emptyList())
}

@Singleton class CmfSearchSpaceProvider @Inject constructor() : SearchSpaceProvider {
    override val componentId = IndicatorComponentId.of(IndicatorType.CMF)
    override fun searchSpace() = SearchSpace(componentId, listOf(ParameterSpec.ContinuousRange("period", 5.0, 50.0, 1.0)))
}

@Singleton class MfiSearchSpaceProvider @Inject constructor() : SearchSpaceProvider {
    override val componentId = IndicatorComponentId.of(IndicatorType.MFI)
    override fun searchSpace() = SearchSpace(componentId, listOf(ParameterSpec.ContinuousRange("period", 2.0, 50.0, 1.0)))
}

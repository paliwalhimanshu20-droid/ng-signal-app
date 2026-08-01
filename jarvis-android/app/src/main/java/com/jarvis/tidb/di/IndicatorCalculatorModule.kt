package com.jarvis.tidb.di

import com.jarvis.tidb.historical.indicator.calc.AdxCalculator
import com.jarvis.tidb.historical.indicator.calc.AroonCalculator
import com.jarvis.tidb.historical.indicator.calc.AtrCalculator
import com.jarvis.tidb.historical.indicator.calc.BollingerBandsCalculator
import com.jarvis.tidb.historical.indicator.calc.CciCalculator
import com.jarvis.tidb.historical.indicator.calc.CmfCalculator
import com.jarvis.tidb.historical.indicator.calc.DmiCalculator
import com.jarvis.tidb.historical.indicator.calc.DonchianChannelCalculator
import com.jarvis.tidb.historical.indicator.calc.EmaCalculator
import com.jarvis.tidb.historical.indicator.calc.IchimokuCalculator
import com.jarvis.tidb.historical.indicator.calc.IndicatorCalculator
import com.jarvis.tidb.historical.indicator.calc.KeltnerChannelCalculator
import com.jarvis.tidb.historical.indicator.calc.MacdCalculator
import com.jarvis.tidb.historical.indicator.calc.MfiCalculator
import com.jarvis.tidb.historical.indicator.calc.MomentumCalculator
import com.jarvis.tidb.historical.indicator.calc.ObvCalculator
import com.jarvis.tidb.historical.indicator.calc.ParabolicSarCalculator
import com.jarvis.tidb.historical.indicator.calc.RocCalculator
import com.jarvis.tidb.historical.indicator.calc.RsiCalculator
import com.jarvis.tidb.historical.indicator.calc.SmaCalculator
import com.jarvis.tidb.historical.indicator.calc.StochasticCalculator
import com.jarvis.tidb.historical.indicator.calc.SupertrendCalculator
import com.jarvis.tidb.historical.indicator.calc.TrixCalculator
import com.jarvis.tidb.historical.indicator.calc.VwapCalculator
import com.jarvis.tidb.historical.indicator.calc.VwmaCalculator
import com.jarvis.tidb.historical.indicator.calc.WilliamsRCalculator
import com.jarvis.tidb.historical.indicator.calc.WmaCalculator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * "Universal Indicator Engine" Phase 2: the swap point [IndicatorCalculator]'s own docstring
 * promises, mirroring [com.jarvis.os.app.di.ChatProviderModule] / `LocalIntentHandlerModule`'s
 * exact `@Binds @IntoSet` shape already established elsewhere in this codebase.
 * [com.jarvis.tidb.historical.indicator.calc.IndicatorCalculatorRegistry] injects
 * `Set<IndicatorCalculator>`; without this module that set has no contributors. A new indicator
 * is (1) implement [IndicatorCalculator], (2) add one `@Binds @IntoSet` line here -- the registry
 * never needs to change.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class IndicatorCalculatorModule {

    @Binds
    @IntoSet
    abstract fun bindSmaCalculator(impl: SmaCalculator): IndicatorCalculator

    @Binds
    @IntoSet
    abstract fun bindEmaCalculator(impl: EmaCalculator): IndicatorCalculator

    @Binds
    @IntoSet
    abstract fun bindWmaCalculator(impl: WmaCalculator): IndicatorCalculator

    @Binds
    @IntoSet
    abstract fun bindVwmaCalculator(impl: VwmaCalculator): IndicatorCalculator

    @Binds
    @IntoSet
    abstract fun bindVwapCalculator(impl: VwapCalculator): IndicatorCalculator

    @Binds
    @IntoSet
    abstract fun bindRsiCalculator(impl: RsiCalculator): IndicatorCalculator

    @Binds
    @IntoSet
    abstract fun bindMacdCalculator(impl: MacdCalculator): IndicatorCalculator

    @Binds
    @IntoSet
    abstract fun bindCciCalculator(impl: CciCalculator): IndicatorCalculator

    @Binds
    @IntoSet
    abstract fun bindRocCalculator(impl: RocCalculator): IndicatorCalculator

    @Binds
    @IntoSet
    abstract fun bindMomentumCalculator(impl: MomentumCalculator): IndicatorCalculator

    @Binds
    @IntoSet
    abstract fun bindWilliamsRCalculator(impl: WilliamsRCalculator): IndicatorCalculator

    @Binds
    @IntoSet
    abstract fun bindStochasticCalculator(impl: StochasticCalculator): IndicatorCalculator

    @Binds
    @IntoSet
    abstract fun bindAtrCalculator(impl: AtrCalculator): IndicatorCalculator

    @Binds
    @IntoSet
    abstract fun bindBollingerBandsCalculator(impl: BollingerBandsCalculator): IndicatorCalculator

    @Binds
    @IntoSet
    abstract fun bindSupertrendCalculator(impl: SupertrendCalculator): IndicatorCalculator

    @Binds
    @IntoSet
    abstract fun bindKeltnerChannelCalculator(impl: KeltnerChannelCalculator): IndicatorCalculator

    @Binds
    @IntoSet
    abstract fun bindDonchianChannelCalculator(impl: DonchianChannelCalculator): IndicatorCalculator

    @Binds
    @IntoSet
    abstract fun bindAdxCalculator(impl: AdxCalculator): IndicatorCalculator

    @Binds
    @IntoSet
    abstract fun bindDmiCalculator(impl: DmiCalculator): IndicatorCalculator

    @Binds
    @IntoSet
    abstract fun bindAroonCalculator(impl: AroonCalculator): IndicatorCalculator

    @Binds
    @IntoSet
    abstract fun bindTrixCalculator(impl: TrixCalculator): IndicatorCalculator

    @Binds
    @IntoSet
    abstract fun bindParabolicSarCalculator(impl: ParabolicSarCalculator): IndicatorCalculator

    @Binds
    @IntoSet
    abstract fun bindIchimokuCalculator(impl: IchimokuCalculator): IndicatorCalculator

    @Binds
    @IntoSet
    abstract fun bindObvCalculator(impl: ObvCalculator): IndicatorCalculator

    @Binds
    @IntoSet
    abstract fun bindCmfCalculator(impl: CmfCalculator): IndicatorCalculator

    @Binds
    @IntoSet
    abstract fun bindMfiCalculator(impl: MfiCalculator): IndicatorCalculator
}

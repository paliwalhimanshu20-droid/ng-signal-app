package com.jarvis.tidb.di

import com.jarvis.tidb.optimization.algorithm.GridSearchAlgorithm
import com.jarvis.tidb.optimization.algorithm.OptimizationAlgorithm
import com.jarvis.tidb.optimization.algorithm.RandomSearchAlgorithm
import com.jarvis.tidb.optimization.searchspace.AdxSearchSpaceProvider
import com.jarvis.tidb.optimization.searchspace.AroonSearchSpaceProvider
import com.jarvis.tidb.optimization.searchspace.AtrSearchSpaceProvider
import com.jarvis.tidb.optimization.searchspace.BollingerBandsSearchSpaceProvider
import com.jarvis.tidb.optimization.searchspace.CciSearchSpaceProvider
import com.jarvis.tidb.optimization.searchspace.CmfSearchSpaceProvider
import com.jarvis.tidb.optimization.searchspace.DmiSearchSpaceProvider
import com.jarvis.tidb.optimization.searchspace.DonchianChannelSearchSpaceProvider
import com.jarvis.tidb.optimization.searchspace.EmaSearchSpaceProvider
import com.jarvis.tidb.optimization.searchspace.IchimokuSearchSpaceProvider
import com.jarvis.tidb.optimization.searchspace.KeltnerChannelSearchSpaceProvider
import com.jarvis.tidb.optimization.searchspace.MacdSearchSpaceProvider
import com.jarvis.tidb.optimization.searchspace.MfiSearchSpaceProvider
import com.jarvis.tidb.optimization.searchspace.MomentumSearchSpaceProvider
import com.jarvis.tidb.optimization.searchspace.ObvSearchSpaceProvider
import com.jarvis.tidb.optimization.searchspace.ParabolicSarSearchSpaceProvider
import com.jarvis.tidb.optimization.searchspace.RocSearchSpaceProvider
import com.jarvis.tidb.optimization.searchspace.RsiSearchSpaceProvider
import com.jarvis.tidb.optimization.searchspace.SearchSpaceProvider
import com.jarvis.tidb.optimization.searchspace.SmaSearchSpaceProvider
import com.jarvis.tidb.optimization.searchspace.StochasticSearchSpaceProvider
import com.jarvis.tidb.optimization.searchspace.SupertrendSearchSpaceProvider
import com.jarvis.tidb.optimization.searchspace.TrixSearchSpaceProvider
import com.jarvis.tidb.optimization.searchspace.VwapSearchSpaceProvider
import com.jarvis.tidb.optimization.searchspace.VwmaSearchSpaceProvider
import com.jarvis.tidb.optimization.searchspace.WilliamsRSearchSpaceProvider
import com.jarvis.tidb.optimization.searchspace.WmaSearchSpaceProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * "Universal Parameter Optimization Engine" (Module 3) + "Optimization Algorithms" (Module 11):
 * the swap point [com.jarvis.tidb.optimization.searchspace.SearchSpaceRegistry] and a future
 * `OptimizationAlgorithmRegistry` promise, same `@Binds @IntoSet` shape as
 * [IndicatorCalculatorModule] / [com.jarvis.os.app.di.ChatProviderModule]. A new indicator's
 * search space, or a new optimization algorithm, is one implementation class plus one line here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class OptimizationModule {

    @Binds
    @IntoSet
    abstract fun bindSmaSearchSpaceProvider(impl: SmaSearchSpaceProvider): SearchSpaceProvider

    @Binds
    @IntoSet
    abstract fun bindEmaSearchSpaceProvider(impl: EmaSearchSpaceProvider): SearchSpaceProvider

    @Binds
    @IntoSet
    abstract fun bindWmaSearchSpaceProvider(impl: WmaSearchSpaceProvider): SearchSpaceProvider

    @Binds
    @IntoSet
    abstract fun bindVwmaSearchSpaceProvider(impl: VwmaSearchSpaceProvider): SearchSpaceProvider

    @Binds
    @IntoSet
    abstract fun bindVwapSearchSpaceProvider(impl: VwapSearchSpaceProvider): SearchSpaceProvider

    @Binds
    @IntoSet
    abstract fun bindRsiSearchSpaceProvider(impl: RsiSearchSpaceProvider): SearchSpaceProvider

    @Binds
    @IntoSet
    abstract fun bindMacdSearchSpaceProvider(impl: MacdSearchSpaceProvider): SearchSpaceProvider

    @Binds
    @IntoSet
    abstract fun bindCciSearchSpaceProvider(impl: CciSearchSpaceProvider): SearchSpaceProvider

    @Binds
    @IntoSet
    abstract fun bindRocSearchSpaceProvider(impl: RocSearchSpaceProvider): SearchSpaceProvider

    @Binds
    @IntoSet
    abstract fun bindMomentumSearchSpaceProvider(impl: MomentumSearchSpaceProvider): SearchSpaceProvider

    @Binds
    @IntoSet
    abstract fun bindWilliamsRSearchSpaceProvider(impl: WilliamsRSearchSpaceProvider): SearchSpaceProvider

    @Binds
    @IntoSet
    abstract fun bindStochasticSearchSpaceProvider(impl: StochasticSearchSpaceProvider): SearchSpaceProvider

    @Binds
    @IntoSet
    abstract fun bindAtrSearchSpaceProvider(impl: AtrSearchSpaceProvider): SearchSpaceProvider

    @Binds
    @IntoSet
    abstract fun bindBollingerBandsSearchSpaceProvider(impl: BollingerBandsSearchSpaceProvider): SearchSpaceProvider

    @Binds
    @IntoSet
    abstract fun bindSupertrendSearchSpaceProvider(impl: SupertrendSearchSpaceProvider): SearchSpaceProvider

    @Binds
    @IntoSet
    abstract fun bindKeltnerChannelSearchSpaceProvider(impl: KeltnerChannelSearchSpaceProvider): SearchSpaceProvider

    @Binds
    @IntoSet
    abstract fun bindDonchianChannelSearchSpaceProvider(impl: DonchianChannelSearchSpaceProvider): SearchSpaceProvider

    @Binds
    @IntoSet
    abstract fun bindAdxSearchSpaceProvider(impl: AdxSearchSpaceProvider): SearchSpaceProvider

    @Binds
    @IntoSet
    abstract fun bindDmiSearchSpaceProvider(impl: DmiSearchSpaceProvider): SearchSpaceProvider

    @Binds
    @IntoSet
    abstract fun bindAroonSearchSpaceProvider(impl: AroonSearchSpaceProvider): SearchSpaceProvider

    @Binds
    @IntoSet
    abstract fun bindTrixSearchSpaceProvider(impl: TrixSearchSpaceProvider): SearchSpaceProvider

    @Binds
    @IntoSet
    abstract fun bindParabolicSarSearchSpaceProvider(impl: ParabolicSarSearchSpaceProvider): SearchSpaceProvider

    @Binds
    @IntoSet
    abstract fun bindIchimokuSearchSpaceProvider(impl: IchimokuSearchSpaceProvider): SearchSpaceProvider

    @Binds
    @IntoSet
    abstract fun bindObvSearchSpaceProvider(impl: ObvSearchSpaceProvider): SearchSpaceProvider

    @Binds
    @IntoSet
    abstract fun bindCmfSearchSpaceProvider(impl: CmfSearchSpaceProvider): SearchSpaceProvider

    @Binds
    @IntoSet
    abstract fun bindMfiSearchSpaceProvider(impl: MfiSearchSpaceProvider): SearchSpaceProvider

    @Binds
    @IntoSet
    abstract fun bindGridSearchAlgorithm(impl: GridSearchAlgorithm): OptimizationAlgorithm

    @Binds
    @IntoSet
    abstract fun bindRandomSearchAlgorithm(impl: RandomSearchAlgorithm): OptimizationAlgorithm
}

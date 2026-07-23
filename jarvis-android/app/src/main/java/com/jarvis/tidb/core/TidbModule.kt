package com.jarvis.tidb.core

import android.content.Context
import com.jarvis.tidb.core.repository.ContractRepository
import com.jarvis.tidb.core.repository.ContractRepositoryImpl
import com.jarvis.tidb.core.repository.ExchangeRepository
import com.jarvis.tidb.core.repository.ExchangeRepositoryImpl
import com.jarvis.tidb.core.repository.HistoricalCandleRepository
import com.jarvis.tidb.core.repository.HistoricalCandleRepositoryImpl
import com.jarvis.tidb.core.repository.InstrumentRepository
import com.jarvis.tidb.core.repository.InstrumentRepositoryImpl
import com.jarvis.tidb.core.repository.LiveMarketSnapshotRepository
import com.jarvis.tidb.core.repository.LiveMarketSnapshotRepositoryImpl
import com.jarvis.tidb.core.repository.MarketEventRepository
import com.jarvis.tidb.core.repository.MarketEventRepositoryImpl
import com.jarvis.tidb.core.repository.MarketSessionRepository
import com.jarvis.tidb.core.repository.MarketSessionRepositoryImpl

/**
 * Plain-Kotlin composition root for Module 1. Intentionally framework-agnostic (no Hilt/Koin
 * annotations) so it can be dropped into whatever DI approach the rest of JARVIS ends up
 * standardizing on — swap this class out later; repositories and DAOs don't change.
 */
class TidbModule private constructor(context: Context) {

    private val database: TidbDatabase = TidbDatabase.getInstance(context)

    val exchangeRepository: ExchangeRepository by lazy { ExchangeRepositoryImpl(database.exchangeDao()) }
    val instrumentRepository: InstrumentRepository by lazy { InstrumentRepositoryImpl(database.instrumentDao()) }
    val contractRepository: ContractRepository by lazy { ContractRepositoryImpl(database.contractDao()) }
    val marketSessionRepository: MarketSessionRepository by lazy {
        MarketSessionRepositoryImpl(database.marketSessionDao())
    }
    val historicalCandleRepository: HistoricalCandleRepository by lazy {
        HistoricalCandleRepositoryImpl(database.historicalCandleDao())
    }
    val liveMarketSnapshotRepository: LiveMarketSnapshotRepository by lazy {
        LiveMarketSnapshotRepositoryImpl(database.liveMarketSnapshotDao())
    }
    val marketEventRepository: MarketEventRepository by lazy {
        MarketEventRepositoryImpl(database.marketEventDao())
    }

    companion object {
        @Volatile
        private var INSTANCE: TidbModule? = null

        fun getInstance(context: Context): TidbModule =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: TidbModule(context.applicationContext).also { INSTANCE = it }
            }
    }
}

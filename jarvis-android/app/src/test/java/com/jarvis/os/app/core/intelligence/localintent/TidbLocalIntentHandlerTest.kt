package com.jarvis.os.app.core.intelligence.localintent

import com.jarvis.tidb.core.entity.ContractEntity
import com.jarvis.tidb.core.entity.HistoricalCandleEntity
import com.jarvis.tidb.core.entity.InstrumentEntity
import com.jarvis.tidb.core.entity.LiveMarketSnapshotEntity
import com.jarvis.tidb.core.entity.AssetClass
import com.jarvis.tidb.core.entity.CandleSource
import com.jarvis.tidb.core.entity.InstrumentType
import com.jarvis.tidb.core.entity.Timeframe
import com.jarvis.tidb.core.repository.ContractRepository
import com.jarvis.tidb.core.repository.HistoricalCandleRepository
import com.jarvis.tidb.core.repository.InstrumentRepository
import com.jarvis.tidb.core.repository.LiveMarketSnapshotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Conversation Replay Bug Fix" Requirement 7/8/9: proves the specific questions this bug fix
 * was written around ("what do you have of natural gas?", "what do you have in database?",
 * "show available instruments") are answered locally and deterministically -- never falling
 * through to JarvisCore's AI-bound conversational path, which is where the removed unconditional
 * history injection used to contaminate replies.
 */
class TidbLocalIntentHandlerTest {

    private class FakeInstrumentRepository(private val all: List<InstrumentEntity>) : InstrumentRepository {
        override suspend fun upsert(instrument: InstrumentEntity) = 0L
        override suspend fun getById(instrumentId: Long) = all.firstOrNull { it.instrumentId == instrumentId }
        override suspend fun getByUuid(uuid: String) = all.firstOrNull { it.uuid == uuid }
        override suspend fun getBySymbol(symbol: String) = all.firstOrNull { it.symbol == symbol }
        override suspend fun exists(instrumentId: Long) = all.any { it.instrumentId == instrumentId }
        override fun observeAll(): Flow<List<InstrumentEntity>> = flowOf(all)
        override fun observeByExchange(exchangeId: Long): Flow<List<InstrumentEntity>> = flowOf(all.filter { it.exchangeId == exchangeId })
        override fun observeByAssetClass(assetClass: String): Flow<List<InstrumentEntity>> = flowOf(all.filter { it.assetClass.value == assetClass })
        override suspend fun getWithEvents(instrumentId: Long) = null
        override suspend fun getFullDetail(instrumentId: Long) = null
        override suspend fun softDelete(instrumentId: Long) = Unit
    }

    private class FakeContractRepository(private val nearest: ContractEntity?) : ContractRepository {
        override suspend fun upsert(contract: ContractEntity) = 0L
        override suspend fun upsertAll(contracts: List<ContractEntity>) = emptyList<Long>()
        override suspend fun getById(contractId: Long) = null
        override fun observeByInstrument(instrumentId: Long): Flow<List<ContractEntity>> = flowOf(emptyList())
        override fun observeByInstrumentAndStatus(instrumentId: Long, status: com.jarvis.tidb.core.entity.ContractTradingStatus): Flow<List<ContractEntity>> = flowOf(emptyList())
        override suspend fun getNearestActiveContract(instrumentId: Long) = nearest
        override fun observeExpiringBetween(fromEpochMillis: Long, toEpochMillis: Long): Flow<List<ContractEntity>> = flowOf(emptyList())
        override suspend fun softDelete(contractId: Long) = Unit
    }

    private class FakeHistoricalCandleRepository(private val latestByInstrument: Map<Long, List<HistoricalCandleEntity>>) : HistoricalCandleRepository {
        override suspend fun insert(candle: HistoricalCandleEntity) = 0L
        override suspend fun insertAll(candles: List<HistoricalCandleEntity>) = emptyList<Long>()
        override fun observeRange(instrumentId: Long, timeframe: Timeframe, fromMillis: Long, toMillis: Long): Flow<List<HistoricalCandleEntity>> =
            flowOf(latestByInstrument[instrumentId].orEmpty())
        override suspend fun getLatest(instrumentId: Long, timeframe: Timeframe, limit: Int) = latestByInstrument[instrumentId].orEmpty().take(limit)
        override suspend fun softDeleteByImportBatch(importBatchId: String) = Unit
    }

    private class FakeLiveMarketSnapshotRepository(private val byInstrument: Map<Long, LiveMarketSnapshotEntity>) : LiveMarketSnapshotRepository {
        override suspend fun upsert(snapshot: LiveMarketSnapshotEntity) = 0L
        override fun observeByInstrument(instrumentId: Long): Flow<LiveMarketSnapshotEntity?> = MutableStateFlow(byInstrument[instrumentId])
    }

    private val naturalGas = InstrumentEntity(
        instrumentId = 1L,
        symbol = "NATURALGAS",
        displayName = "Natural Gas",
        exchangeId = 1L,
        assetClass = AssetClass.COMMODITY,
        instrumentType = InstrumentType.FUTURE,
        tickSize = 0.1,
        lotSize = 1250,
        multiplier = 1.0,
        quoteCurrency = "INR",
        tradingCurrency = "INR",
        tradingHours = "09:00-23:30",
    )

    private fun buildHandler(
        instruments: List<InstrumentEntity> = listOf(naturalGas),
        snapshot: LiveMarketSnapshotEntity? = LiveMarketSnapshotEntity(instrumentId = 1L, lastPrice = 245.5),
        candle: HistoricalCandleEntity? = HistoricalCandleEntity(
            instrumentId = 1L, timeframe = Timeframe.D1, timestamp = 0L,
            open = 240.0, high = 250.0, low = 238.0, close = 245.5, volume = 1000L, source = CandleSource.HISTORICAL_IMPORT,
        ),
        contract: ContractEntity? = ContractEntity(instrumentId = 1L, expiryDate = 1L, rollDate = 1L, contractSize = 1250.0, marginRequirement = 0.1),
    ): TidbLocalIntentHandler {
        val candlesByInstrument = if (candle != null) mapOf(1L to listOf(candle)) else emptyMap()
        val snapshotsByInstrument = if (snapshot != null) mapOf(1L to snapshot) else emptyMap()
        return TidbLocalIntentHandler(
            instruments = FakeInstrumentRepository(instruments),
            contracts = FakeContractRepository(contract),
            candles = FakeHistoricalCandleRepository(candlesByInstrument),
            liveSnapshots = FakeLiveMarketSnapshotRepository(snapshotsByInstrument),
        )
    }

    @Test
    fun `what do you have of natural gas resolves locally with real data, never falls through`() = runTest {
        val handler = buildHandler()

        val answer = handler.tryHandle("What do you have of natural gas?")

        assertNotNull("must resolve locally -- this exact question used to fall through to the AI-bound path", answer)
        assertEquals(LocalIntentOutcome.LOCAL_ONLY, answer!!.outcome)
        assertTrue("should identify the instrument", answer.response.contains("Natural Gas"))
        assertTrue("general inventory question should include price", answer.response.contains("Last recorded price"))
        assertTrue("general inventory question should include candle data", answer.response.contains("Latest daily candle"))
        assertTrue("general inventory question should include contract data", answer.response.contains("Nearest active contract"))
        assertTrue("must never mention past conversation", "we recently touched on" !in answer.response.lowercase())
    }

    @Test
    fun `what do you have in database resolves to a database inventory summary`() = runTest {
        val handler = buildHandler()

        val answer = handler.tryHandle("What do you have in database?")

        assertNotNull(answer)
        assertTrue(answer!!.response.contains("instrument"))
        assertTrue(answer.response.contains("historical candle"))
    }

    @Test
    fun `show available instruments lists real seeded instruments`() = runTest {
        val handler = buildHandler()

        val answer = handler.tryHandle("Show available instruments")

        assertNotNull(answer)
        assertTrue(answer!!.response.contains("Natural Gas (NATURALGAS)"))
    }

    @Test
    fun `what modules are available answers from the real schema, not history`() = runTest {
        val handler = buildHandler()

        val answer = handler.tryHandle("What modules are available?")

        assertNotNull(answer)
        assertTrue(answer!!.response.contains("Core Market Foundation"))
    }

    @Test
    fun `an instrument not in the database gets an honest miss, not a guess`() = runTest {
        val handler = buildHandler()

        val answer = handler.tryHandle("What do you have of crude oil?")

        assertNotNull(answer)
        assertTrue(answer!!.response.contains("doesn't match any instrument"))
    }

    @Test
    fun `an unrelated question does not match this handler at all`() = runTest {
        val handler = buildHandler()

        val answer = handler.tryHandle("What's the weather like today?")

        assertNull(answer)
    }
}

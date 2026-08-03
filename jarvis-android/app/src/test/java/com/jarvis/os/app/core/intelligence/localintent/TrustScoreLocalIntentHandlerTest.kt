package com.jarvis.os.app.core.intelligence.localintent

import com.jarvis.os.app.testutil.FakeInstrumentRepository
import com.jarvis.os.app.testutil.fakeTrustScoreCalculator
import com.jarvis.tidb.core.entity.AssetClass
import com.jarvis.tidb.core.entity.InstrumentEntity
import com.jarvis.tidb.core.entity.InstrumentType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 4B Runtime Integration milestone, Goal 2/3 -- direct Trust Score queries. */
class TrustScoreLocalIntentHandlerTest {

    private val naturalGas = InstrumentEntity(
        instrumentId = 1L, symbol = "NATURALGAS", displayName = "Natural Gas", exchangeId = 1L,
        assetClass = AssetClass.COMMODITY, instrumentType = InstrumentType.FUTURE,
        tickSize = 0.1, lotSize = 1250, multiplier = 1.0, quoteCurrency = "INR", tradingCurrency = "INR", tradingHours = "09:00-23:30",
    )

    @Test
    fun `no instrument seeded returns the exact required phrasing, not invented prose`() = runTest {
        val handler = TrustScoreLocalIntentHandler(FakeInstrumentRepository(), fakeTrustScoreCalculator())

        val answer = handler.tryHandle("What is your current trust score?")

        // Goal 3, verbatim requirement: "No Trust Score has been calculated." NOT "we didn't
        // establish one" or any other paraphrase.
        assertTrue(answer!!.response.contains("No Trust Score has been calculated"))
    }

    @Test
    fun `an empty repository labels only the structurally-missing engines NOT_IMPLEMENTED`() = runTest {
        val handler = TrustScoreLocalIntentHandler(FakeInstrumentRepository(listOf(naturalGas)), fakeTrustScoreCalculator())

        val text = handler.tryHandle("What is your current trust score?")!!.response

        // Backtests and Paper Trading: no execution-engine class exists at all -> NOT_IMPLEMENTED.
        assertTrue(text.contains("BACKTESTS: NOT_IMPLEMENTED"))
        assertTrue(text.contains("PAPER_TRADING: NOT_IMPLEMENTED"))
        // Optimization: the engine class is real and works, it has simply run zero jobs -- must
        // be NO_DATA, never NOT_IMPLEMENTED (regression guard for the bug caught while writing
        // this test -- see TrustScoreLocalIntentHandler's own class docstring).
        assertTrue(text.contains("OPTIMIZATION: NO_DATA"))
        assertTrue(text.contains("HISTORICAL_DATA: NO_DATA"))
        assertTrue(text.contains("LEARNING: NO_DATA"))
        // Never a vague, invented excuse (Goal 3's explicit anti-example).
        assertFalse(text.contains("I need more market data"))
    }

    @Test
    fun `an unrelated question never matches this handler`() = runTest {
        val handler = TrustScoreLocalIntentHandler(FakeInstrumentRepository(), fakeTrustScoreCalculator())

        assertNull(handler.tryHandle("What's the weather like?"))
    }
}

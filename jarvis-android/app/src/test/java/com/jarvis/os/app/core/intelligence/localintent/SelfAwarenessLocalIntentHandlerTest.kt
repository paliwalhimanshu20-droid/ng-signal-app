package com.jarvis.os.app.core.intelligence.localintent

import com.jarvis.os.app.core.intelligence.selfawareness.SelfAwarenessEngine
import com.jarvis.os.app.testutil.emptyCapabilityInventory
import com.jarvis.os.app.testutil.FakeGitHubStatusProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 4B Slice 2, Section 4 -- Mission Status. */
class SelfAwarenessLocalIntentHandlerTest {

    private fun handler(): SelfAwarenessLocalIntentHandler {
        val engine = SelfAwarenessEngine(emptyCapabilityInventory(), FakeGitHubStatusProvider())
        return SelfAwarenessLocalIntentHandler(engine)
    }

    @Test
    fun `where are we reports real capability counts, not invented text`() = runTest {
        val answer = handler().tryHandle("Where are we?")

        assertTrue(answer!!.response.contains("of"))
        assertTrue(answer.response.contains("tracked capabilities are COMPLETE"))
    }

    @Test
    fun `what is missing lists honest MISSING capabilities with their reasons`() = runTest {
        val answer = handler().tryHandle("What is missing?")

        assertTrue(answer!!.response.contains("Backtest Execution Engine"))
        assertTrue(answer.response.contains("Live Trading"))
    }

    @Test
    fun `what is blocking live trading names real dependency capabilities`() = runTest {
        val answer = handler().tryHandle("What's blocking live trading?")

        assertTrue(answer!!.response.contains("Backtest Execution Engine"))
        // Regression guard: Live Trading's dependency string must name capabilities using their
        // exact SystemCapabilityRecord.name, or SelfAwarenessEngine.whatIsBlockingLiveTrading()'s
        // substring match silently drops real blockers (caught while writing this test).
        assertTrue(answer.response.contains("Massive Optimization Engine"))
    }

    @Test
    fun `an unrelated question never matches this handler`() = runTest {
        assertNull(handler().tryHandle("What's the price of natural gas?"))
    }

    @Test
    fun `does not claim HelpLocalIntentHandler's own exact phrases`() = runTest {
        // "what can you do" is HELP's own CAPABILITY_PHRASES exact match (declared earlier in
        // LocalServiceDomain) -- this handler must stay disjoint from it, not compete for it.
        assertNull(handler().tryHandle("what can you do"))
    }
}

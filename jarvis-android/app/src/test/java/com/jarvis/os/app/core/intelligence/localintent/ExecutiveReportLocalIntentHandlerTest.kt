package com.jarvis.os.app.core.intelligence.localintent

import com.jarvis.os.app.core.intelligence.selfawareness.ExecutiveReportEngine
import com.jarvis.os.app.core.intelligence.selfawareness.SelfAwarenessEngine
import com.jarvis.os.app.testutil.FakeGitHubStatusProvider
import com.jarvis.os.app.testutil.FakeInstrumentRepository
import com.jarvis.os.app.testutil.emptyCapabilityInventory
import com.jarvis.os.app.testutil.fakeTrustScoreCalculator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 4B Slice 2, Section 3 -- Executive Report Engine, routed locally. */
class ExecutiveReportLocalIntentHandlerTest {

    private fun handler(): ExecutiveReportLocalIntentHandler {
        val selfAwareness = SelfAwarenessEngine(emptyCapabilityInventory(), FakeGitHubStatusProvider())
        val engine = ExecutiveReportEngine(selfAwareness, FakeInstrumentRepository(), fakeTrustScoreCalculator())
        return ExecutiveReportLocalIntentHandler(engine)
    }

    @Test
    fun `generates a real report with no generic AI text placeholder`() = runTest {
        val answer = handler().tryHandle("Give me an executive report")

        val text = answer!!.response
        assertTrue(text.contains("EXECUTIVE REPORT"))
        assertTrue(text.contains("Completed ("))
        assertTrue(text.contains("Missing ("))
        assertTrue(text.contains("Next Milestone:"))
        // Every named item in the report must be a real capability name, never invented prose --
        // spot-check one honest MISSING capability actually appears.
        assertTrue(text.contains("Backtest Execution Engine"))
    }

    @Test
    fun `an unrelated question never matches this handler`() = runTest {
        assertNull(handler().tryHandle("What's the price of natural gas?"))
    }
}

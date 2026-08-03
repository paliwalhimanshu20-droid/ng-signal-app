package com.jarvis.os.app.core.intelligence.selfawareness

import com.jarvis.os.app.data.model.CapabilityStatus
import com.jarvis.os.app.data.repository.GitHubFetchResult
import com.jarvis.os.app.data.repository.GitHubStatus
import com.jarvis.os.app.testutil.FakeGitHubStatusProvider
import com.jarvis.os.app.testutil.capabilityInventory
import com.jarvis.tidb.core.entity.AssetClass
import com.jarvis.tidb.core.entity.InstrumentEntity
import com.jarvis.tidb.core.entity.InstrumentType
import com.jarvis.tidb.optimization.entity.OptimizationJobEntity
import com.jarvis.tidb.optimization.entity.OptimizationJobStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Phase 4B Slice 2, Section 2 -- Capability Inventory.
 *
 * Runtime Integration repair: this test previously duplicated its own private fake repository
 * classes instead of reusing [com.jarvis.os.app.testutil.capabilityInventory] -- see that file's
 * class docstring for why that duplication is exactly the shape of bug this repair pass fixed
 * (a stale, unmaintained second copy of [com.jarvis.tidb.analytics.repository.LearningRepository]'s
 * fake, plus a duplicate `FakeGitHubStatusProvider` class colliding with the pre-existing one).
 * Now there is exactly one fake per repository interface in the whole test source set.
 */
class CapabilityInventoryTest {

    private val naturalGas = InstrumentEntity(
        instrumentId = 1L, symbol = "NATURALGAS", displayName = "Natural Gas", exchangeId = 1L,
        assetClass = AssetClass.COMMODITY, instrumentType = InstrumentType.FUTURE,
        tickSize = 0.1, lotSize = 1250, multiplier = 1.0, quoteCurrency = "INR", tradingCurrency = "INR", tradingHours = "09:00-23:30",
    )

    @Test
    fun `backtest engine is reported MISSING even when rows exist, since no execution engine class exists`() = runTest {
        val backtest = com.jarvis.tidb.analytics.entity.BacktestEntity(rowId = 1L, uuid = "u1", name = "manual", strategyId = "s1", periodStart = 0L, periodEnd = 100L, instrumentIdsCsv = "1")
        val caps = capabilityInventory(backtests = listOf(backtest)).snapshot()
        val backtestCap = caps.first { it.name == "Backtest Execution Engine" }

        assertEquals(CapabilityStatus.MISSING, backtestCap.status)
        assertEquals(0, backtestCap.completionPercent)
        assertTrue(backtestCap.verificationState.contains("1 backtest record"))
    }

    @Test
    fun `optimization engine reflects real job completion counts`() = runTest {
        val job = OptimizationJobEntity(
            rowId = 1L, componentId = "INDICATOR:EMA", algorithmId = "GRID_SEARCH", instrumentId = 1L,
            timeframeValue = "1d", periodStart = 0L, periodEnd = 100L, budget = 400,
            statusValue = OptimizationJobStatus.COMPLETED.name, totalCombinations = 299, completedCombinations = 299,
        )
        val caps = capabilityInventory(jobs = listOf(job)).snapshot()
        val optCap = caps.first { it.name == "Massive Optimization Engine" }

        assertEquals(CapabilityStatus.COMPLETE, optCap.status)
    }

    @Test
    fun `optimization engine is MISSING with zero completion when no jobs exist`() = runTest {
        val optCap = capabilityInventory().snapshot().first { it.name == "Massive Optimization Engine" }

        assertEquals(CapabilityStatus.MISSING, optCap.status)
        assertEquals(0, optCap.completionPercent)
    }

    @Test
    fun `historical market data platform is COMPLETE once at least one instrument is ingested`() = runTest {
        val historicalCap = capabilityInventory(instruments = listOf(naturalGas)).snapshot()
            .first { it.name == "Historical Market Data Platform" }

        assertEquals(CapabilityStatus.COMPLETE, historicalCap.status)
    }

    @Test
    fun `live trading is always MISSING and names its real blockers using exact capability names`() = runTest {
        val caps = capabilityInventory().snapshot()
        val liveTrading = caps.first { it.name == "Live Trading" }

        assertEquals(CapabilityStatus.MISSING, liveTrading.status)
        // Regression guard: every name in this dependency string must be a real
        // SystemCapabilityRecord.name, exactly as it appears in the inventory -- a mismatch here
        // silently breaks SelfAwarenessEngine.whatIsBlockingLiveTrading()'s substring match.
        for (blockerName in listOf("Backtest Execution Engine", "Massive Optimization Engine", "Paper Trading Loop", "Trust Layer (Phase 4B Slice 1)")) {
            assertTrue("dependency string must name '$blockerName' exactly", liveTrading.dependency!!.contains(blockerName))
            assertTrue("'$blockerName' must be a real capability in the inventory", caps.any { it.name == blockerName })
        }
    }

    @Test
    fun `deployment center reflects real GitHub connection state`() = runTest {
        val connectedProvider = FakeGitHubStatusProvider()
        connectedProvider.status.value = GitHubFetchResult.Success(
            GitHubStatus(
                repoFullName = "owner/repo", defaultBranch = "main", openPullRequestCount = 0,
                recentPullRequestTitles = emptyList(), openIssueCount = 0, recentWorkflowRuns = emptyList(),
                recentCommits = emptyList(), fetchedAt = Instant.now(),
            ),
        )
        val connected = capabilityInventory(gitHub = connectedProvider).snapshot()
        val notConnected = capabilityInventory().snapshot()

        assertEquals(CapabilityStatus.COMPLETE, connected.first { it.name.startsWith("Deployment Center") }.status)
        assertEquals(CapabilityStatus.PARTIAL, notConnected.first { it.name.startsWith("Deployment Center") }.status)
    }
}

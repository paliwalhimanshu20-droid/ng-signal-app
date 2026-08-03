package com.jarvis.os.app.core.intelligence.selfawareness

import com.jarvis.os.app.core.monitoring.SystemHealthMonitor
import com.jarvis.os.app.core.tools.ToolResult
import com.jarvis.os.app.core.workflow.WorkflowEngine
import com.jarvis.os.app.data.model.CapabilityStatus
import com.jarvis.os.app.data.model.Connection
import com.jarvis.os.app.data.model.ConnectionHealth
import com.jarvis.os.app.data.model.ToolDefinition
import com.jarvis.os.app.data.model.ToolExecutionRecord
import com.jarvis.os.app.data.model.ToolHealthStatus
import com.jarvis.os.app.data.model.WorkflowDefinition
import com.jarvis.os.app.data.model.WorkflowRunRecord
import com.jarvis.os.app.data.model.WorkflowStep
import com.jarvis.os.app.data.repository.ConnectionRepository
import com.jarvis.os.app.data.repository.ConnectionTransition
import com.jarvis.os.app.data.repository.GitHubFetchResult
import com.jarvis.os.app.data.repository.GitHubStatusProvider
import com.jarvis.os.app.data.repository.ToolRepository
import com.jarvis.tidb.analytics.entity.BacktestConfigurationEntity
import com.jarvis.tidb.analytics.entity.BacktestEntity
import com.jarvis.tidb.analytics.entity.BacktestResultEntity
import com.jarvis.tidb.analytics.entity.BacktestRunEntity
import com.jarvis.tidb.analytics.entity.BacktestRunWithDetails
import com.jarvis.tidb.analytics.entity.BacktestStatus
import com.jarvis.tidb.analytics.entity.BacktestTradeEntity
import com.jarvis.tidb.analytics.entity.LearningObservationEntity
import com.jarvis.tidb.analytics.entity.ObservationSource
import com.jarvis.tidb.analytics.entity.PortfolioAllocationEntity
import com.jarvis.tidb.analytics.entity.PortfolioEntity
import com.jarvis.tidb.analytics.entity.PortfolioPositionEntity
import com.jarvis.tidb.analytics.entity.PortfolioRiskEntity
import com.jarvis.tidb.analytics.entity.PortfolioSnapshotEntity
import com.jarvis.tidb.analytics.entity.PortfolioSnapshotType
import com.jarvis.tidb.analytics.entity.PortfolioWithDetails
import com.jarvis.tidb.analytics.entity.PositionStatus
import com.jarvis.tidb.analytics.repository.BacktestRepository
import com.jarvis.tidb.analytics.repository.LearningRepository
import com.jarvis.tidb.analytics.repository.PortfolioRepository
import com.jarvis.tidb.core.entity.AssetClass
import com.jarvis.tidb.core.entity.InstrumentEntity
import com.jarvis.tidb.core.entity.InstrumentType
import com.jarvis.tidb.core.repository.InstrumentRepository
import com.jarvis.tidb.optimization.entity.OptimizationCombinationEntity
import com.jarvis.tidb.optimization.entity.OptimizationJobEntity
import com.jarvis.tidb.optimization.entity.OptimizationJobStatus
import com.jarvis.tidb.optimization.repository.OptimizationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 4B Slice 2, Section 2 -- Capability Inventory. Every row must trace to a real, fakeable repository signal, never a hardcoded guess. */
class CapabilityInventoryTest {

    // --- Minimal, mostly-empty fakes -- only the methods CapabilityInventory actually calls have real behavior. ---

    private class FakeInstrumentRepository(private val all: List<InstrumentEntity>) : InstrumentRepository {
        override suspend fun upsert(instrument: InstrumentEntity) = 0L
        override suspend fun getById(instrumentId: Long) = all.firstOrNull { it.instrumentId == instrumentId }
        override suspend fun getByUuid(uuid: String) = all.firstOrNull { it.uuid == uuid }
        override suspend fun getBySymbol(symbol: String) = all.firstOrNull { it.symbol == symbol }
        override suspend fun exists(instrumentId: Long) = all.any { it.instrumentId == instrumentId }
        override fun observeAll(): Flow<List<InstrumentEntity>> = flowOf(all)
        override fun observeByExchange(exchangeId: Long): Flow<List<InstrumentEntity>> = flowOf(emptyList())
        override fun observeByAssetClass(assetClass: String): Flow<List<InstrumentEntity>> = flowOf(emptyList())
        override suspend fun getWithEvents(instrumentId: Long) = null
        override suspend fun getFullDetail(instrumentId: Long) = null
        override suspend fun softDelete(instrumentId: Long) = Unit
    }

    private class FakeOptimizationRepository(private val jobs: List<OptimizationJobEntity>) : OptimizationRepository {
        override suspend fun createJob(componentId: String, algorithmId: String, instrumentId: Long, timeframeValue: String, periodStart: Long, periodEnd: Long, budget: Int, randomSeed: Long?) = throw NotImplementedError()
        override suspend fun getJob(jobRowId: Long) = jobs.firstOrNull { it.rowId == jobRowId }
        override fun observeJob(jobRowId: Long): Flow<OptimizationJobEntity?> = flowOf(jobs.firstOrNull { it.rowId == jobRowId })
        override fun observeAllJobs(): Flow<List<OptimizationJobEntity>> = flowOf(jobs)
        override suspend fun findResumableJobs() = emptyList<OptimizationJobEntity>()
        override suspend fun pendingCombinations(jobRowId: Long) = emptyList<OptimizationCombinationEntity>()
        override suspend fun markJobRunning(jobRowId: Long) {}
        override suspend fun markJobCancelled(jobRowId: Long) {}
        override suspend fun markJobFailed(jobRowId: Long, errorMessage: String) {}
        override suspend fun markCombinationEvaluated(combinationRowId: Long, backtestRunRowId: Long?, backtestResultRowId: Long?) {}
        override suspend fun markCombinationFailed(combinationRowId: Long, errorMessage: String) {}
        override suspend fun rankedCombinations(jobRowId: Long) = emptyList<OptimizationCombinationEntity>()
        override suspend fun rankCombinations(jobRowId: Long, rankedRowIdsBestFirst: List<Long>) {}
    }

    private class FakeBacktestRepository(private val backtests: List<BacktestEntity> = emptyList()) : BacktestRepository {
        override suspend fun createBacktest(backtest: BacktestEntity) = 1L
        override suspend fun updateBacktest(backtest: BacktestEntity) {}
        override suspend fun getBacktest(rowId: Long) = backtests.firstOrNull { it.rowId == rowId }
        override fun observeBacktestsByStrategy(strategyId: String): Flow<List<BacktestEntity>> = flowOf(emptyList())
        override fun observeAllBacktests(): Flow<List<BacktestEntity>> = flowOf(backtests)
        override suspend fun addConfiguration(configuration: BacktestConfigurationEntity) = 1L
        override suspend fun latestConfiguration(backtestRowId: Long): BacktestConfigurationEntity? = null
        override suspend fun startRun(run: BacktestRunEntity) = 1L
        override suspend fun updateRun(run: BacktestRunEntity) {}
        override fun observeRunsByBacktest(backtestRowId: Long): Flow<List<BacktestRunEntity>> = flowOf(emptyList())
        override fun observeRunsByStatus(status: BacktestStatus): Flow<List<BacktestRunEntity>> = flowOf(emptyList())
        override suspend fun getRunWithDetails(runRowId: Long): BacktestRunWithDetails? = null
        override suspend fun recordGeneratedTrades(trades: List<BacktestTradeEntity>) = emptyList<Long>()
        override fun observeTradesByRun(runRowId: Long): Flow<List<BacktestTradeEntity>> = flowOf(emptyList())
        override suspend fun recordResult(result: BacktestResultEntity) = 1L
        override suspend fun getResultForRun(runRowId: Long): BacktestResultEntity? = null
        override fun observeResultsByBacktest(backtestRowId: Long): Flow<List<BacktestResultEntity>> = flowOf(emptyList())
    }

    private class FakeLearningRepository(private val observations: List<LearningObservationEntity> = emptyList()) : LearningRepository {
        override suspend fun recordObservation(observation: LearningObservationEntity) = 1L
        override fun observeObservations(): Flow<List<LearningObservationEntity>> = flowOf(observations)
        override fun observeObservationsByTrade(tradeRowId: Long) = flowOf(emptyList<LearningObservationEntity>())
        override fun observeObservationsByInstrument(instrumentId: Long) = flowOf(emptyList<LearningObservationEntity>())
        override fun observeObservationsByStrategy(strategyId: String) = flowOf(emptyList<LearningObservationEntity>())
        override fun observeObservationsByMinConfidence(minConfidence: Double) = flowOf(emptyList<LearningObservationEntity>())
        override suspend fun recordInsight(insight: com.jarvis.tidb.analytics.entity.LearningInsightEntity) = 1L
        override fun observeInsights() = flowOf(emptyList<com.jarvis.tidb.analytics.entity.LearningInsightEntity>())
        override fun observeInsightsByCategory(category: String) = flowOf(emptyList<com.jarvis.tidb.analytics.entity.LearningInsightEntity>())
        override suspend fun proposeSuggestion(suggestion: com.jarvis.tidb.analytics.entity.OptimizationSuggestionEntity) = 1L
        override suspend fun updateSuggestionStatus(rowId: Long, status: com.jarvis.tidb.analytics.entity.SuggestionStatus, reviewedBy: String) {}
        override fun observeSuggestions() = flowOf(emptyList<com.jarvis.tidb.analytics.entity.OptimizationSuggestionEntity>())
    }

    private class FakePortfolioRepository(
        private val portfolios: List<PortfolioEntity> = emptyList(),
        private val positionsByPortfolio: Map<Long, List<PortfolioPositionEntity>> = emptyMap(),
    ) : PortfolioRepository {
        override suspend fun createPortfolio(portfolio: PortfolioEntity) = 1L
        override suspend fun updatePortfolio(portfolio: PortfolioEntity) {}
        override suspend fun getPortfolio(rowId: Long) = portfolios.firstOrNull { it.rowId == rowId }
        override fun observePortfolios(): Flow<List<PortfolioEntity>> = flowOf(portfolios)
        override fun observePortfolioWithDetails(rowId: Long): Flow<PortfolioWithDetails?> = flowOf(null)
        override suspend fun openPosition(position: PortfolioPositionEntity) = 1L
        override suspend fun updatePosition(position: PortfolioPositionEntity) {}
        override fun observePositions(portfolioRowId: Long, status: PositionStatus): Flow<List<PortfolioPositionEntity>> =
            flowOf((positionsByPortfolio[portfolioRowId] ?: emptyList()).filter { it.status == status })
        override fun observePositionsByInstrument(instrumentId: Long): Flow<List<PortfolioPositionEntity>> = flowOf(emptyList())
        override suspend fun recordAllocation(allocation: PortfolioAllocationEntity) = 1L
        override fun observeAllocations(portfolioRowId: Long) = flowOf(emptyList<PortfolioAllocationEntity>())
        override suspend fun latestAllocation(portfolioRowId: Long, scopeKey: String): PortfolioAllocationEntity? = null
        override suspend fun recordRiskSnapshot(risk: PortfolioRiskEntity) = 1L
        override fun observeRiskSnapshots(portfolioRowId: Long) = flowOf(emptyList<PortfolioRiskEntity>())
        override suspend fun latestRiskSnapshot(portfolioRowId: Long): PortfolioRiskEntity? = null
        override suspend fun recordCapitalMovement(movement: com.jarvis.tidb.analytics.entity.CapitalMovementEntity) = 1L
        override fun observeCapitalMovements(portfolioRowId: Long) = flowOf(emptyList<com.jarvis.tidb.analytics.entity.CapitalMovementEntity>())
        override fun observeCapitalMovementsByRange(portfolioRowId: Long, startMillis: Long, endMillis: Long) = flowOf(emptyList<com.jarvis.tidb.analytics.entity.CapitalMovementEntity>())
        override suspend fun captureSnapshot(snapshot: PortfolioSnapshotEntity) = 1L
        override fun observeSnapshots(portfolioRowId: Long) = flowOf(emptyList<PortfolioSnapshotEntity>())
        override fun observeSnapshotsByType(portfolioRowId: Long, type: PortfolioSnapshotType) = flowOf(emptyList<PortfolioSnapshotEntity>())
        override fun observeSnapshotsByRange(portfolioRowId: Long, startMillis: Long, endMillis: Long) = flowOf(emptyList<PortfolioSnapshotEntity>())
        override suspend fun latestSnapshot(portfolioRowId: Long): PortfolioSnapshotEntity? = null
    }

    private class FakeConnectionRepository : ConnectionRepository {
        override val connections: StateFlow<List<Connection>> = MutableStateFlow(emptyList())
        override val transitions: SharedFlow<ConnectionTransition> = MutableSharedFlow()
        override fun requestConnection(providerId: String, providerName: String, requestedPermissions: Set<com.jarvis.os.app.data.model.PermissionScope>, maximumPermission: com.jarvis.os.app.data.model.PermissionScope, profileTags: Set<String>) = throw NotImplementedError()
        override fun approve(connectionId: String, approvedBy: String) {}
        override fun reject(connectionId: String, reason: String) {}
        override fun connect(connectionId: String) {}
        override fun markConnected(connectionId: String) {}
        override fun markError(connectionId: String, reason: String) {}
        override fun disconnect(connectionId: String, reason: String?) {}
        override fun suspend(connectionId: String, reason: String) {}
        override fun reconnect(connectionId: String) {}
        override fun disableAll(reason: String) {}
        override fun testConnection(connectionId: String) = throw NotImplementedError()
    }

    private class FakeToolRepository : ToolRepository {
        override val tools: StateFlow<List<ToolDefinition>> = MutableStateFlow(emptyList())
        override val health: StateFlow<Map<String, ToolHealthStatus>> = MutableStateFlow(emptyMap())
        override val executionLog: StateFlow<List<ToolExecutionRecord>> = MutableStateFlow(emptyList())
        override fun checkHealth(toolId: String) = ToolHealthStatus.HEALTHY
        override suspend fun execute(toolId: String, input: String, approvalId: String?): ToolResult = throw NotImplementedError()
    }

    private class FakeWorkflowEngine : WorkflowEngine {
        override val runs: StateFlow<List<WorkflowRunRecord>> = MutableStateFlow(emptyList())
        override suspend fun run(definition: WorkflowDefinition, execute: suspend (WorkflowStep) -> Boolean): WorkflowRunRecord = throw NotImplementedError()
    }

    private fun healthMonitor() = SystemHealthMonitor(FakeConnectionRepository(), FakeToolRepository(), FakeWorkflowEngine())

    private val naturalGas = InstrumentEntity(
        instrumentId = 1L, symbol = "NATURALGAS", displayName = "Natural Gas", exchangeId = 1L,
        assetClass = AssetClass.COMMODITY, instrumentType = InstrumentType.FUTURE,
        tickSize = 0.1, lotSize = 1250, multiplier = 1.0, quoteCurrency = "INR", tradingCurrency = "INR", tradingHours = "09:00-23:30",
    )

    private fun inventory(
        instruments: List<InstrumentEntity> = emptyList(),
        jobs: List<OptimizationJobEntity> = emptyList(),
        backtests: List<BacktestEntity> = emptyList(),
        observations: List<LearningObservationEntity> = emptyList(),
        portfolios: List<PortfolioEntity> = emptyList(),
        positions: Map<Long, List<PortfolioPositionEntity>> = emptyMap(),
        gitHub: GitHubStatusProvider,
    ) = CapabilityInventory(
        FakeInstrumentRepository(instruments),
        FakeOptimizationRepository(jobs),
        FakeBacktestRepository(backtests),
        FakeLearningRepository(observations),
        FakePortfolioRepository(portfolios, positions),
        healthMonitor(),
        gitHub,
    )

    private class FakeGitHubStatusProvider(result: GitHubFetchResult?) : GitHubStatusProvider {
        override val status: StateFlow<GitHubFetchResult?> = MutableStateFlow(result)
        override suspend fun refresh() {}
    }

    @Test
    fun `backtest engine is reported MISSING even when rows exist, since no execution engine class exists`() = runTest {
        val backtest = BacktestEntity(rowId = 1L, uuid = "u1", name = "manual", strategyId = "s1", periodStart = 0L, periodEnd = 100L, instrumentIdsCsv = "1")
        val caps = inventory(backtests = listOf(backtest), gitHub = FakeGitHubStatusProvider(null)).snapshot()
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
        val caps = inventory(jobs = listOf(job), gitHub = FakeGitHubStatusProvider(null)).snapshot()
        val optCap = caps.first { it.name == "Massive Optimization Engine" }

        assertEquals(CapabilityStatus.COMPLETE, optCap.status)
    }

    @Test
    fun `optimization engine is MISSING with zero completion when no jobs exist`() = runTest {
        val caps = inventory(gitHub = FakeGitHubStatusProvider(null)).snapshot()
        val optCap = caps.first { it.name == "Massive Optimization Engine" }

        assertEquals(CapabilityStatus.MISSING, optCap.status)
        assertEquals(0, optCap.completionPercent)
    }

    @Test
    fun `historical market data platform is COMPLETE once at least one instrument is ingested`() = runTest {
        val caps = inventory(instruments = listOf(naturalGas), gitHub = FakeGitHubStatusProvider(null)).snapshot()
        val historicalCap = caps.first { it.name == "Historical Market Data Platform" }

        assertEquals(CapabilityStatus.COMPLETE, historicalCap.status)
    }

    @Test
    fun `live trading is always MISSING and names its real blockers as dependencies`() = runTest {
        val caps = inventory(gitHub = FakeGitHubStatusProvider(null)).snapshot()
        val liveTrading = caps.first { it.name == "Live Trading" }

        assertEquals(CapabilityStatus.MISSING, liveTrading.status)
        assertTrue(liveTrading.dependency!!.contains("Backtest Execution Engine"))
    }

    @Test
    fun `deployment center reflects real GitHub connection state`() = runTest {
        val connected = inventory(
            gitHub = FakeGitHubStatusProvider(
                GitHubFetchResult.Success(
                    com.jarvis.os.app.data.repository.GitHubStatus(
                        repoFullName = "owner/repo", defaultBranch = "main", openPullRequestCount = 0,
                        recentPullRequestTitles = emptyList(), openIssueCount = 0, recentWorkflowRuns = emptyList(),
                        recentCommits = emptyList(), fetchedAt = java.time.Instant.now(),
                    ),
                ),
            ),
        ).snapshot()
        val notConnected = inventory(gitHub = FakeGitHubStatusProvider(null)).snapshot()

        assertEquals(CapabilityStatus.COMPLETE, connected.first { it.name.startsWith("Deployment Center") }.status)
        assertEquals(CapabilityStatus.PARTIAL, notConnected.first { it.name.startsWith("Deployment Center") }.status)
    }
}

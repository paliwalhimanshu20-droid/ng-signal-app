package com.jarvis.os.app.testutil

import com.jarvis.os.app.core.intelligence.selfawareness.CapabilityInventory
import com.jarvis.os.app.core.monitoring.SystemHealthMonitor
import com.jarvis.os.app.core.tools.ToolResult
import com.jarvis.os.app.core.workflow.WorkflowEngine
import com.jarvis.os.app.data.model.Connection
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
import com.jarvis.tidb.analytics.entity.CapitalMovementEntity
import com.jarvis.tidb.analytics.entity.LearningInsightEntity
import com.jarvis.tidb.analytics.entity.LearningObservationEntity
import com.jarvis.tidb.analytics.entity.OptimizationSuggestionEntity
import com.jarvis.tidb.analytics.entity.PortfolioAllocationEntity
import com.jarvis.tidb.analytics.entity.PortfolioEntity
import com.jarvis.tidb.analytics.entity.PortfolioPositionEntity
import com.jarvis.tidb.analytics.entity.PortfolioRiskEntity
import com.jarvis.tidb.analytics.entity.PortfolioSnapshotEntity
import com.jarvis.tidb.analytics.entity.PortfolioSnapshotType
import com.jarvis.tidb.analytics.entity.PortfolioWithDetails
import com.jarvis.tidb.analytics.entity.PositionStatus
import com.jarvis.tidb.analytics.entity.SuggestionStatus
import com.jarvis.tidb.analytics.repository.BacktestRepository
import com.jarvis.tidb.analytics.repository.LearningRepository
import com.jarvis.tidb.analytics.repository.PortfolioRepository
import com.jarvis.tidb.core.entity.InstrumentEntity
import com.jarvis.tidb.core.repository.InstrumentRepository
import com.jarvis.tidb.optimization.entity.OptimizationCombinationEntity
import com.jarvis.tidb.optimization.entity.OptimizationJobEntity
import com.jarvis.tidb.optimization.repository.OptimizationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Phase 4B Slice 2: shared "all real subsystems, empty/default state" fakes for
 * [CapabilityInventory]-dependent tests -- [com.jarvis.os.app.core.intelligence.selfawareness
 * .SelfAwarenessEngineTest] and the two new local intent handler tests all need the exact same
 * seven-dependency wiring; kept here once rather than duplicated per file.
 */
private class FakeInstrumentRepository(private val all: List<InstrumentEntity> = emptyList()) : InstrumentRepository {
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

private class FakeOptimizationRepository(private val jobs: List<OptimizationJobEntity> = emptyList()) : OptimizationRepository {
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
    override suspend fun recordInsight(insight: LearningInsightEntity) = 1L
    override fun observeInsights() = flowOf(emptyList<LearningInsightEntity>())
    override fun observeInsightsByCategory(category: String) = flowOf(emptyList<LearningInsightEntity>())
    override suspend fun proposeSuggestion(suggestion: OptimizationSuggestionEntity) = 1L
    override suspend fun updateSuggestionStatus(rowId: Long, status: SuggestionStatus, reviewedBy: String) {}
    override fun observeSuggestions() = flowOf(emptyList<OptimizationSuggestionEntity>())
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
    override suspend fun recordCapitalMovement(movement: CapitalMovementEntity) = 1L
    override fun observeCapitalMovements(portfolioRowId: Long) = flowOf(emptyList<CapitalMovementEntity>())
    override fun observeCapitalMovementsByRange(portfolioRowId: Long, startMillis: Long, endMillis: Long) = flowOf(emptyList<CapitalMovementEntity>())
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

class FakeGitHubStatusProvider(result: GitHubFetchResult? = null) : GitHubStatusProvider {
    override val status: StateFlow<GitHubFetchResult?> = MutableStateFlow(result)
    override suspend fun refresh() {}
}

/** An empty-state, all-real-signatures [CapabilityInventory] -- every capability reads as its honest "not yet built/no data" default. */
fun emptyCapabilityInventory(gitHub: GitHubStatusProvider = FakeGitHubStatusProvider()): CapabilityInventory = CapabilityInventory(
    FakeInstrumentRepository(),
    FakeOptimizationRepository(),
    FakeBacktestRepository(),
    FakeLearningRepository(),
    FakePortfolioRepository(),
    SystemHealthMonitor(FakeConnectionRepository(), FakeToolRepository(), FakeWorkflowEngine()),
    gitHub,
)

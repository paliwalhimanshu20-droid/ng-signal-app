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
import com.jarvis.tidb.analytics.entity.EvidenceSourceType
import com.jarvis.tidb.analytics.entity.FailureAnalysisEntity
import com.jarvis.tidb.analytics.entity.LearningEntityType
import com.jarvis.tidb.analytics.entity.LearningEvidenceLinkEntity
import com.jarvis.tidb.analytics.entity.LearningInsightEntity
import com.jarvis.tidb.analytics.entity.LearningObservationEntity
import com.jarvis.tidb.analytics.entity.OptimizationSuggestionEntity
import com.jarvis.tidb.analytics.entity.PatternDiscoveryEntity
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
 * Phase 4B Slice 2/3: shared fakes for [CapabilityInventory]-dependent tests.
 *
 * Runtime Integration milestone fix: this file previously declared its OWN `FakeGitHubStatusProvider`
 * -- a duplicate, same-package redeclaration of the ALREADY-EXISTING [FakeGitHubStatusProvider] in
 * this same `testutil` package (a genuine compile error: two public top-level classes, one name,
 * one package). Removed here; every fake in this file now reuses the pre-existing canonical one
 * instead, per this codebase's own "search, reuse, extend" rule -- the exact rule this bug was a
 * failure to follow the first time.
 *
 * Also fixes [FakeLearningRepository]: it implemented an earlier, smaller version of
 * [LearningRepository] and was never updated when that interface grew Pattern Discovery and
 * Failure Analysis methods (`recordPattern`, `observeFailureAnalysesByCategory`, `linkEvidence`,
 * etc.) -- a real "interface moved, fake didn't" break, not a hypothetical one; every member below
 * is checked against the CURRENT interface as of this repair pass, not copied from memory.
 *
 * Internal (not private) so [CapabilityInventoryTest] can inject its own data into the SAME fake
 * classes via [capabilityInventory] rather than maintaining a second, independently-drifting copy
 * -- the maintenance shape that produced this file's own bugs in the first place.
 */
internal class FakeInstrumentRepository(private val all: List<InstrumentEntity> = emptyList()) : InstrumentRepository {
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

internal class FakeOptimizationRepository(private val jobs: List<OptimizationJobEntity> = emptyList()) : OptimizationRepository {
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

internal class FakeBacktestRepository(private val backtests: List<BacktestEntity> = emptyList()) : BacktestRepository {
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

/** Checked against the FULL current [LearningRepository] interface (18 members) as of this repair -- see this file's class docstring. */
internal class FakeLearningRepository(private val observations: List<LearningObservationEntity> = emptyList()) : LearningRepository {
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
    override fun observeSuggestionsByStatus(status: SuggestionStatus) = flowOf(emptyList<OptimizationSuggestionEntity>())
    override suspend fun recordPattern(pattern: PatternDiscoveryEntity) = 1L
    override suspend fun latestPattern(patternKey: String): PatternDiscoveryEntity? = null
    override fun observePatterns() = flowOf(emptyList<PatternDiscoveryEntity>())
    override suspend fun recordFailureAnalysis(analysis: FailureAnalysisEntity) = 1L
    override fun observeFailureAnalysesByTrade(tradeRowId: Long) = flowOf(emptyList<FailureAnalysisEntity>())
    override fun observeFailureAnalysesByBacktestRun(runRowId: Long) = flowOf(emptyList<FailureAnalysisEntity>())
    override fun observeFailureAnalysesByCategory(category: String) = flowOf(emptyList<FailureAnalysisEntity>())
    override suspend fun linkEvidence(linkedEntityType: LearningEntityType, linkedEntityRowId: Long, sourceType: EvidenceSourceType, sourceRowId: Long, note: String?) = 1L
    override fun observeEvidenceFor(linkedEntityType: LearningEntityType, linkedEntityRowId: Long) = flowOf(emptyList<LearningEvidenceLinkEntity>())
    override fun observeEntitiesSupportedBy(sourceType: EvidenceSourceType, sourceRowId: Long) = flowOf(emptyList<LearningEvidenceLinkEntity>())
}

internal class FakePortfolioRepository(
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

internal class FakeConnectionRepository : ConnectionRepository {
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

internal class FakeToolRepository : ToolRepository {
    override val tools: StateFlow<List<ToolDefinition>> = MutableStateFlow(emptyList())
    override val health: StateFlow<Map<String, ToolHealthStatus>> = MutableStateFlow(emptyMap())
    override val executionLog: StateFlow<List<ToolExecutionRecord>> = MutableStateFlow(emptyList())
    override fun checkHealth(toolId: String) = ToolHealthStatus.HEALTHY
    override suspend fun execute(toolId: String, input: String, approvalId: String?): ToolResult = throw NotImplementedError()
}

internal class FakeWorkflowEngine : WorkflowEngine {
    override val runs: StateFlow<List<WorkflowRunRecord>> = MutableStateFlow(emptyList())
    override suspend fun run(definition: WorkflowDefinition, execute: suspend (WorkflowStep) -> Boolean): WorkflowRunRecord = throw NotImplementedError()
}

/** [TrustScoreCalculator]'s three dependencies not already faked above -- all empty/default state, so [fakeTrustScoreCalculator] composes an honest "no evidence yet" assessment for any instrument, matching TrustScoreCalculator's own documented fail-closed default. */
internal class FakeQualityReportRepository : com.jarvis.tidb.historical.quality.repository.QualityReportRepository {
    override suspend fun publishReport(report: com.jarvis.tidb.historical.quality.entity.CandleQualityReportEntity, issues: List<com.jarvis.tidb.historical.quality.entity.QualityIssueEntity>) = 1L
    override suspend fun getLatest(instrumentId: Long, timeframe: String): com.jarvis.tidb.historical.quality.entity.CandleQualityReportEntity? = null
    override fun observeByInstrument(instrumentId: Long) = flowOf(emptyList<com.jarvis.tidb.historical.quality.entity.CandleQualityReportEntity>())
    override fun observeBelowThreshold(threshold: Double) = flowOf(emptyList<com.jarvis.tidb.historical.quality.entity.CandleQualityReportEntity>())
    override fun observeIssues(reportId: Long) = flowOf(emptyList<com.jarvis.tidb.historical.quality.entity.QualityIssueEntity>())
    override fun observeUnresolvedCritical() = flowOf(emptyList<com.jarvis.tidb.historical.quality.entity.QualityIssueEntity>())
    override suspend fun resolveIssue(issueId: Long) {}
}

internal class FakeIndicatorDefinitionRepository : com.jarvis.tidb.historical.indicator.repository.IndicatorDefinitionRepository {
    override suspend fun define(definition: com.jarvis.tidb.historical.indicator.entity.IndicatorDefinitionEntity) = 1L
    override suspend fun getById(id: Long): com.jarvis.tidb.historical.indicator.entity.IndicatorDefinitionEntity? = null
    override suspend fun getLatestByName(name: String): com.jarvis.tidb.historical.indicator.entity.IndicatorDefinitionEntity? = null
    override fun observeByType(type: String) = flowOf(emptyList<com.jarvis.tidb.historical.indicator.entity.IndicatorDefinitionEntity>())
    override fun observeActive() = flowOf(emptyList<com.jarvis.tidb.historical.indicator.entity.IndicatorDefinitionEntity>())
    override suspend fun createNewVersion(definition: com.jarvis.tidb.historical.indicator.entity.IndicatorDefinitionEntity, newParamsJson: String) = 1L
}

internal class FakeIndicatorValueRepository : com.jarvis.tidb.historical.indicator.repository.IndicatorValueRepository {
    override suspend fun storeValues(values: List<com.jarvis.tidb.historical.indicator.entity.IndicatorValueEntity>) = emptyList<Long>()
    override fun observeRange(indicatorDefId: Long, instrumentId: Long, timeframe: String, fromTs: Long, toTs: Long) = flowOf(emptyList<com.jarvis.tidb.historical.indicator.entity.IndicatorValueEntity>())
    override suspend fun getLatest(indicatorDefId: Long, instrumentId: Long, timeframe: String, limit: Int) = emptyList<com.jarvis.tidb.historical.indicator.entity.IndicatorValueEntity>()
    override suspend fun countInRange(indicatorDefId: Long, instrumentId: Long, timeframe: String, fromTs: Long, toTs: Long) = 0
    override suspend fun discardVersion(indicatorDefId: Long, instrumentId: Long, timeframe: String, version: Int) {}
}

/** A real [TrustScoreCalculator] wired to empty-state fakes -- every dimension honestly scores 0.0/"no evidence yet", the correct fail-closed default this class documents for a fresh repository. Reuses the SAME Optimization/Backtest/Learning/Portfolio fakes [capabilityInventory] uses, so a test asserting on both never sees two different "empty state" shapes. */
fun fakeTrustScoreCalculator(
    jobs: List<OptimizationJobEntity> = emptyList(),
    backtests: List<BacktestEntity> = emptyList(),
    observations: List<LearningObservationEntity> = emptyList(),
    portfolios: List<PortfolioEntity> = emptyList(),
    positions: Map<Long, List<PortfolioPositionEntity>> = emptyMap(),
): com.jarvis.os.app.core.trading.reasoning.TrustScoreCalculator = com.jarvis.os.app.core.trading.reasoning.TrustScoreCalculator(
    FakeOptimizationRepository(jobs),
    FakeBacktestRepository(backtests),
    FakeLearningRepository(observations),
    FakePortfolioRepository(portfolios, positions),
    FakeQualityReportRepository(),
    FakeIndicatorDefinitionRepository(),
    FakeIndicatorValueRepository(),
)

private fun healthMonitor() = SystemHealthMonitor(FakeConnectionRepository(), FakeToolRepository(), FakeWorkflowEngine())

/**
 * Fully-parameterized [CapabilityInventory] factory -- the ONE place every CapabilityInventory
 * test builds its dependency graph, so a future interface change (like the one this repair pass
 * just fixed) only ever needs updating here, not once per test file.
 */
fun capabilityInventory(
    instruments: List<InstrumentEntity> = emptyList(),
    jobs: List<OptimizationJobEntity> = emptyList(),
    backtests: List<BacktestEntity> = emptyList(),
    observations: List<LearningObservationEntity> = emptyList(),
    portfolios: List<PortfolioEntity> = emptyList(),
    positions: Map<Long, List<PortfolioPositionEntity>> = emptyMap(),
    gitHub: GitHubStatusProvider = FakeGitHubStatusProvider(),
): CapabilityInventory = CapabilityInventory(
    FakeInstrumentRepository(instruments),
    FakeOptimizationRepository(jobs),
    FakeBacktestRepository(backtests),
    FakeLearningRepository(observations),
    FakePortfolioRepository(portfolios, positions),
    healthMonitor(),
    gitHub,
)

/** An empty-state, all-real-signatures [CapabilityInventory] -- every capability reads as its honest "not yet built/no data" default. */
fun emptyCapabilityInventory(gitHub: GitHubStatusProvider = FakeGitHubStatusProvider()): CapabilityInventory = capabilityInventory(gitHub = gitHub)

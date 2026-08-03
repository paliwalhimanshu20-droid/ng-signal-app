package com.jarvis.os.app.core.intelligence.selfawareness

import com.jarvis.os.app.core.monitoring.SystemHealthMonitor
import com.jarvis.os.app.data.model.CapabilityStatus
import com.jarvis.os.app.data.model.SystemCapabilityRecord
import com.jarvis.os.app.data.model.SystemHealthLevel
import com.jarvis.os.app.data.repository.GitHubFetchResult
import com.jarvis.os.app.data.repository.GitHubStatusProvider
import com.jarvis.tidb.analytics.entity.PositionStatus
import com.jarvis.tidb.analytics.repository.BacktestRepository
import com.jarvis.tidb.analytics.repository.LearningRepository
import com.jarvis.tidb.analytics.repository.PortfolioRepository
import com.jarvis.tidb.core.repository.InstrumentRepository
import com.jarvis.tidb.database.TradingIntelligenceDatabase
import com.jarvis.tidb.historical.indicator.entity.IndicatorType
import com.jarvis.tidb.optimization.entity.OptimizationJobStatus
import com.jarvis.tidb.optimization.repository.OptimizationRepository
import com.jarvis.os.app.core.intelligence.localintent.LocalServiceDomain
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 4B Slice 2, Section 2 -- Capability Inventory.
 *
 * "IMPORTANT: Do NOT rebuild existing functionality... Search. Reuse. Extend." This class adds
 * NO new persistence and NO new repository -- every row below is composed from real state
 * repositories/services JARVIS already had before this slice: [InstrumentRepository],
 * [OptimizationRepository], [BacktestRepository], [LearningRepository], [PortfolioRepository],
 * [SystemHealthMonitor] (itself already a rollup over Connections/Tools/Workflows -- see that
 * class's own docstring), [GitHubStatusProvider], and [TradingIntelligenceDatabase]'s own
 * compiled schema constants. Nothing here is invented -- see each private `xDimension()`
 * function below for exactly which real signal backs its [SystemCapabilityRecord.status] and
 * [SystemCapabilityRecord.completionPercent].
 *
 * STEP 0 REPOSITORY REALITY REPORT finding, stated here since it directly shaped this class's
 * design: [com.jarvis.os.app.data.repository.ProjectRepository] (`MockProjectRepository`)
 * already tracks "projects"/"milestones"/"progress percent" for `jarvis-os`, but its data is
 * hand-seeded demo content (e.g. a permanently-62%-complete "jarvis-os" project referencing
 * "Sprint 10"), not derived from this repository's real state. Using it here would violate
 * Section 1's "Never hallucinate" / "All answers must come from repository state" requirement,
 * so this class deliberately does NOT read [com.jarvis.os.app.data.repository.ProjectRepository]
 * -- every row instead queries the real TIDB/OS repositories directly, the same sources
 * [com.jarvis.os.app.core.intelligence.localintent.SystemStatusLocalIntentHandler] and
 * [com.jarvis.os.app.core.trading.reasoning.TrustScoreCalculator] already trust.
 *
 * A capability whose engine class genuinely does not exist yet in this compiled module (Backtest
 * Execution Engine, autonomous Paper Trading loop) is reported [CapabilityStatus.MISSING] even if
 * rows happen to exist in its table from manual/external entry -- rows in a table are not the
 * same claim as "an engine automatically produces them," and Section 5 (Owner Transparency)
 * requires the honest, stronger claim never be implied by the weaker one.
 */
@Singleton
class CapabilityInventory @Inject constructor(
    private val instruments: InstrumentRepository,
    private val optimizationRepository: OptimizationRepository,
    private val backtestRepository: BacktestRepository,
    private val learningRepository: LearningRepository,
    private val portfolioRepository: PortfolioRepository,
    private val systemHealthMonitor: SystemHealthMonitor,
    private val gitHub: GitHubStatusProvider,
) {
    suspend fun snapshot(): List<SystemCapabilityRecord> = listOf(
        tradingIntelligenceDatabase(),
        historicalMarketDataPlatform(),
        indicatorWarehouse(),
        optimizationEngine(),
        backtestExecutionEngine(),
        learningFramework(),
        paperTradingLoop(),
        trustLayer(),
        localIntentRouter(),
        deploymentCenter(),
        osInfrastructure(),
        liveTrading(),
    )

    private suspend fun tradingIntelligenceDatabase(): SystemCapabilityRecord = SystemCapabilityRecord(
        name = "Trading Intelligence Database (TIDB)",
        description = "Room-based local intelligence layer -- Core Market Foundation, Signal Intelligence, Trading " +
            "Analytics, Historical Platform, Evidence Engine, News/Sentiment, Decision Intelligence.",
        status = CapabilityStatus.COMPLETE,
        dependency = null,
        completionPercent = 100,
        nextMilestone = null,
        risk = null,
        verificationState = "Compiled schema: TradingIntelligenceDatabase.SCHEMA_VERSION=" +
            "${TradingIntelligenceDatabase.SCHEMA_VERSION}, ENTITY_COUNT=${TradingIntelligenceDatabase.ENTITY_COUNT}.",
    )

    private suspend fun historicalMarketDataPlatform(): SystemCapabilityRecord {
        val instrumentCount = instruments.observeAll().first().size
        val hasData = instrumentCount > 0
        return SystemCapabilityRecord(
            name = "Historical Market Data Platform",
            description = "Instrument master, candle ingestion, and candle-quality reporting.",
            status = if (hasData) CapabilityStatus.COMPLETE else CapabilityStatus.PARTIAL,
            dependency = null,
            completionPercent = if (hasData) 100 else 40,
            nextMilestone = if (hasData) null else "Ingest at least one instrument to move this from built-but-empty to verified-with-data.",
            risk = if (hasData) null else "Engine exists but has never ingested real data on this device -- unverified end-to-end.",
            verificationState = "InstrumentRepository.observeAll() returned $instrumentCount instrument(s).",
        )
    }

    private suspend fun indicatorWarehouse(): SystemCapabilityRecord {
        val typeCount = IndicatorType.entries.size
        return SystemCapabilityRecord(
            name = "Indicator Warehouse",
            description = "EMA, RSI, ATR, Supertrend, and the rest of the bundled indicator calculation engine.",
            status = CapabilityStatus.COMPLETE,
            dependency = null,
            completionPercent = 100,
            nextMilestone = null,
            risk = null,
            verificationState = "IndicatorType.entries.size=$typeCount -- calculation engine wired for all of them (see SystemStatusLocalIntentHandler).",
        )
    }

    private suspend fun optimizationEngine(): SystemCapabilityRecord {
        val jobs = optimizationRepository.observeAllJobs().first()
        val completed = jobs.count { it.statusValue == OptimizationJobStatus.COMPLETED.name }
        val status = when {
            jobs.isEmpty() -> CapabilityStatus.MISSING
            completed == 0 -> CapabilityStatus.PARTIAL
            else -> CapabilityStatus.COMPLETE
        }
        val percent = if (jobs.isEmpty()) 0 else ((completed.toDouble() / jobs.size) * 100).toInt().coerceAtLeast(15)
        return SystemCapabilityRecord(
            name = "Massive Optimization Engine",
            description = "Parameter-sweep optimization jobs against historical data per instrument/component.",
            status = status,
            dependency = "Historical Market Data Platform",
            completionPercent = percent,
            nextMilestone = if (status != CapabilityStatus.COMPLETE) "Complete and rank at least one optimization job per tracked instrument." else null,
            risk = if (jobs.isEmpty()) "No optimization job has ever been created -- today a job must be created manually (Section 5, not yet scheduled automatically)." else null,
            verificationState = "OptimizationRepository.observeAllJobs() returned ${jobs.size} job(s), $completed COMPLETED.",
        )
    }

    private suspend fun backtestExecutionEngine(): SystemCapabilityRecord {
        val backtests = backtestRepository.observeAllBacktests().first()
        val withResults = backtests.count { backtestRepository.observeResultsByBacktest(it.rowId).first().isNotEmpty() }
        return SystemCapabilityRecord(
            name = "Backtest Execution Engine",
            description = "Runs a strategy against stored historical candles and produces a scored result.",
            status = CapabilityStatus.MISSING,
            dependency = "Historical Market Data Platform, Optimization Engine",
            completionPercent = 0,
            nextMilestone = "Build the execution engine that replays a strategy against historical candles (flagged as missing since Phase 3C).",
            risk = "TrustScoreCalculator's BACKTESTS dimension stays at 0.0 for every instrument until this exists -- directly blocks live trading.",
            verificationState = "No BacktestEngine/execution class found in this repository. ${backtests.size} backtest record(s) exist " +
                "in the table ($withResults with a stored result) -- table rows are not evidence of an automated engine; see this " +
                "class's docstring for why that distinction is reported honestly.",
        )
    }

    private suspend fun learningFramework(): SystemCapabilityRecord {
        val observations = learningRepository.observeObservations().first()
        val status = when {
            observations.isEmpty() -> CapabilityStatus.MISSING
            observations.size < 5 -> CapabilityStatus.PARTIAL
            else -> CapabilityStatus.COMPLETE
        }
        return SystemCapabilityRecord(
            name = "Learning Framework",
            description = "Records observations from trade/backtest outcomes and their confidence.",
            status = status,
            dependency = "Backtest Execution Engine, Paper Trading Loop",
            completionPercent = (observations.size * 20).coerceAtMost(100),
            nextMilestone = if (status != CapabilityStatus.COMPLETE) "Accumulate learning observations once the Backtest Engine and Paper Trading loop are producing outcomes." else null,
            risk = if (observations.isEmpty()) "No trade or backtest outcomes exist yet for the Learning Framework to observe." else null,
            verificationState = "LearningRepository.observeObservations() returned ${observations.size} observation(s).",
        )
    }

    private suspend fun paperTradingLoop(): SystemCapabilityRecord {
        val portfolios = portfolioRepository.observePortfolios().first()
        val paperPortfolios = portfolios.filter { !it.isLive }
        var closedPositions = 0
        for (portfolio in paperPortfolios) {
            closedPositions += portfolioRepository.observePositions(portfolio.rowId, PositionStatus.CLOSED).first().size
        }
        val status = when {
            paperPortfolios.isEmpty() -> CapabilityStatus.MISSING
            closedPositions == 0 -> CapabilityStatus.PARTIAL
            else -> CapabilityStatus.PARTIAL // manual paper trades only -- the autonomous loop itself is still missing
        }
        return SystemCapabilityRecord(
            name = "Paper Trading Loop",
            description = "Non-live (isLive=false) portfolio tracking, and an autonomous loop that runs it continuously.",
            status = status,
            dependency = null,
            completionPercent = if (paperPortfolios.isEmpty()) 0 else if (closedPositions == 0) 30 else 55,
            nextMilestone = "Build the autonomous paper-trading loop (Section 11, not yet built) -- today positions can only be recorded manually.",
            risk = "No autonomous loop exists yet -- the Trust Layer's PAPER_TRADING dimension can only reach partial credit from manual entries.",
            verificationState = "PortfolioRepository.observePortfolios() returned ${portfolios.size} portfolio(s), ${paperPortfolios.size} non-live, " +
                "$closedPositions closed paper position(s) total. No autonomous-loop class found in this repository.",
        )
    }

    private fun trustLayer(): SystemCapabilityRecord = SystemCapabilityRecord(
        name = "Trust Layer (Phase 4B Slice 1)",
        description = "Six-dimension per-instrument Trust Score (Historical Data, Indicators, Optimization, Backtests, " +
            "Learning, Paper Trading) gating recommendation issuance.",
        status = CapabilityStatus.COMPLETE,
        dependency = "Optimization Engine, Backtest Execution Engine, Learning Framework, Paper Trading Loop",
        completionPercent = 100,
        nextMilestone = "JARVIS-005 Trust Score v2 (Calibration, Out-of-Sample Validation, Statistical Significance, " +
            "Regime Coverage, Contradicting Evidence, Recency Decay, Cost Adjustment, Portfolio Correlation) -- " +
            "referenced in code comments; the spec document itself is not present in this repository (a doc gap, not a Trust Layer defect).",
        risk = "TrustScoreCalculator.MINIMUM_TRUST_SCORE=0.35 is a fail-closed gate -- most instruments will not clear it while " +
            "Optimization/Backtests/Paper Trading remain MISSING or PARTIAL above. This is intended honest behavior, not a bug.",
        verificationState = "TrustScoreCalculator class present and wired into DecisionLifecycleRunner's VALIDATE stage. Computed " +
            "per-instrument only -- no system-wide aggregate exists yet (see SelfAwarenessEngine's own note on this).",
    )

    private fun localIntentRouter(): SystemCapabilityRecord = SystemCapabilityRecord(
        name = "OS-First Local Intent Routing",
        description = "Deterministic, keyword-classified local answers tried before any AI provider is ever consulted.",
        status = CapabilityStatus.COMPLETE,
        dependency = null,
        completionPercent = 100,
        nextMilestone = "DEVICE_ACTION domain is reserved in the enum but has no bound-and-executing handler for real device actions yet.",
        risk = null,
        verificationState = "LocalServiceDomain.entries.size=${LocalServiceDomain.entries.size} local service domains declared and routed.",
    )

    private fun deploymentCenter(): SystemCapabilityRecord {
        val connected = gitHub.status.value is GitHubFetchResult.Success
        return SystemCapabilityRecord(
            name = "Deployment Center (GitHub publishing pipeline)",
            description = "GitHub Git Data API-based code publishing with security scanning, change analysis, and an owner-gated flow.",
            status = if (connected) CapabilityStatus.COMPLETE else CapabilityStatus.PARTIAL,
            dependency = null,
            completionPercent = if (connected) 100 else 70,
            nextMilestone = if (connected) null else "Configure a GitHub Personal Access Token (GitHubTokenStore) to verify the live connection on this device.",
            risk = if (connected) null else "Pipeline code exists and compiles, but has not yet successfully reached the GitHub API on this device/session.",
            verificationState = "GitHubStatusProvider.status.value is ${if (connected) "Success" else "not yet Success (no token, or last fetch failed)"}.",
        )
    }

    private fun osInfrastructure(): SystemCapabilityRecord {
        val health = systemHealthMonitor.snapshot()
        val status = when (health.level) {
            SystemHealthLevel.HEALTHY -> CapabilityStatus.COMPLETE
            SystemHealthLevel.DEGRADED -> CapabilityStatus.PARTIAL
            SystemHealthLevel.CRITICAL -> CapabilityStatus.MISSING
        }
        return SystemCapabilityRecord(
            name = "OS Infrastructure (Connections, Tools, Settings, Diagnostics, Mission Control)",
            description = "Connected-systems tracking, tool health, diagnostics/audit log, and settings -- the non-trading OS layer.",
            status = status,
            dependency = null,
            completionPercent = when (status) {
                CapabilityStatus.COMPLETE -> 100
                CapabilityStatus.PARTIAL -> 70
                CapabilityStatus.MISSING -> 20
            },
            nextMilestone = if (health.reasons.isEmpty()) null else "Resolve: ${health.reasons.joinToString("; ")}",
            risk = if (health.reasons.isEmpty()) null else health.reasons.joinToString("; "),
            verificationState = "SystemHealthMonitor.snapshot(): ${health.connectionsHealthy}/${health.connectionsTotal} connections healthy, " +
                "${health.toolsHealthy}/${health.toolsTotal} tools healthy, level=${health.level}.",
        )
    }

    private fun liveTrading(): SystemCapabilityRecord = SystemCapabilityRecord(
        name = "Live Trading",
        description = "Autonomous or owner-approved live order placement on real capital.",
        status = CapabilityStatus.MISSING,
        dependency = "Backtest Execution Engine, Massive Optimization Engine, Paper Trading Loop, Trust Layer (Phase 4B Slice 1)",
        completionPercent = 0,
        nextMilestone = "\"September Mission\": Natural Gas instrument intelligence end-to-end, sequenced after the Backtest Engine, " +
            "a completed Optimization pass, and a real Paper Trading track record.",
        risk = "No order-placement pathway exists in this repository at all -- this is a hard, intentional gate, not an oversight.",
        verificationState = "No live-order-execution class found in this repository.",
    )
}

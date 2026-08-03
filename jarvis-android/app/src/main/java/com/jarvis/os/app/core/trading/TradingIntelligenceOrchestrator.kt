package com.jarvis.os.app.core.trading

import com.jarvis.os.app.core.trading.reasoning.DecisionLifecycleRequest
import com.jarvis.os.app.core.trading.reasoning.DecisionLifecycleResult
import com.jarvis.os.app.core.trading.reasoning.DecisionLifecycleRunner
import com.jarvis.tidb.core.repository.InstrumentRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JARVIS-002 Layer 3 (Conversation-facing wrapper) -- single entry point for trading-shaped
 * questions, matching the "one entry point per subsystem, never bypassed" rule this app already
 * follows for [com.jarvis.os.app.core.JarvisCore] itself. Only [DecisionLifecycleRunner] is
 * fanned out to in this first implementation; `InsightEngine`/`TradingBriefingEngine` remain
 * Section 6 (September Mission) items not yet built -- see the JARVIS-002 plan's Section 5
 * integration sequence, which deliberately orders this class after the pipeline is proven
 * standalone.
 *
 * SCOPE NOTE: full `TradingIntentRouter`/`TradingQuestionParser` (turning arbitrary natural
 * language into a [DecisionLifecycleRequest]) is deliberately NOT built in this first pass --
 * that is real, separate scope (keyword/pattern classification, matching this codebase's
 * existing `IntentRouter`/`JarvisDecisionEngine` convention) that deserves its own implementation
 * pass rather than being rushed alongside the runtime bridge. [askAbout] takes an instrument
 * symbol directly; wiring a real parser in front of it is additive and does not change this
 * class's contract.
 *
 * Split into an interface + [DefaultTradingIntelligenceOrchestrator], matching this codebase's
 * existing `WorkflowEngine`/`DefaultWorkflowEngine` convention (`core.workflow.WorkflowEngine`),
 * so JarvisCore's own unit tests can supply a trivial fake instead of needing a real
 * DecisionLifecycleRunner + full TIDB repository graph in a plain JVM test.
 */
interface TradingIntelligenceOrchestrator {
    /**
     * Resolves [symbol] to a real instrument, runs the Decision Lifecycle against it, and
     * renders a plain-text response suitable for [com.jarvis.os.app.core.JarvisCore]'s existing
     * chat response path. Returns null if [symbol] does not resolve to a known instrument --
     * the caller (JarvisCore) falls back to its existing conversational handling in that case,
     * matching the "not every message is a trading question" boundary any future
     * TradingIntentRouter will also need to respect.
     */
    suspend fun askAbout(symbol: String): String?
}

@Singleton
class DefaultTradingIntelligenceOrchestrator @Inject constructor(
    private val decisionLifecycleRunner: DecisionLifecycleRunner,
    private val instrumentRepository: InstrumentRepository,
) : TradingIntelligenceOrchestrator {

    override suspend fun askAbout(symbol: String): String? {
        val instrument = instrumentRepository.getBySymbol(symbol) ?: return null
        val result = decisionLifecycleRunner.run(DecisionLifecycleRequest(instrumentId = instrument.instrumentId))
        return render(result)
    }

    private fun render(result: DecisionLifecycleResult): String = when (result) {
        is DecisionLifecycleResult.Recommended ->
            "${result.explanation} (Recommendation #${result.recommendationId}, " +
                "confidence ${result.confidenceScore?.let { "%.0f%%".format(it * 100) } ?: "unknown"}, " +
                "trust ${"%.0f%%".format(result.trustAssessment.overallScore * 100)}.)"
        is DecisionLifecycleResult.InsufficientEvidence ->
            "I don't have enough evidence yet to give you a real recommendation on this instrument. ${result.reason} " +
                "I'd rather tell you that honestly than guess."
        is DecisionLifecycleResult.TrustScoreBelowThreshold ->
            "I have some evidence on this instrument, but not enough spread across historical data, indicators, " +
                "optimization, backtests, learning, and paper trading to trust a recommendation yet. " +
                "${result.trustAssessment.explanation} I'd rather tell you that honestly than guess."
        is DecisionLifecycleResult.PipelineFailed ->
            "Something went wrong while working through this — the ${result.failedStage} stage didn't complete" +
                (result.detail?.let { ": $it" } ?: ".") + " Nothing incomplete was saved."
    }
}

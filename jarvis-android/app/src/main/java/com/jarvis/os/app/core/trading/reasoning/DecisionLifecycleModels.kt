package com.jarvis.os.app.core.trading.reasoning

import com.jarvis.tidb.decision.entity.RecommendationEntity
import com.jarvis.tidb.decision.entity.RecommendationRiskAssessmentEntity

/**
 * JARVIS-002 Layer 2. One request into [DecisionLifecycleRunner] -- deliberately just an
 * instrument + timeframe, matching TRADING-007B's own framing of a recommendation as something
 * produced for an instrument, not something parsed out of a natural-language question. Turning a
 * chat message into this shape is `TradingIntentRouter`/`TradingQuestionParser`'s job (Section 3
 * of `TradingIntelligenceOrchestrator`), not this runner's.
 */
data class DecisionLifecycleRequest(
    val instrumentId: Long,
    /** Regime/timeframe scope for stage 4 (CONTEXTUALIZE). Defaults to daily; caller may narrow for an intraday/scalping-style question -- see `RecommendationType`/`DecisionTimeHorizon` for how the eventual recommendation itself expresses horizon, which is a separate concept from this read-side timeframe scope. */
    val timeframe: String = "1D",
    val requestedBy: String = "owner",
)

/**
 * Outcome of one synchronous pipeline run (stages 1-11). Stages 12-13 (RESOLVE/LEARN) are
 * explicitly out of this shape -- they are asynchronous, triggered later by a real market
 * outcome, not part of one request/response cycle. See [DecisionLifecycleRunner.recordOutcome]
 * for the write path stage 12 needs, kept separate on purpose.
 */
sealed interface DecisionLifecycleResult {

    /** Stages 1-11 completed and a recommendation was persisted (stage 9) and monitored (stage 11). */
    data class Recommended(
        val recommendationId: Long,
        val recommendation: RecommendationEntity,
        val riskAssessments: List<RecommendationRiskAssessmentEntity>,
        /** The stage-6 composed confidence score, in [0.0, 1.0] -- null only if stage 6 itself could not compose one, in which case [Recommended] should not normally have been reached (see stage 2's VALIDATE gate). */
        val confidenceScore: Double?,
        /** Stage 10's rendering of the evidence graph -- structural explanation first (the graph itself, resolvable independently via `IntelligenceEvidenceRepository`), this string second, per TRADING-007B Principle 2. Deterministic in this first implementation -- see [DecisionLifecycleRunner] class doc. */
        val explanation: String,
    ) : DecisionLifecycleResult

    /** Stage 2 (VALIDATE) halted the pipeline before a recommendation was drafted -- fail-closed per the JARVIS-INTEL-001 architecture's Section 13 rule: no recommendation is better than a low-quality one. Nothing is persisted for this outcome. */
    data class InsufficientEvidence(
        val instrumentId: Long,
        val reason: String,
    ) : DecisionLifecycleResult

    /** A stage other than VALIDATE failed after exhausting WorkflowEngine's retries -- surfaced honestly rather than silently swallowed. Nothing past the failed stage was persisted; whatever stage(s) before it already wrote (e.g. stage 6's confidence score) remain, since those are individually valid, standalone records per this schema's own insert-only conventions -- there is no partial-pipeline rollback, matching stage 6/9's persistence-point analysis in the JARVIS-001A blueprint. */
    data class PipelineFailed(
        val instrumentId: Long,
        val failedStage: String,
        val detail: String?,
    ) : DecisionLifecycleResult
}

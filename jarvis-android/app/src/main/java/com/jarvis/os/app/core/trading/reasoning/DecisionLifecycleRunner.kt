package com.jarvis.os.app.core.trading.reasoning

import com.jarvis.os.app.core.workflow.WorkflowEngine
import com.jarvis.os.app.data.model.WorkflowDefinition
import com.jarvis.os.app.data.model.WorkflowStep
import com.jarvis.os.app.data.model.WorkflowStepStatus
import com.jarvis.tidb.decision.entity.DecisionReviewEntity
import com.jarvis.tidb.decision.entity.DecisionTimeHorizon
import com.jarvis.tidb.decision.entity.RecommendationEntity
import com.jarvis.tidb.decision.entity.RecommendationOutcomeEntity
import com.jarvis.tidb.decision.entity.RecommendationRiskAssessmentEntity
import com.jarvis.tidb.decision.entity.RecommendationRiskCategory
import com.jarvis.tidb.decision.entity.RecommendationStatus
import com.jarvis.tidb.decision.entity.RecommendationType
import com.jarvis.tidb.decision.entity.RiskLevel
import com.jarvis.tidb.decision.repository.DecisionIntelligenceRepository
import com.jarvis.tidb.historical.evidence.entity.EvidenceRecordEntity
import com.jarvis.tidb.historical.evidence.repository.EvidenceRepository
import com.jarvis.tidb.intelligence.confidence.entity.ConfidenceModelEntity
import com.jarvis.tidb.intelligence.confidence.entity.ConfidenceModelType
import com.jarvis.tidb.intelligence.confidence.entity.ConfidenceScoreEntity
import com.jarvis.tidb.intelligence.confidence.entity.ScoredEntityType
import com.jarvis.tidb.intelligence.confidence.repository.ConfidenceRepository
import com.jarvis.tidb.intelligence.evidence.entity.EvidenceLinkEntity
import com.jarvis.tidb.intelligence.evidence.entity.EvidenceLinkRole
import com.jarvis.tidb.intelligence.evidence.entity.LinkedEntityType
import com.jarvis.tidb.intelligence.evidence.repository.IntelligenceEvidenceRepository
import com.jarvis.tidb.intelligence.regime.entity.MarketRegimeEntity
import com.jarvis.tidb.intelligence.regime.repository.RegimeRepository
import com.jarvis.tidb.analytics.entity.TimelineEventType
import com.jarvis.tidb.analytics.entity.TimelineSeverity
import com.jarvis.tidb.analytics.entity.TradingTimelineEventEntity
import com.jarvis.tidb.analytics.repository.TimelineRepository
import com.jarvis.tidb.signals.entity.SignalEntity
import com.jarvis.tidb.signals.repository.SignalRepository
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JARVIS-002 Layer 2: the runtime executor of TRADING-007B's already-designed 13-stage Decision
 * Lifecycle (COLLECT -> VALIDATE -> SCORE -> CONTEXTUALIZE -> HISTORICAL -> WEIGH -> RISK ->
 * ALTERNATIVES -> RECOMMEND -> EXPLAIN -> MONITOR -> RESOLVE -> LEARN). This is deliberately NOT
 * a second reasoning design -- every stage below implements exactly the responsibility named in
 * that document and the JARVIS-001A blueprint's Part 3 stage table; nothing here invents new
 * decomposition.
 *
 * SCOPE OF THIS FIRST IMPLEMENTATION, stated plainly rather than left implicit:
 * - Stages 1-11 run synchronously as one [WorkflowEngine] run per [run] call. Stages 12
 *   (RESOLVE) and 13 (LEARN) are intentionally NOT invoked automatically here -- there is no
 *   scheduler anywhere in this app (confirmed absent during the JARVIS-002 planning review) and
 *   no real market outcome exists to resolve against immediately after a recommendation is
 *   issued. [recordOutcome] exposes the write path stage 12 needs; a future scheduled/triggered
 *   caller invokes it, this class does not invent that trigger.
 * - Stage 3 (SCORE) and stage 6 (WEIGH) compose from [EvidenceRecordEntity.strength] --
 *   already-scored, per-item data this class reads, never recomputes, per TRADING-007B's own
 *   Principle 1 ("store, don't decide twice"). The composition itself (a plain average) is a
 *   deliberately simple first pass, not a claimed methodology -- see [composeConfidence]'s doc.
 * - Stage 7 (RISK) persists one [RecommendationRiskAssessmentEntity] per risk category at
 *   [RiskLevel.MODERATE] with no `probability`/`severity` populated (both fields are nullable
 *   for exactly this reason) and a `riskFactors` note stating the category is an unassessed
 *   baseline -- honest about what hasn't been computed rather than defaulting to a falsely
 *   reassuring LOW, per this pipeline's own fail-closed/never-fail-silent principle. Real
 *   per-category scoring (reading `PortfolioRiskEntity`/`VolatilityProfileEntity`/
 *   `LiquidityProfileEntity`/`DriftMetricEntity`/`CorrelationEntity`, per TRADING-007B's own
 *   §9 architecture note) is unimplemented here and flagged as the next milestone's highest-value
 *   follow-up.
 * - Stage 8 (ALTERNATIVES) persists an empty list -- no alternative-generation logic exists yet;
 *   an empty, explicit list is itself the honest fact to record, per TRADING-007B's own framing.
 * - Stage 9 (RECOMMEND) always issues [RecommendationType.WATCH] -- the enum's own designated
 *   fallback. No directional scoring methodology (what would justify ENTRY_LONG vs ENTRY_SHORT)
 *   exists anywhere in this codebase yet; issuing a directional call with no real basis would be
 *   fabrication, which this project's own Constitution (Article III, Non-Fabrication) forbids.
 *   This first implementation proves the pipeline persists and round-trips correctly end to end;
 *   real directional judgment is out of scope pending a real scoring methodology.
 * - Stage 10 (EXPLAIN) renders the evidence graph deterministically (template text over the real
 *   linked evidence, matching this codebase's established "deterministic decomposition, not an
 *   opaque call" convention elsewhere -- `PlanningEngine`, `JarvisDecisionEngine`). No AI
 *   provider call is made here. TRADING-007B's own Principle 2 already treats free text as
 *   optional, secondary rendering of the evidence graph, not the explanation itself, so this
 *   is a conforming, honest first pass -- a live `AiRouter`/`ChatProvider` call to produce more
 *   natural prose is a self-contained future enhancement to this one method, not a redesign.
 *
 * IMPLEMENTATION NOTE ON STAGE 6/9 ORDERING, discovered while building this against the real
 * repository interface rather than assumed in advance: [RecommendationEntity.confidenceScoreId]
 * must be known at the time a [ConfidenceScoreEntity] is composed (stage 6), but
 * [ConfidenceScoreEntity.scoredEntityRowId] needs a real `recommendationId` to score against,
 * which does not exist until stage 9 inserts it. `DecisionIntelligenceRepository` exposes no
 * "attach a confidence score to an already-inserted recommendation" update method. This runner
 * resolves the ordering using a mechanism the schema already provides for exactly this kind of
 * change -- "supersede, don't mutate": a DRAFT recommendation is inserted first (with
 * `confidenceScoreId = null`), scored against its own real id, then immediately superseded via
 * [DecisionIntelligenceRepository.reviseRecommendation] with the composed `confidenceScoreId`
 * attached and status promoted to ACTIVE. This is two real, valid, audit-trail rows (a DRAFT and
 * its ACTIVE revision) rather than one -- an accepted, schema-consistent trade-off, not a bug.
 */
@Singleton
class DecisionLifecycleRunner @Inject constructor(
    private val workflowEngine: WorkflowEngine,
    private val decisionRepository: DecisionIntelligenceRepository,
    private val confidenceRepository: ConfidenceRepository,
    private val intelligenceEvidenceRepository: IntelligenceEvidenceRepository,
    private val evidenceRepository: EvidenceRepository,
    private val signalRepository: SignalRepository,
    private val regimeRepository: RegimeRepository,
    private val timelineRepository: TimelineRepository,
) {
    /** Runs stages 1-11 for one instrument. See class doc for exactly what each stage does and does not do in this first implementation. */
    suspend fun run(request: DecisionLifecycleRequest): DecisionLifecycleResult {
        var collectedEvidence: List<EvidenceRecordEntity> = emptyList()
        var collectedSignals: List<SignalEntity> = emptyList()
        var activeRegime: MarketRegimeEntity? = null
        var composedScore: Double? = null
        var draftRecommendationId: Long? = null
        var draftRecommendation: RecommendationEntity? = null
        var riskAssessments: List<RecommendationRiskAssessmentEntity> = emptyList()
        var finalRecommendationId: Long? = null
        var finalRecommendation: RecommendationEntity? = null
        var explanation: String? = null
        var insufficientReason: String? = null

        val definition = WorkflowDefinition(
            workflowId = "decision-lifecycle-${request.instrumentId}-${UUID.randomUUID()}",
            name = "Decision Lifecycle (TRADING-007B) — instrument ${request.instrumentId}",
            steps = listOf(
                WorkflowStep(stepId = "collect", name = "COLLECT", maxRetries = 1),
                WorkflowStep(stepId = "validate", name = "VALIDATE", dependsOn = setOf("collect")),
                WorkflowStep(stepId = "score", name = "SCORE", dependsOn = setOf("validate")),
                WorkflowStep(stepId = "contextualize", name = "CONTEXTUALIZE", dependsOn = setOf("score"), maxRetries = 1),
                WorkflowStep(stepId = "historical", name = "HISTORICAL", dependsOn = setOf("contextualize"), maxRetries = 1),
                WorkflowStep(stepId = "weigh_and_recommend_draft", name = "WEIGH + RECOMMEND(draft)", dependsOn = setOf("historical")),
                WorkflowStep(stepId = "risk", name = "RISK", dependsOn = setOf("weigh_and_recommend_draft")),
                WorkflowStep(stepId = "alternatives", name = "ALTERNATIVES", dependsOn = setOf("risk")),
                WorkflowStep(stepId = "recommend_finalize", name = "RECOMMEND(finalize)", dependsOn = setOf("alternatives")),
                WorkflowStep(stepId = "explain", name = "EXPLAIN", dependsOn = setOf("recommend_finalize")),
                WorkflowStep(stepId = "monitor", name = "MONITOR", dependsOn = setOf("explain")),
            ),
        )

        val runRecord = workflowEngine.run(definition) { step ->
            try {
                when (step.stepId) {
                    "collect" -> {
                        collectedSignals = signalRepository.observeActiveByInstrument(request.instrumentId).first()
                        collectedEvidence = evidenceRepository.observeRecentEvidence(request.instrumentId, limit = 50).first()
                        true
                    }
                    "validate" -> {
                        if (collectedEvidence.isEmpty() && collectedSignals.isEmpty()) {
                            insufficientReason = "No evidence and no active signals found for instrument ${request.instrumentId}."
                            false
                        } else {
                            true
                        }
                    }
                    "score" -> {
                        // Reads EvidenceRecordEntity.strength -- already-scored, per-item data. No recomputation here; see composeConfidence's doc.
                        true
                    }
                    "contextualize" -> {
                        activeRegime = regimeRepository.getActiveRegime(request.instrumentId, request.timeframe)
                        true
                    }
                    "historical" -> {
                        // First-pass HISTORICAL: comparable prior occurrences for this instrument/timeframe.
                        // observePatternsByInstrumentTimeframe returns PatternOccurrenceEntity rows -- read
                        // only to confirm the surface is reachable in this first pass; not yet folded into
                        // composeConfidence or the explanation. Full HISTORICAL comparison (PatternRepository
                        // catalog cross-reference, ResearchRepository, GraphRepository correlation) is a
                        // follow-up, not part of this milestone's proof that the wire works end to end.
                        evidenceRepository.observePatternsByInstrumentTimeframe(request.instrumentId, request.timeframe).first()
                        true
                    }
                    "weigh_and_recommend_draft" -> {
                        composedScore = composeConfidence(collectedEvidence)
                        val draft = RecommendationEntity(
                            instrumentId = request.instrumentId,
                            recommendationType = RecommendationType.WATCH,
                            status = RecommendationStatus.DRAFT,
                            confidenceScoreId = null,
                            timeHorizon = DecisionTimeHorizon.SWING,
                            reasoningSummary = null,
                            generatedBy = request.requestedBy,
                        )
                        val id = decisionRepository.recordRecommendation(draft)
                        draftRecommendationId = id
                        draftRecommendation = draft.copy(recommendationId = id)
                        true
                    }
                    "risk" -> {
                        val id = draftRecommendationId ?: return@run false
                        riskAssessments = RecommendationRiskCategory.entries.map { category ->
                            RecommendationRiskAssessmentEntity(
                                recommendationId = id,
                                riskCategory = category,
                                riskLevel = RiskLevel.MODERATE,
                                probability = null,
                                severity = null,
                                riskFactors = "Unassessed baseline -- category-specific scoring not yet implemented (JARVIS-002 first pass).",
                            )
                        }
                        decisionRepository.recordRiskAssessments(riskAssessments)
                        true
                    }
                    "alternatives" -> {
                        // Deliberately empty -- see class doc.
                        true
                    }
                    "recommend_finalize" -> {
                        val id = draftRecommendationId
                        val draft = draftRecommendation
                        if (id == null || draft == null) return@run false

                        val model = ensureBaselineConfidenceModel()
                        val score = composedScore ?: return@run false
                        val scoreId = confidenceRepository.recordScore(
                            ConfidenceScoreEntity(
                                modelId = model.modelId,
                                scoredEntityType = ScoredEntityType.DECISION,
                                scoredEntityRowId = id,
                                score = score,
                                notes = "Composed from ${collectedEvidence.size} evidence record(s) — plain average of EvidenceRecordEntity.strength.",
                            ),
                        )

                        collectedEvidence.forEach { evidence ->
                            intelligenceEvidenceRepository.linkEvidence(
                                EvidenceLinkEntity(
                                    evidenceId = evidence.evidenceId,
                                    linkedEntityType = LinkedEntityType.DECISION,
                                    linkedEntityRowId = id,
                                    role = EvidenceLinkRole.SUPPORTS,
                                    weight = evidence.strength,
                                ),
                            )
                        }

                        val revision = draft.copy(
                            recommendationId = 0L,
                            confidenceScoreId = scoreId,
                            status = RecommendationStatus.ACTIVE,
                            revisesRecommendationId = id,
                        )
                        val activeId = decisionRepository.reviseRecommendation(id, revision)
                        finalRecommendationId = activeId
                        finalRecommendation = revision.copy(recommendationId = activeId)
                        true
                    }
                    "explain" -> {
                        val rec = finalRecommendation ?: return@run false
                        explanation = renderExplanation(rec, collectedEvidence, collectedSignals, activeRegime, composedScore)
                        true
                    }
                    "monitor" -> {
                        val id = finalRecommendationId ?: return@run false
                        timelineRepository.recordEvent(
                            TradingTimelineEventEntity(
                                eventType = TimelineEventType.RECOMMENDATION_ISSUED,
                                severity = TimelineSeverity.NOTABLE,
                                title = "Recommendation issued for instrument ${request.instrumentId}",
                                details = explanation,
                                relatedInstrumentId = request.instrumentId,
                            ),
                        )
                        true
                    }
                    else -> false
                }
            } catch (e: Exception) {
                insufficientReason = insufficientReason ?: e.message
                false
            }
        }

        return when {
            insufficientReason != null && finalRecommendationId == null && runRecord.stepStatuses["validate"] != WorkflowStepStatus.SUCCEEDED ->
                DecisionLifecycleResult.InsufficientEvidence(request.instrumentId, insufficientReason!!)
            finalRecommendationId != null && finalRecommendation != null ->
                DecisionLifecycleResult.Recommended(
                    recommendationId = finalRecommendationId!!,
                    recommendation = finalRecommendation!!,
                    riskAssessments = riskAssessments,
                    confidenceScore = composedScore,
                    explanation = explanation.orEmpty(),
                )
            else -> {
                val failedStep = runRecord.stepStatuses.entries.firstOrNull { it.value == WorkflowStepStatus.FAILED }
                DecisionLifecycleResult.PipelineFailed(
                    instrumentId = request.instrumentId,
                    failedStage = failedStep?.key ?: "unknown",
                    detail = insufficientReason,
                )
            }
        }
    }

    /**
     * Stage 12 (RESOLVE) write path only -- not invoked automatically by [run]. A future
     * scheduled/triggered caller (no such trigger exists anywhere in this app yet, per the
     * JARVIS-002 planning review) calls this once a real market outcome is known.
     */
    suspend fun recordOutcome(outcome: RecommendationOutcomeEntity): Long =
        decisionRepository.recordOutcome(outcome)

    /** Stage 11's triggered-review counterpart -- see class doc: DecisionReviewEntity rows are created by a scheduled/triggered check, not at issuance time. Exposed for that future caller. */
    suspend fun recordReview(review: DecisionReviewEntity): Long =
        decisionRepository.recordDecisionReview(review)

    /**
     * Plain average of [EvidenceRecordEntity.strength] across everything COLLECT gathered,
     * clamped to [0.0, 1.0] (already guaranteed by the field's own contract, clamped again here
     * defensively). This is intentionally the simplest possible honest composition -- no
     * confidence-model-specific weighting, no recency decay, no per-source reliability
     * adjustment (`EvidenceSourceEntity.reliabilityWeight` exists and is not read here). Falls
     * back to a neutral 0.5 only when there is evidence to score but the average cannot be
     * computed for some reason; VALIDATE already guarantees this function is never called with
     * zero evidence AND zero signals, but zero evidence with only signals present is possible,
     * in which case 0.5 (maximally uncertain, not a claim of confidence) is the honest default.
     */
    private fun composeConfidence(evidence: List<EvidenceRecordEntity>): Double =
        if (evidence.isEmpty()) 0.5 else (evidence.sumOf { it.strength } / evidence.size).coerceIn(0.0, 1.0)

    private suspend fun ensureBaselineConfidenceModel(): ConfidenceModelEntity {
        val key = "jarvis_decision_lifecycle_v1"
        confidenceRepository.getModelByKey(key)?.let { return it }
        val modelId = confidenceRepository.defineModel(
            ConfidenceModelEntity(
                modelKey = key,
                displayName = "JARVIS Decision Lifecycle — v1 (plain average)",
                modelType = ConfidenceModelType.WEIGHTED_SUM,
                description = "First-pass composition: plain average of collected EvidenceRecordEntity.strength. " +
                    "No per-source reliability weighting, no recency decay yet — see DecisionLifecycleRunner.composeConfidence.",
            ),
        )
        return confidenceRepository.getModel(modelId)
            ?: error("ConfidenceModelEntity was just inserted (id=$modelId) but could not be re-read.")
    }

    private fun renderExplanation(
        recommendation: RecommendationEntity,
        evidence: List<EvidenceRecordEntity>,
        signals: List<SignalEntity>,
        regime: MarketRegimeEntity?,
        confidenceScore: Double?,
    ): String {
        val evidenceLine = if (evidence.isEmpty()) {
            "No stored evidence records were found; this recommendation rests on ${signals.size} active signal(s) only."
        } else {
            "Composed from ${evidence.size} evidence record(s), averaging to a confidence of ${"%.2f".format(confidenceScore ?: 0.0)}."
        }
        val regimeLine = regime?.let { "Current regime: ${it.regimeType} (classification confidence ${"%.2f".format(it.confidence)})." }
            ?: "No active market regime was found for this instrument/timeframe."
        return "Recommendation: ${recommendation.recommendationType} (${recommendation.timeHorizon}). $evidenceLine $regimeLine " +
            "This is a first-pass, deterministic rendering of the evidence graph — the graph itself, not this sentence, is the authoritative explanation (see EvidenceLinkEntity rows linked to this recommendation)."
    }
}

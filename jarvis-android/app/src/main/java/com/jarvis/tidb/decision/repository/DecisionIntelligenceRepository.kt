package com.jarvis.tidb.decision.repository

import com.jarvis.tidb.decision.entity.DecisionReviewEntity
import com.jarvis.tidb.decision.entity.DecisionReviewTrigger
import com.jarvis.tidb.decision.entity.RecommendationAlternativeEntity
import com.jarvis.tidb.decision.entity.RecommendationEntity
import com.jarvis.tidb.decision.entity.RecommendationOutcomeEntity
import com.jarvis.tidb.decision.entity.RecommendationRiskAssessmentEntity
import com.jarvis.tidb.decision.entity.RecommendationRiskCategory
import com.jarvis.tidb.decision.entity.RecommendationStatus
import com.jarvis.tidb.intelligence.evidence.entity.OutcomeVerdict
import kotlinx.coroutines.flow.Flow

/**
 * Single facade over the Decision Intelligence Engine's 5 tables. Persistence only — no
 * inference, no scoring, no recommendation algorithm — matching every other module's convention
 * (see `context.repository.MarketContextIntelligenceRepository`'s equivalent doc note) and the
 * approved blueprint's explicit "only implement persistence" instruction.
 *
 * This repository does NOT expose methods for composing confidence scores or writing
 * evidence links directly — callers use `intelligence.confidence.repository.ConfidenceRepository`
 * (`scoredEntityType = ScoredEntityType.DECISION`) and
 * `intelligence.evidence.repository.IntelligenceEvidenceRepository`
 * (`linkedEntityType = LinkedEntityType.DECISION`) directly against a
 * [RecommendationEntity.recommendationId], the same cross-module boundary
 * `NewsRepository` and `MarketContextIntelligenceRepository` already document.
 */
interface DecisionIntelligenceRepository {

    // ---- recommendations ----
    /**
     * Inserts [recommendation] along with its risk assessments and alternatives as one unit,
     * wiring the generated recommendationId into each — mirrors
     * `NewsRepository.ingestArticle` / `MarketContextIntelligenceRepository.ingestEvent`'s
     * "record the parent, then wire children" shape.
     */
    suspend fun recordRecommendation(
        recommendation: RecommendationEntity,
        riskAssessments: List<RecommendationRiskAssessmentEntity> = emptyList(),
        alternatives: List<RecommendationAlternativeEntity> = emptyList()
    ): Long

    suspend fun updateRecommendationStatus(recommendationId: Long, status: RecommendationStatus)
    suspend fun linkDecisionRecord(recommendationId: Long, decisionRecordId: Long)
    suspend fun getRecommendation(recommendationId: Long): RecommendationEntity?
    fun observeRecommendationsForInstrument(instrumentId: Long, limit: Int = 100): Flow<List<RecommendationEntity>>
    fun observeRecommendationsForInstrumentByStatus(instrumentId: Long, status: RecommendationStatus, limit: Int = 100): Flow<List<RecommendationEntity>>
    fun observeRecommendationsByStatus(status: RecommendationStatus, limit: Int = 200): Flow<List<RecommendationEntity>>
    /** Recommendations still ACTIVE whose `expiresAt` has passed as of [asOf] — the read path a scheduled sweep uses before calling [updateRecommendationStatus] with EXPIRED. */
    suspend fun findActiveExpiredAsOf(asOf: Long): List<RecommendationEntity>
    fun observeRevisionsOf(recommendationId: Long): Flow<List<RecommendationEntity>>

    /**
     * Records [revision] with `revisesRecommendationId` set, and marks the original
     * SUPERSEDED — the "supersede, don't mutate" pattern already used by
     * `NewsRepository.recordCorrection` / `MarketContextIntelligenceRepository`'s outcome
     * revision handling.
     */
    suspend fun reviseRecommendation(originalRecommendationId: Long, revision: RecommendationEntity): Long

    // ---- risk assessments ----
    fun observeRiskAssessments(recommendationId: Long): Flow<List<RecommendationRiskAssessmentEntity>>
    suspend fun getLatestRiskAssessment(recommendationId: Long, riskCategory: RecommendationRiskCategory): RecommendationRiskAssessmentEntity?
    /** Records a new risk-assessment pass (e.g. triggered by a [recordDecisionReview] call) without touching prior assessments — insert-only, see entity doc. */
    suspend fun recordRiskAssessments(assessments: List<RecommendationRiskAssessmentEntity>)

    // ---- alternatives ----
    fun observeAlternatives(recommendationId: Long): Flow<List<RecommendationAlternativeEntity>>

    // ---- outcomes ----
    suspend fun recordOutcome(outcome: RecommendationOutcomeEntity): Long
    fun observeOutcomes(recommendationId: Long): Flow<List<RecommendationOutcomeEntity>>
    suspend fun getLatestOutcome(recommendationId: Long): RecommendationOutcomeEntity?
    fun observeOutcomesByVerdict(verdict: OutcomeVerdict, limit: Int = 200): Flow<List<RecommendationOutcomeEntity>>

    // ---- reviews ----
    suspend fun recordDecisionReview(review: DecisionReviewEntity): Long
    fun observeReviews(recommendationId: Long): Flow<List<DecisionReviewEntity>>
    fun observeReviewsByTrigger(triggerType: DecisionReviewTrigger, limit: Int = 100): Flow<List<DecisionReviewEntity>>
}

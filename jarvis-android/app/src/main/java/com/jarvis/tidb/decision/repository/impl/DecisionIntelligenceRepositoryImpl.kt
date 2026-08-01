package com.jarvis.tidb.decision.repository.impl

import com.jarvis.tidb.decision.dao.DecisionReviewDao
import com.jarvis.tidb.decision.dao.RecommendationAlternativeDao
import com.jarvis.tidb.decision.dao.RecommendationDao
import com.jarvis.tidb.decision.dao.RecommendationOutcomeDao
import com.jarvis.tidb.decision.dao.RecommendationRiskAssessmentDao
import com.jarvis.tidb.decision.entity.DecisionReviewEntity
import com.jarvis.tidb.decision.entity.DecisionReviewTrigger
import com.jarvis.tidb.decision.entity.RecommendationAlternativeEntity
import com.jarvis.tidb.decision.entity.RecommendationEntity
import com.jarvis.tidb.decision.entity.RecommendationOutcomeEntity
import com.jarvis.tidb.decision.entity.RecommendationRiskAssessmentEntity
import com.jarvis.tidb.decision.entity.RecommendationRiskCategory
import com.jarvis.tidb.decision.entity.RecommendationStatus
import com.jarvis.tidb.decision.repository.DecisionIntelligenceRepository
import com.jarvis.tidb.intelligence.evidence.entity.OutcomeVerdict
import kotlinx.coroutines.flow.Flow

class DecisionIntelligenceRepositoryImpl(
    private val recommendationDao: RecommendationDao,
    private val riskAssessmentDao: RecommendationRiskAssessmentDao,
    private val alternativeDao: RecommendationAlternativeDao,
    private val outcomeDao: RecommendationOutcomeDao,
    private val reviewDao: DecisionReviewDao
) : DecisionIntelligenceRepository {

    // ---- recommendations ----

    override suspend fun recordRecommendation(
        recommendation: RecommendationEntity,
        riskAssessments: List<RecommendationRiskAssessmentEntity>,
        alternatives: List<RecommendationAlternativeEntity>
    ): Long {
        val recommendationId = recommendationDao.insert(recommendation)
        if (riskAssessments.isNotEmpty()) {
            riskAssessmentDao.insertAll(riskAssessments.map { it.copy(recommendationId = recommendationId) })
        }
        if (alternatives.isNotEmpty()) {
            alternativeDao.insertAll(alternatives.map { it.copy(recommendationId = recommendationId) })
        }
        return recommendationId
    }

    override suspend fun updateRecommendationStatus(recommendationId: Long, status: RecommendationStatus) {
        val recommendation = recommendationDao.findById(recommendationId) ?: return
        val resolvedAt = if (status != RecommendationStatus.DRAFT && status != RecommendationStatus.ACTIVE) {
            System.currentTimeMillis()
        } else {
            recommendation.resolvedAt
        }
        recommendationDao.update(
            recommendation.copy(status = status, resolvedAt = resolvedAt, audit = recommendation.audit.touched())
        )
    }

    override suspend fun linkDecisionRecord(recommendationId: Long, decisionRecordId: Long) {
        val recommendation = recommendationDao.findById(recommendationId) ?: return
        recommendationDao.update(
            recommendation.copy(linkedDecisionRecordId = decisionRecordId, audit = recommendation.audit.touched())
        )
    }

    override suspend fun getRecommendation(recommendationId: Long): RecommendationEntity? =
        recommendationDao.findById(recommendationId)

    override fun observeRecommendationsForInstrument(instrumentId: Long, limit: Int): Flow<List<RecommendationEntity>> =
        recommendationDao.observeForInstrument(instrumentId, limit)

    override fun observeRecommendationsForInstrumentByStatus(instrumentId: Long, status: RecommendationStatus, limit: Int): Flow<List<RecommendationEntity>> =
        recommendationDao.observeForInstrumentByStatus(instrumentId, status, limit)

    override fun observeRecommendationsByStatus(status: RecommendationStatus, limit: Int): Flow<List<RecommendationEntity>> =
        recommendationDao.observeByStatus(status, limit)

    override suspend fun findActiveExpiredAsOf(asOf: Long): List<RecommendationEntity> =
        recommendationDao.findActiveExpiredAsOf(asOf)

    override fun observeRevisionsOf(recommendationId: Long): Flow<List<RecommendationEntity>> =
        recommendationDao.observeRevisionsOf(recommendationId)

    override suspend fun reviseRecommendation(originalRecommendationId: Long, revision: RecommendationEntity): Long {
        val original = recommendationDao.findById(originalRecommendationId)
        val revisionId = recommendationDao.insert(revision.copy(revisesRecommendationId = originalRecommendationId))
        if (original != null) {
            recommendationDao.update(
                original.copy(status = RecommendationStatus.SUPERSEDED, resolvedAt = System.currentTimeMillis(), audit = original.audit.touched())
            )
        }
        return revisionId
    }

    // ---- risk assessments ----

    override fun observeRiskAssessments(recommendationId: Long): Flow<List<RecommendationRiskAssessmentEntity>> =
        riskAssessmentDao.observeForRecommendation(recommendationId)

    override suspend fun getLatestRiskAssessment(recommendationId: Long, riskCategory: RecommendationRiskCategory): RecommendationRiskAssessmentEntity? =
        riskAssessmentDao.findLatestForCategory(recommendationId, riskCategory)

    override suspend fun recordRiskAssessments(assessments: List<RecommendationRiskAssessmentEntity>) {
        if (assessments.isNotEmpty()) riskAssessmentDao.insertAll(assessments)
    }

    // ---- alternatives ----

    override fun observeAlternatives(recommendationId: Long): Flow<List<RecommendationAlternativeEntity>> =
        alternativeDao.observeForRecommendation(recommendationId)

    // ---- outcomes ----

    override suspend fun recordOutcome(outcome: RecommendationOutcomeEntity): Long = outcomeDao.insert(outcome)

    override fun observeOutcomes(recommendationId: Long): Flow<List<RecommendationOutcomeEntity>> =
        outcomeDao.observeForRecommendation(recommendationId)

    override suspend fun getLatestOutcome(recommendationId: Long): RecommendationOutcomeEntity? =
        outcomeDao.findLatestForRecommendation(recommendationId)

    override fun observeOutcomesByVerdict(verdict: OutcomeVerdict, limit: Int): Flow<List<RecommendationOutcomeEntity>> =
        outcomeDao.observeByVerdict(verdict, limit)

    // ---- reviews ----

    override suspend fun recordDecisionReview(review: DecisionReviewEntity): Long = reviewDao.insert(review)

    override fun observeReviews(recommendationId: Long): Flow<List<DecisionReviewEntity>> =
        reviewDao.observeForRecommendation(recommendationId)

    override fun observeReviewsByTrigger(triggerType: DecisionReviewTrigger, limit: Int): Flow<List<DecisionReviewEntity>> =
        reviewDao.observeByTriggerType(triggerType, limit)
}

package com.jarvis.tidb.decision.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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

@Dao
interface RecommendationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(recommendation: RecommendationEntity): Long

    @Update
    suspend fun update(recommendation: RecommendationEntity)

    @Query("SELECT * FROM decision_recommendations WHERE recommendationId = :recommendationId")
    suspend fun findById(recommendationId: Long): RecommendationEntity?

    @Query("SELECT * FROM decision_recommendations WHERE isDeleted = 0 AND instrumentId = :instrumentId ORDER BY decidedAt DESC LIMIT :limit")
    fun observeForInstrument(instrumentId: Long, limit: Int = 100): Flow<List<RecommendationEntity>>

    @Query("SELECT * FROM decision_recommendations WHERE isDeleted = 0 AND instrumentId = :instrumentId AND status = :status ORDER BY decidedAt DESC LIMIT :limit")
    fun observeForInstrumentByStatus(instrumentId: Long, status: RecommendationStatus, limit: Int = 100): Flow<List<RecommendationEntity>>

    @Query("SELECT * FROM decision_recommendations WHERE isDeleted = 0 AND status = :status ORDER BY decidedAt DESC LIMIT :limit")
    fun observeByStatus(status: RecommendationStatus, limit: Int = 200): Flow<List<RecommendationEntity>>

    @Query("SELECT * FROM decision_recommendations WHERE isDeleted = 0 AND status = 'ACTIVE' AND expiresAt IS NOT NULL AND expiresAt <= :asOf")
    suspend fun findActiveExpiredAsOf(asOf: Long): List<RecommendationEntity>

    @Query("SELECT * FROM decision_recommendations WHERE revisesRecommendationId = :recommendationId")
    fun observeRevisionsOf(recommendationId: Long): Flow<List<RecommendationEntity>>

    @Query("SELECT * FROM decision_recommendations WHERE linkedDecisionRecordId = :decisionRecordId LIMIT 1")
    suspend fun findByLinkedDecisionRecord(decisionRecordId: Long): RecommendationEntity?
}

@Dao
interface RecommendationRiskAssessmentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(assessments: List<RecommendationRiskAssessmentEntity>): List<Long>

    @Query("SELECT * FROM recommendation_risk_assessments WHERE recommendationId = :recommendationId ORDER BY assessedAt DESC")
    fun observeForRecommendation(recommendationId: Long): Flow<List<RecommendationRiskAssessmentEntity>>

    @Query("SELECT * FROM recommendation_risk_assessments WHERE recommendationId = :recommendationId AND riskCategory = :riskCategory ORDER BY assessedAt DESC LIMIT 1")
    suspend fun findLatestForCategory(recommendationId: Long, riskCategory: RecommendationRiskCategory): RecommendationRiskAssessmentEntity?
}

@Dao
interface RecommendationAlternativeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(alternatives: List<RecommendationAlternativeEntity>): List<Long>

    @Query("SELECT * FROM recommendation_alternatives WHERE recommendationId = :recommendationId ORDER BY consideredAt ASC")
    fun observeForRecommendation(recommendationId: Long): Flow<List<RecommendationAlternativeEntity>>
}

@Dao
interface RecommendationOutcomeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(outcome: RecommendationOutcomeEntity): Long

    @Query("SELECT * FROM recommendation_outcomes WHERE recommendationId = :recommendationId ORDER BY evaluatedAt DESC")
    fun observeForRecommendation(recommendationId: Long): Flow<List<RecommendationOutcomeEntity>>

    @Query("SELECT * FROM recommendation_outcomes WHERE recommendationId = :recommendationId ORDER BY evaluatedAt DESC LIMIT 1")
    suspend fun findLatestForRecommendation(recommendationId: Long): RecommendationOutcomeEntity?

    @Query("SELECT * FROM recommendation_outcomes WHERE verdict = :verdict ORDER BY evaluatedAt DESC LIMIT :limit")
    fun observeByVerdict(verdict: OutcomeVerdict, limit: Int = 200): Flow<List<RecommendationOutcomeEntity>>
}

@Dao
interface DecisionReviewDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(review: DecisionReviewEntity): Long

    @Query("SELECT * FROM decision_reviews WHERE recommendationId = :recommendationId ORDER BY reviewedAt DESC")
    fun observeForRecommendation(recommendationId: Long): Flow<List<DecisionReviewEntity>>

    @Query("SELECT * FROM decision_reviews WHERE triggerType = :triggerType ORDER BY reviewedAt DESC LIMIT :limit")
    fun observeByTriggerType(triggerType: DecisionReviewTrigger, limit: Int = 100): Flow<List<DecisionReviewEntity>>
}

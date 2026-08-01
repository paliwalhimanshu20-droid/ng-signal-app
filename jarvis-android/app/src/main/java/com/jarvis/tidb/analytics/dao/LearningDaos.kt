package com.jarvis.tidb.analytics.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jarvis.tidb.analytics.entity.EvidenceSourceType
import com.jarvis.tidb.analytics.entity.FailureAnalysisEntity
import com.jarvis.tidb.analytics.entity.LearningEntityType
import com.jarvis.tidb.analytics.entity.LearningEvidenceLinkEntity
import com.jarvis.tidb.analytics.entity.LearningInsightEntity
import com.jarvis.tidb.analytics.entity.LearningObservationEntity
import com.jarvis.tidb.analytics.entity.OptimizationSuggestionEntity
import com.jarvis.tidb.analytics.entity.PatternDiscoveryEntity
import com.jarvis.tidb.analytics.entity.SuggestionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface LearningObservationDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(observation: LearningObservationEntity): Long

    @Query("SELECT * FROM learning_observations ORDER BY generatedAt DESC")
    fun observeAll(): Flow<List<LearningObservationEntity>>

    @Query("SELECT * FROM learning_observations WHERE relatedTradeRowId = :tradeRowId ORDER BY generatedAt DESC")
    fun observeByTrade(tradeRowId: Long): Flow<List<LearningObservationEntity>>

    @Query("SELECT * FROM learning_observations WHERE relatedInstrumentId = :instrumentId ORDER BY generatedAt DESC")
    fun observeByInstrument(instrumentId: Long): Flow<List<LearningObservationEntity>>

    @Query("SELECT * FROM learning_observations WHERE relatedStrategyId = :strategyId ORDER BY generatedAt DESC")
    fun observeByStrategy(strategyId: String): Flow<List<LearningObservationEntity>>

    @Query("SELECT * FROM learning_observations WHERE confidence >= :minConfidence ORDER BY confidence DESC")
    fun observeByMinConfidence(minConfidence: Double): Flow<List<LearningObservationEntity>>
}

@Dao
interface LearningInsightDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(insight: LearningInsightEntity): Long

    @Query("SELECT * FROM learning_insights WHERE isDeleted = 0 ORDER BY generatedAt DESC")
    fun observeAll(): Flow<List<LearningInsightEntity>>

    @Query("SELECT * FROM learning_insights WHERE category = :category AND isDeleted = 0 ORDER BY generatedAt DESC")
    fun observeByCategory(category: String): Flow<List<LearningInsightEntity>>
}

@Dao
interface OptimizationSuggestionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(suggestion: OptimizationSuggestionEntity): Long

    @Update
    suspend fun update(suggestion: OptimizationSuggestionEntity)

    @Query("SELECT * FROM optimization_suggestions ORDER BY impactScore DESC")
    fun observeAll(): Flow<List<OptimizationSuggestionEntity>>

    @Query("SELECT * FROM optimization_suggestions WHERE status = :status ORDER BY impactScore DESC")
    fun observeByStatus(status: SuggestionStatus): Flow<List<OptimizationSuggestionEntity>>

    @Query("UPDATE optimization_suggestions SET status = :status, reviewedBy = :reviewedBy, reviewedAt = :reviewedAt WHERE rowId = :rowId")
    suspend fun updateStatus(rowId: Long, status: SuggestionStatus, reviewedBy: String, reviewedAt: Long = System.currentTimeMillis())
}

@Dao
interface PatternDiscoveryDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(pattern: PatternDiscoveryEntity): Long

    @Query("SELECT * FROM pattern_discoveries WHERE patternKey = :patternKey ORDER BY lastObservedAt DESC LIMIT 1")
    suspend fun findLatestByKey(patternKey: String): PatternDiscoveryEntity?

    @Query("SELECT * FROM pattern_discoveries ORDER BY confidence DESC")
    fun observeAll(): Flow<List<PatternDiscoveryEntity>>
}

@Dao
interface FailureAnalysisDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(analysis: FailureAnalysisEntity): Long

    @Query("SELECT * FROM failure_analyses WHERE relatedTradeRowId = :tradeRowId")
    fun observeByTrade(tradeRowId: Long): Flow<List<FailureAnalysisEntity>>

    @Query("SELECT * FROM failure_analyses WHERE relatedBacktestRunRowId = :runRowId")
    fun observeByBacktestRun(runRowId: Long): Flow<List<FailureAnalysisEntity>>

    @Query("SELECT * FROM failure_analyses WHERE category = :category ORDER BY generatedAt DESC")
    fun observeByCategory(category: String): Flow<List<FailureAnalysisEntity>>
}

@Dao
interface LearningEvidenceLinkDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(link: LearningEvidenceLinkEntity): Long

    @Query("SELECT * FROM learning_evidence_links WHERE linkedEntityType = :entityType AND linkedEntityRowId = :entityRowId")
    fun observeForEntity(entityType: LearningEntityType, entityRowId: Long): Flow<List<LearningEvidenceLinkEntity>>

    @Query("SELECT * FROM learning_evidence_links WHERE sourceType = :sourceType AND sourceRowId = :sourceRowId")
    fun observeForSource(sourceType: EvidenceSourceType, sourceRowId: Long): Flow<List<LearningEvidenceLinkEntity>>
}

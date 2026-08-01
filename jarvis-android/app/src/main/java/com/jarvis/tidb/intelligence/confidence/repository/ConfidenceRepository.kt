package com.jarvis.tidb.intelligence.confidence.repository

import com.jarvis.tidb.intelligence.confidence.entity.ConfidenceModelEntity
import com.jarvis.tidb.intelligence.confidence.entity.ConfidenceScoreEntity
import com.jarvis.tidb.intelligence.confidence.entity.ScoredEntityType
import kotlinx.coroutines.flow.Flow

interface ConfidenceRepository {
    suspend fun defineModel(model: ConfidenceModelEntity): Long
    suspend fun getModel(modelId: Long): ConfidenceModelEntity?
    suspend fun getModelByKey(modelKey: String): ConfidenceModelEntity?
    fun observeActiveModels(): Flow<List<ConfidenceModelEntity>>

    suspend fun recordScore(score: ConfidenceScoreEntity): Long
    fun observeScoresForEntity(scoredEntityType: ScoredEntityType, scoredEntityRowId: Long): Flow<List<ConfidenceScoreEntity>>
    suspend fun getLatestScore(scoredEntityType: ScoredEntityType, scoredEntityRowId: Long): ConfidenceScoreEntity?
    fun observeScoresForModel(modelId: Long): Flow<List<ConfidenceScoreEntity>>
}

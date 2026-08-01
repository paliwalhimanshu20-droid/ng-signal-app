package com.jarvis.tidb.intelligence.confidence.repository.impl

import com.jarvis.tidb.intelligence.confidence.dao.ConfidenceModelDao
import com.jarvis.tidb.intelligence.confidence.dao.ConfidenceScoreDao
import com.jarvis.tidb.intelligence.confidence.entity.ConfidenceModelEntity
import com.jarvis.tidb.intelligence.confidence.entity.ConfidenceScoreEntity
import com.jarvis.tidb.intelligence.confidence.entity.ScoredEntityType
import com.jarvis.tidb.intelligence.confidence.repository.ConfidenceRepository
import kotlinx.coroutines.flow.Flow

class ConfidenceRepositoryImpl(
    private val modelDao: ConfidenceModelDao,
    private val scoreDao: ConfidenceScoreDao
) : ConfidenceRepository {

    override suspend fun defineModel(model: ConfidenceModelEntity): Long = modelDao.insert(model)

    override suspend fun getModel(modelId: Long): ConfidenceModelEntity? = modelDao.findById(modelId)

    override suspend fun getModelByKey(modelKey: String): ConfidenceModelEntity? = modelDao.findByKey(modelKey)

    override fun observeActiveModels(): Flow<List<ConfidenceModelEntity>> = modelDao.observeActive()

    override suspend fun recordScore(score: ConfidenceScoreEntity): Long = scoreDao.insert(score)

    override fun observeScoresForEntity(scoredEntityType: ScoredEntityType, scoredEntityRowId: Long): Flow<List<ConfidenceScoreEntity>> =
        scoreDao.observeForEntity(scoredEntityType.value, scoredEntityRowId)

    override suspend fun getLatestScore(scoredEntityType: ScoredEntityType, scoredEntityRowId: Long): ConfidenceScoreEntity? =
        scoreDao.findLatestForEntity(scoredEntityType.value, scoredEntityRowId)

    override fun observeScoresForModel(modelId: Long): Flow<List<ConfidenceScoreEntity>> = scoreDao.observeForModel(modelId)
}

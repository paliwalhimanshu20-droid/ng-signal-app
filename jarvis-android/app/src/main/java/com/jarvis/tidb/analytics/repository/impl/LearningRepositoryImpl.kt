package com.jarvis.tidb.analytics.repository.impl

import com.jarvis.tidb.analytics.dao.FailureAnalysisDao
import com.jarvis.tidb.analytics.dao.LearningEvidenceLinkDao
import com.jarvis.tidb.analytics.dao.LearningInsightDao
import com.jarvis.tidb.analytics.dao.LearningObservationDao
import com.jarvis.tidb.analytics.dao.OptimizationSuggestionDao
import com.jarvis.tidb.analytics.dao.PatternDiscoveryDao
import com.jarvis.tidb.analytics.entity.EvidenceSourceType
import com.jarvis.tidb.analytics.entity.FailureAnalysisEntity
import com.jarvis.tidb.analytics.entity.LearningEntityType
import com.jarvis.tidb.analytics.entity.LearningEvidenceLinkEntity
import com.jarvis.tidb.analytics.entity.LearningInsightEntity
import com.jarvis.tidb.analytics.entity.LearningObservationEntity
import com.jarvis.tidb.analytics.entity.OptimizationSuggestionEntity
import com.jarvis.tidb.analytics.entity.PatternDiscoveryEntity
import com.jarvis.tidb.analytics.entity.SuggestionStatus
import com.jarvis.tidb.analytics.repository.LearningRepository
import kotlinx.coroutines.flow.Flow

class LearningRepositoryImpl(
    private val observationDao: LearningObservationDao,
    private val insightDao: LearningInsightDao,
    private val suggestionDao: OptimizationSuggestionDao,
    private val patternDao: PatternDiscoveryDao,
    private val failureDao: FailureAnalysisDao,
    private val evidenceLinkDao: LearningEvidenceLinkDao
) : LearningRepository {

    override suspend fun recordObservation(observation: LearningObservationEntity): Long =
        observationDao.insert(observation)

    override fun observeObservations(): Flow<List<LearningObservationEntity>> = observationDao.observeAll()

    override fun observeObservationsByTrade(tradeRowId: Long): Flow<List<LearningObservationEntity>> =
        observationDao.observeByTrade(tradeRowId)

    override fun observeObservationsByInstrument(instrumentId: Long): Flow<List<LearningObservationEntity>> =
        observationDao.observeByInstrument(instrumentId)

    override fun observeObservationsByStrategy(strategyId: String): Flow<List<LearningObservationEntity>> =
        observationDao.observeByStrategy(strategyId)

    override fun observeObservationsByMinConfidence(minConfidence: Double): Flow<List<LearningObservationEntity>> =
        observationDao.observeByMinConfidence(minConfidence)

    override suspend fun recordInsight(insight: LearningInsightEntity): Long = insightDao.insert(insight)

    override fun observeInsights(): Flow<List<LearningInsightEntity>> = insightDao.observeAll()

    override fun observeInsightsByCategory(category: String): Flow<List<LearningInsightEntity>> =
        insightDao.observeByCategory(category)

    override suspend fun proposeSuggestion(suggestion: OptimizationSuggestionEntity): Long =
        suggestionDao.insert(suggestion)

    override suspend fun updateSuggestionStatus(rowId: Long, status: SuggestionStatus, reviewedBy: String) =
        suggestionDao.updateStatus(rowId, status, reviewedBy)

    override fun observeSuggestions(): Flow<List<OptimizationSuggestionEntity>> = suggestionDao.observeAll()

    override fun observeSuggestionsByStatus(status: SuggestionStatus): Flow<List<OptimizationSuggestionEntity>> =
        suggestionDao.observeByStatus(status)

    override suspend fun recordPattern(pattern: PatternDiscoveryEntity): Long = patternDao.insert(pattern)

    override suspend fun latestPattern(patternKey: String): PatternDiscoveryEntity? =
        patternDao.findLatestByKey(patternKey)

    override fun observePatterns(): Flow<List<PatternDiscoveryEntity>> = patternDao.observeAll()

    override suspend fun recordFailureAnalysis(analysis: FailureAnalysisEntity): Long = failureDao.insert(analysis)

    override fun observeFailureAnalysesByTrade(tradeRowId: Long): Flow<List<FailureAnalysisEntity>> =
        failureDao.observeByTrade(tradeRowId)

    override fun observeFailureAnalysesByBacktestRun(runRowId: Long): Flow<List<FailureAnalysisEntity>> =
        failureDao.observeByBacktestRun(runRowId)

    override fun observeFailureAnalysesByCategory(category: String): Flow<List<FailureAnalysisEntity>> =
        failureDao.observeByCategory(category)

    override suspend fun linkEvidence(
        linkedEntityType: LearningEntityType,
        linkedEntityRowId: Long,
        sourceType: EvidenceSourceType,
        sourceRowId: Long,
        note: String?
    ): Long = evidenceLinkDao.insert(
        LearningEvidenceLinkEntity(
            linkedEntityType = linkedEntityType,
            linkedEntityRowId = linkedEntityRowId,
            sourceType = sourceType,
            sourceRowId = sourceRowId,
            note = note
        )
    )

    override fun observeEvidenceFor(linkedEntityType: LearningEntityType, linkedEntityRowId: Long): Flow<List<LearningEvidenceLinkEntity>> =
        evidenceLinkDao.observeForEntity(linkedEntityType, linkedEntityRowId)

    override fun observeEntitiesSupportedBy(sourceType: EvidenceSourceType, sourceRowId: Long): Flow<List<LearningEvidenceLinkEntity>> =
        evidenceLinkDao.observeForSource(sourceType, sourceRowId)
}

package com.jarvis.tidb.analytics.repository

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

/** Stores AI findings; does not generate them. Every write here is assumed to come from an external inference process or a manual annotation. */
interface LearningRepository {

    suspend fun recordObservation(observation: LearningObservationEntity): Long

    fun observeObservations(): Flow<List<LearningObservationEntity>>

    fun observeObservationsByTrade(tradeRowId: Long): Flow<List<LearningObservationEntity>>

    fun observeObservationsByInstrument(instrumentId: Long): Flow<List<LearningObservationEntity>>

    fun observeObservationsByStrategy(strategyId: String): Flow<List<LearningObservationEntity>>

    fun observeObservationsByMinConfidence(minConfidence: Double): Flow<List<LearningObservationEntity>>

    suspend fun recordInsight(insight: LearningInsightEntity): Long

    fun observeInsights(): Flow<List<LearningInsightEntity>>

    fun observeInsightsByCategory(category: String): Flow<List<LearningInsightEntity>>

    suspend fun proposeSuggestion(suggestion: OptimizationSuggestionEntity): Long

    suspend fun updateSuggestionStatus(rowId: Long, status: SuggestionStatus, reviewedBy: String)

    fun observeSuggestions(): Flow<List<OptimizationSuggestionEntity>>

    fun observeSuggestionsByStatus(status: SuggestionStatus): Flow<List<OptimizationSuggestionEntity>>

    suspend fun recordPattern(pattern: PatternDiscoveryEntity): Long

    suspend fun latestPattern(patternKey: String): PatternDiscoveryEntity?

    fun observePatterns(): Flow<List<PatternDiscoveryEntity>>

    suspend fun recordFailureAnalysis(analysis: FailureAnalysisEntity): Long

    fun observeFailureAnalysesByTrade(tradeRowId: Long): Flow<List<FailureAnalysisEntity>>

    fun observeFailureAnalysesByBacktestRun(runRowId: Long): Flow<List<FailureAnalysisEntity>>

    fun observeFailureAnalysesByCategory(category: String): Flow<List<FailureAnalysisEntity>>

    /** Links a piece of evidence (Signal/Trade/BacktestRun/PerformanceMetric/TimelineEvent) to an insight/suggestion/pattern/observation/failure-analysis row, per v1.0 consolidation item 5. */
    suspend fun linkEvidence(
        linkedEntityType: LearningEntityType,
        linkedEntityRowId: Long,
        sourceType: EvidenceSourceType,
        sourceRowId: Long,
        note: String? = null
    ): Long

    fun observeEvidenceFor(linkedEntityType: LearningEntityType, linkedEntityRowId: Long): Flow<List<LearningEvidenceLinkEntity>>

    fun observeEntitiesSupportedBy(sourceType: EvidenceSourceType, sourceRowId: Long): Flow<List<LearningEvidenceLinkEntity>>
}

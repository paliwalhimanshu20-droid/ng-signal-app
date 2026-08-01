package com.jarvis.tidb.historical.evidence.repository

import com.jarvis.tidb.historical.evidence.entity.ConfidenceComponentEntity
import com.jarvis.tidb.historical.evidence.entity.EvidenceRecordEntity
import com.jarvis.tidb.historical.evidence.entity.MarketObservationEntity
import com.jarvis.tidb.historical.evidence.entity.PatternOccurrenceEntity
import com.jarvis.tidb.historical.evidence.entity.SourceReferenceEntity
import com.jarvis.tidb.historical.evidence.entity.SupportingIndicatorEntity
import kotlinx.coroutines.flow.Flow

/** Single facade over the Evidence Foundation's six tables. No decisioning logic — pure storage/retrieval. */
interface EvidenceRepository {
    suspend fun recordObservation(observation: MarketObservationEntity): Long
    fun observeRecentObservations(instrumentId: Long, limit: Int = 100): Flow<List<MarketObservationEntity>>
    fun observeObservationsByType(instrumentId: Long, type: String): Flow<List<MarketObservationEntity>>

    /**
     * Records [evidence] along with its supporting-indicator links, confidence-component
     * breakdown, and source references as one unit, wiring the generated evidenceId into each.
     */
    suspend fun recordEvidence(
        evidence: EvidenceRecordEntity,
        supportingIndicators: List<SupportingIndicatorEntity> = emptyList(),
        confidenceComponents: List<ConfidenceComponentEntity> = emptyList(),
        sourceReferences: List<SourceReferenceEntity> = emptyList()
    ): Long

    suspend fun getEvidence(evidenceId: Long): EvidenceRecordEntity?
    fun observeRecentEvidence(instrumentId: Long, limit: Int = 100): Flow<List<EvidenceRecordEntity>>
    fun observeEvidenceByType(instrumentId: Long, type: String): Flow<List<EvidenceRecordEntity>>
    fun observeSupportingIndicators(evidenceId: Long): Flow<List<SupportingIndicatorEntity>>
    fun observeConfidenceComponents(evidenceId: Long): Flow<List<ConfidenceComponentEntity>>
    fun observeSourceReferences(evidenceId: Long): Flow<List<SourceReferenceEntity>>

    suspend fun recordPatternOccurrence(occurrence: PatternOccurrenceEntity): Long
    suspend fun updatePatternOutcome(occurrenceId: Long, outcome: com.jarvis.tidb.historical.evidence.entity.PatternOutcome, notes: String?)
    fun observePatternsByInstrumentTimeframe(instrumentId: Long, timeframe: String): Flow<List<PatternOccurrenceEntity>>
    fun observePatternsByName(patternName: String): Flow<List<PatternOccurrenceEntity>>
    fun observePendingPatterns(): Flow<List<PatternOccurrenceEntity>>
}

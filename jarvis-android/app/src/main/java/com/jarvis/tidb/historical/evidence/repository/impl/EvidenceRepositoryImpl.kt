package com.jarvis.tidb.historical.evidence.repository.impl

import com.jarvis.tidb.historical.evidence.dao.ConfidenceComponentDao
import com.jarvis.tidb.historical.evidence.dao.EvidenceRecordDao
import com.jarvis.tidb.historical.evidence.dao.MarketObservationDao
import com.jarvis.tidb.historical.evidence.dao.PatternOccurrenceDao
import com.jarvis.tidb.historical.evidence.dao.SourceReferenceDao
import com.jarvis.tidb.historical.evidence.dao.SupportingIndicatorDao
import com.jarvis.tidb.historical.evidence.entity.ConfidenceComponentEntity
import com.jarvis.tidb.historical.evidence.entity.EvidenceRecordEntity
import com.jarvis.tidb.historical.evidence.entity.MarketObservationEntity
import com.jarvis.tidb.historical.evidence.entity.PatternOccurrenceEntity
import com.jarvis.tidb.historical.evidence.entity.PatternOutcome
import com.jarvis.tidb.historical.evidence.entity.SourceReferenceEntity
import com.jarvis.tidb.historical.evidence.entity.SupportingIndicatorEntity
import com.jarvis.tidb.historical.evidence.repository.EvidenceRepository
import kotlinx.coroutines.flow.Flow

class EvidenceRepositoryImpl(
    private val observationDao: MarketObservationDao,
    private val evidenceDao: EvidenceRecordDao,
    private val patternDao: PatternOccurrenceDao,
    private val supportingIndicatorDao: SupportingIndicatorDao,
    private val confidenceComponentDao: ConfidenceComponentDao,
    private val sourceReferenceDao: SourceReferenceDao
) : EvidenceRepository {

    override suspend fun recordObservation(observation: MarketObservationEntity): Long =
        observationDao.insert(observation)

    override fun observeRecentObservations(instrumentId: Long, limit: Int): Flow<List<MarketObservationEntity>> =
        observationDao.observeRecent(instrumentId, limit)

    override fun observeObservationsByType(instrumentId: Long, type: String): Flow<List<MarketObservationEntity>> =
        observationDao.observeByType(instrumentId, type)

    override suspend fun recordEvidence(
        evidence: EvidenceRecordEntity,
        supportingIndicators: List<SupportingIndicatorEntity>,
        confidenceComponents: List<ConfidenceComponentEntity>,
        sourceReferences: List<SourceReferenceEntity>
    ): Long {
        val evidenceId = evidenceDao.insert(evidence)
        if (supportingIndicators.isNotEmpty()) {
            supportingIndicatorDao.insertAll(supportingIndicators.map { it.copy(evidenceId = evidenceId) })
        }
        if (confidenceComponents.isNotEmpty()) {
            confidenceComponentDao.insertAll(confidenceComponents.map { it.copy(evidenceId = evidenceId) })
        }
        if (sourceReferences.isNotEmpty()) {
            sourceReferenceDao.insertAll(sourceReferences.map { it.copy(evidenceId = evidenceId) })
        }
        return evidenceId
    }

    override suspend fun getEvidence(evidenceId: Long): EvidenceRecordEntity? = evidenceDao.findById(evidenceId)

    override fun observeRecentEvidence(instrumentId: Long, limit: Int): Flow<List<EvidenceRecordEntity>> =
        evidenceDao.observeRecent(instrumentId, limit)

    override fun observeEvidenceByType(instrumentId: Long, type: String): Flow<List<EvidenceRecordEntity>> =
        evidenceDao.observeByType(instrumentId, type)

    override fun observeSupportingIndicators(evidenceId: Long): Flow<List<SupportingIndicatorEntity>> =
        supportingIndicatorDao.observeForEvidence(evidenceId)

    override fun observeConfidenceComponents(evidenceId: Long): Flow<List<ConfidenceComponentEntity>> =
        confidenceComponentDao.observeForEvidence(evidenceId)

    override fun observeSourceReferences(evidenceId: Long): Flow<List<SourceReferenceEntity>> =
        sourceReferenceDao.observeForEvidence(evidenceId)

    override suspend fun recordPatternOccurrence(occurrence: PatternOccurrenceEntity): Long =
        patternDao.insert(occurrence)

    override suspend fun updatePatternOutcome(occurrenceId: Long, outcome: PatternOutcome, notes: String?) {
        val occurrence = patternDao.findById(occurrenceId) ?: return
        patternDao.update(occurrence.copy(outcome = outcome, outcomeNotes = notes, audit = occurrence.audit.touched()))
    }

    override fun observePatternsByInstrumentTimeframe(instrumentId: Long, timeframe: String): Flow<List<PatternOccurrenceEntity>> =
        patternDao.observeByInstrumentTimeframe(instrumentId, timeframe)

    override fun observePatternsByName(patternName: String): Flow<List<PatternOccurrenceEntity>> =
        patternDao.observeByPattern(patternName)

    override fun observePendingPatterns(): Flow<List<PatternOccurrenceEntity>> = patternDao.observePending()
}

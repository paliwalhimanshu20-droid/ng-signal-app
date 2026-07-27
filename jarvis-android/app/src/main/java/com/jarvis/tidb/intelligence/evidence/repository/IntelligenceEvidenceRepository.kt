package com.jarvis.tidb.intelligence.evidence.repository

import com.jarvis.tidb.intelligence.evidence.entity.EvidenceCategoryEntity
import com.jarvis.tidb.intelligence.evidence.entity.EvidenceLinkEntity
import com.jarvis.tidb.intelligence.evidence.entity.EvidenceOutcomeEntity
import com.jarvis.tidb.intelligence.evidence.entity.EvidenceSourceEntity
import com.jarvis.tidb.intelligence.evidence.entity.LinkedEntityType
import com.jarvis.tidb.intelligence.evidence.entity.OutcomeVerdict
import kotlinx.coroutines.flow.Flow

/**
 * Single facade over the Module 5 Evidence Foundation extensions (categories, source registry,
 * generic links, outcome checkpoints). Deliberately does not re-expose
 * [com.jarvis.tidb.historical.evidence.repository.EvidenceRepository]'s own surface — callers
 * needing `EvidenceRecordEntity`/`MarketObservationEntity`/`PatternOccurrenceEntity` CRUD use
 * that repository directly; this one is additive.
 */
interface IntelligenceEvidenceRepository {

    suspend fun createCategory(category: EvidenceCategoryEntity): Long
    suspend fun getCategoryByCode(code: String): EvidenceCategoryEntity?
    fun observeActiveCategories(): Flow<List<EvidenceCategoryEntity>>
    fun observeChildCategories(parentCategoryId: Long): Flow<List<EvidenceCategoryEntity>>

    suspend fun registerSource(source: EvidenceSourceEntity): Long
    suspend fun getSourceByCode(sourceCode: String): EvidenceSourceEntity?
    fun observeActiveSources(): Flow<List<EvidenceSourceEntity>>

    suspend fun linkEvidence(link: EvidenceLinkEntity): Long
    suspend fun linkEvidenceToMany(evidenceId: Long, links: List<EvidenceLinkEntity>): List<Long>
    fun observeLinksForEvidence(evidenceId: Long): Flow<List<EvidenceLinkEntity>>
    fun observeLinksForEntity(linkedEntityType: LinkedEntityType, linkedEntityRowId: Long): Flow<List<EvidenceLinkEntity>>

    suspend fun recordOutcomeCheckpoint(outcome: EvidenceOutcomeEntity): Long
    suspend fun updateOutcomeVerdict(outcomeId: Long, verdict: OutcomeVerdict, notes: String?)
    fun observeOutcomesForEvidence(evidenceId: Long): Flow<List<EvidenceOutcomeEntity>>
    fun observePendingOutcomes(): Flow<List<EvidenceOutcomeEntity>>
}

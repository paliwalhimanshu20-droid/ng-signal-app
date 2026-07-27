package com.jarvis.tidb.intelligence.evidence.repository.impl

import com.jarvis.tidb.intelligence.evidence.dao.EvidenceCategoryDao
import com.jarvis.tidb.intelligence.evidence.dao.EvidenceLinkDao
import com.jarvis.tidb.intelligence.evidence.dao.EvidenceOutcomeDao
import com.jarvis.tidb.intelligence.evidence.dao.EvidenceSourceDao
import com.jarvis.tidb.intelligence.evidence.entity.EvidenceCategoryEntity
import com.jarvis.tidb.intelligence.evidence.entity.EvidenceLinkEntity
import com.jarvis.tidb.intelligence.evidence.entity.EvidenceOutcomeEntity
import com.jarvis.tidb.intelligence.evidence.entity.EvidenceSourceEntity
import com.jarvis.tidb.intelligence.evidence.entity.LinkedEntityType
import com.jarvis.tidb.intelligence.evidence.entity.OutcomeVerdict
import com.jarvis.tidb.intelligence.evidence.repository.IntelligenceEvidenceRepository
import kotlinx.coroutines.flow.Flow

class IntelligenceEvidenceRepositoryImpl(
    private val categoryDao: EvidenceCategoryDao,
    private val sourceDao: EvidenceSourceDao,
    private val linkDao: EvidenceLinkDao,
    private val outcomeDao: EvidenceOutcomeDao
) : IntelligenceEvidenceRepository {

    override suspend fun createCategory(category: EvidenceCategoryEntity): Long = categoryDao.insert(category)

    override suspend fun getCategoryByCode(code: String): EvidenceCategoryEntity? = categoryDao.findByCode(code)

    override fun observeActiveCategories(): Flow<List<EvidenceCategoryEntity>> = categoryDao.observeActive()

    override fun observeChildCategories(parentCategoryId: Long): Flow<List<EvidenceCategoryEntity>> =
        categoryDao.observeChildren(parentCategoryId)

    override suspend fun registerSource(source: EvidenceSourceEntity): Long = sourceDao.insert(source)

    override suspend fun getSourceByCode(sourceCode: String): EvidenceSourceEntity? = sourceDao.findByCode(sourceCode)

    override fun observeActiveSources(): Flow<List<EvidenceSourceEntity>> = sourceDao.observeActive()

    override suspend fun linkEvidence(link: EvidenceLinkEntity): Long = linkDao.insert(link)

    override suspend fun linkEvidenceToMany(evidenceId: Long, links: List<EvidenceLinkEntity>): List<Long> =
        linkDao.insertAll(links.map { it.copy(evidenceId = evidenceId) })

    override fun observeLinksForEvidence(evidenceId: Long): Flow<List<EvidenceLinkEntity>> =
        linkDao.observeForEvidence(evidenceId)

    override fun observeLinksForEntity(linkedEntityType: LinkedEntityType, linkedEntityRowId: Long): Flow<List<EvidenceLinkEntity>> =
        linkDao.observeForLinkedEntity(linkedEntityType.value, linkedEntityRowId)

    override suspend fun recordOutcomeCheckpoint(outcome: EvidenceOutcomeEntity): Long = outcomeDao.insert(outcome)

    override suspend fun updateOutcomeVerdict(outcomeId: Long, verdict: OutcomeVerdict, notes: String?) {
        val existing = outcomeDao.findById(outcomeId) ?: return
        outcomeDao.update(existing.copy(verdict = verdict, notes = notes ?: existing.notes, audit = existing.audit.touched()))
    }

    override fun observeOutcomesForEvidence(evidenceId: Long): Flow<List<EvidenceOutcomeEntity>> =
        outcomeDao.observeForEvidence(evidenceId)

    override fun observePendingOutcomes(): Flow<List<EvidenceOutcomeEntity>> = outcomeDao.observePending()
}

package com.jarvis.tidb.intelligence.evidence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jarvis.tidb.intelligence.evidence.entity.EvidenceCategoryEntity
import com.jarvis.tidb.intelligence.evidence.entity.EvidenceLinkEntity
import com.jarvis.tidb.intelligence.evidence.entity.EvidenceOutcomeEntity
import com.jarvis.tidb.intelligence.evidence.entity.EvidenceSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EvidenceCategoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: EvidenceCategoryEntity): Long

    @Update
    suspend fun update(category: EvidenceCategoryEntity)

    @Query("SELECT * FROM evidence_categories WHERE categoryId = :categoryId")
    suspend fun findById(categoryId: Long): EvidenceCategoryEntity?

    @Query("SELECT * FROM evidence_categories WHERE code = :code")
    suspend fun findByCode(code: String): EvidenceCategoryEntity?

    @Query("SELECT * FROM evidence_categories WHERE isActive = 1 ORDER BY displayName ASC")
    fun observeActive(): Flow<List<EvidenceCategoryEntity>>

    @Query("SELECT * FROM evidence_categories WHERE parentCategoryId = :parentCategoryId ORDER BY displayName ASC")
    fun observeChildren(parentCategoryId: Long): Flow<List<EvidenceCategoryEntity>>
}

@Dao
interface EvidenceSourceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(source: EvidenceSourceEntity): Long

    @Update
    suspend fun update(source: EvidenceSourceEntity)

    @Query("SELECT * FROM evidence_sources WHERE sourceId = :sourceId")
    suspend fun findById(sourceId: Long): EvidenceSourceEntity?

    @Query("SELECT * FROM evidence_sources WHERE sourceCode = :sourceCode")
    suspend fun findByCode(sourceCode: String): EvidenceSourceEntity?

    @Query("SELECT * FROM evidence_sources WHERE isActive = 1 ORDER BY displayName ASC")
    fun observeActive(): Flow<List<EvidenceSourceEntity>>
}

@Dao
interface EvidenceLinkDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(link: EvidenceLinkEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(links: List<EvidenceLinkEntity>): List<Long>

    @Query("SELECT * FROM evidence_links WHERE evidenceId = :evidenceId ORDER BY linkedAt DESC")
    fun observeForEvidence(evidenceId: Long): Flow<List<EvidenceLinkEntity>>

    @Query("SELECT * FROM evidence_links WHERE linkedEntityType = :linkedEntityType AND linkedEntityRowId = :linkedEntityRowId ORDER BY linkedAt DESC")
    fun observeForLinkedEntity(linkedEntityType: String, linkedEntityRowId: Long): Flow<List<EvidenceLinkEntity>>
}

@Dao
interface EvidenceOutcomeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(outcome: EvidenceOutcomeEntity): Long

    @Update
    suspend fun update(outcome: EvidenceOutcomeEntity)

    @Query("SELECT * FROM evidence_outcomes WHERE outcomeId = :outcomeId")
    suspend fun findById(outcomeId: Long): EvidenceOutcomeEntity?

    @Query("SELECT * FROM evidence_outcomes WHERE evidenceId = :evidenceId ORDER BY evaluatedAt DESC")
    fun observeForEvidence(evidenceId: Long): Flow<List<EvidenceOutcomeEntity>>

    @Query("SELECT * FROM evidence_outcomes WHERE verdict = 'PENDING' ORDER BY evaluatedAt ASC")
    fun observePending(): Flow<List<EvidenceOutcomeEntity>>
}

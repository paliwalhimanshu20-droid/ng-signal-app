package com.jarvis.tidb.intelligence.pattern.repository.impl

import com.jarvis.tidb.intelligence.pattern.dao.PatternDao
import com.jarvis.tidb.intelligence.pattern.entity.PatternEntity
import com.jarvis.tidb.intelligence.pattern.entity.PatternFamily
import com.jarvis.tidb.intelligence.pattern.repository.PatternRepository
import kotlinx.coroutines.flow.Flow

class PatternRepositoryImpl(
    private val patternDao: PatternDao
) : PatternRepository {

    override suspend fun definePattern(pattern: PatternEntity): Long = patternDao.insert(pattern)

    override suspend fun updatePattern(pattern: PatternEntity) =
        patternDao.update(pattern.copy(audit = pattern.audit.touched()))

    override suspend fun getPattern(patternId: Long): PatternEntity? = patternDao.findById(patternId)

    override suspend fun getPatternByKey(patternKey: String): PatternEntity? = patternDao.findByKey(patternKey)

    override fun observeActivePatterns(): Flow<List<PatternEntity>> = patternDao.observeActive()

    override fun observePatternsByFamily(family: PatternFamily): Flow<List<PatternEntity>> =
        patternDao.observeByFamily(family.value)

    override suspend fun backfillOccurrenceLinks(patternId: Long, patternKey: String): Int =
        patternDao.backfillOccurrenceLinks(patternId, patternKey)
}

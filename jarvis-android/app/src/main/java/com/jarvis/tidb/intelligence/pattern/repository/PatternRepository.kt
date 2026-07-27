package com.jarvis.tidb.intelligence.pattern.repository

import com.jarvis.tidb.intelligence.pattern.entity.PatternEntity
import com.jarvis.tidb.intelligence.pattern.entity.PatternFamily
import kotlinx.coroutines.flow.Flow

interface PatternRepository {
    suspend fun definePattern(pattern: PatternEntity): Long
    suspend fun updatePattern(pattern: PatternEntity)
    suspend fun getPattern(patternId: Long): PatternEntity?
    suspend fun getPatternByKey(patternKey: String): PatternEntity?
    fun observeActivePatterns(): Flow<List<PatternEntity>>
    fun observePatternsByFamily(family: PatternFamily): Flow<List<PatternEntity>>

    /** Links any pre-existing `pattern_occurrences` rows (Module 4) that used this pattern's key as free text but predate the catalog entry. */
    suspend fun backfillOccurrenceLinks(patternId: Long, patternKey: String): Int
}

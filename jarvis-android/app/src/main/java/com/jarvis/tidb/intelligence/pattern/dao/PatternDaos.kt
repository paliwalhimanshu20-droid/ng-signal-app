package com.jarvis.tidb.intelligence.pattern.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jarvis.tidb.intelligence.pattern.entity.PatternEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PatternDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(pattern: PatternEntity): Long

    @Update
    suspend fun update(pattern: PatternEntity)

    @Query("SELECT * FROM patterns WHERE patternId = :patternId")
    suspend fun findById(patternId: Long): PatternEntity?

    @Query("SELECT * FROM patterns WHERE patternKey = :patternKey")
    suspend fun findByKey(patternKey: String): PatternEntity?

    @Query("SELECT * FROM patterns WHERE isActive = 1 ORDER BY displayName ASC")
    fun observeActive(): Flow<List<PatternEntity>>

    @Query("SELECT * FROM patterns WHERE family = :family AND isActive = 1 ORDER BY displayName ASC")
    fun observeByFamily(family: String): Flow<List<PatternEntity>>

    /** Backfills `pattern_occurrences.patternId` for existing rows whose free-text `patternName` matches this pattern's key. Safe to re-run. */
    @Query("UPDATE pattern_occurrences SET patternId = :patternId WHERE patternName = :patternKey AND patternId IS NULL")
    suspend fun backfillOccurrenceLinks(patternId: Long, patternKey: String): Int
}

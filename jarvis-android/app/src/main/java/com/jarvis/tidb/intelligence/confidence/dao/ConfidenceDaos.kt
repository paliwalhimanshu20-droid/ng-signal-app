package com.jarvis.tidb.intelligence.confidence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jarvis.tidb.intelligence.confidence.entity.ConfidenceModelEntity
import com.jarvis.tidb.intelligence.confidence.entity.ConfidenceScoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfidenceModelDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(model: ConfidenceModelEntity): Long

    @Update
    suspend fun update(model: ConfidenceModelEntity)

    @Query("SELECT * FROM confidence_models WHERE modelId = :modelId")
    suspend fun findById(modelId: Long): ConfidenceModelEntity?

    @Query("SELECT * FROM confidence_models WHERE modelKey = :modelKey")
    suspend fun findByKey(modelKey: String): ConfidenceModelEntity?

    @Query("SELECT * FROM confidence_models WHERE isActive = 1 ORDER BY displayName ASC")
    fun observeActive(): Flow<List<ConfidenceModelEntity>>
}

@Dao
interface ConfidenceScoreDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(score: ConfidenceScoreEntity): Long

    @Query("SELECT * FROM confidence_scores WHERE scoredEntityType = :scoredEntityType AND scoredEntityRowId = :scoredEntityRowId ORDER BY computedAt DESC")
    fun observeForEntity(scoredEntityType: String, scoredEntityRowId: Long): Flow<List<ConfidenceScoreEntity>>

    @Query("SELECT * FROM confidence_scores WHERE scoredEntityType = :scoredEntityType AND scoredEntityRowId = :scoredEntityRowId ORDER BY computedAt DESC LIMIT 1")
    suspend fun findLatestForEntity(scoredEntityType: String, scoredEntityRowId: Long): ConfidenceScoreEntity?

    @Query("SELECT * FROM confidence_scores WHERE modelId = :modelId ORDER BY computedAt DESC")
    fun observeForModel(modelId: Long): Flow<List<ConfidenceScoreEntity>>
}

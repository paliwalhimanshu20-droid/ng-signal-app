package com.jarvis.tidb.optimization.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jarvis.tidb.optimization.entity.OptimizationCombinationEntity
import com.jarvis.tidb.optimization.entity.OptimizationJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OptimizationJobDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(job: OptimizationJobEntity): Long

    @Update
    suspend fun update(job: OptimizationJobEntity)

    @Query("SELECT * FROM optimization_jobs WHERE rowId = :rowId")
    suspend fun findByRowId(rowId: Long): OptimizationJobEntity?

    @Query("SELECT * FROM optimization_jobs WHERE rowId = :rowId")
    fun observeByRowId(rowId: Long): Flow<OptimizationJobEntity?>

    @Query("SELECT * FROM optimization_jobs WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<OptimizationJobEntity>>

    @Query("SELECT * FROM optimization_jobs WHERE componentId = :componentId AND isDeleted = 0 ORDER BY createdAt DESC")
    fun observeByComponent(componentId: String): Flow<List<OptimizationJobEntity>>

    /** "Support interruption recovery": jobs left RUNNING or QUEUED are exactly the ones an app restart (crash, kill, upgrade) could have interrupted mid-flight -- a real recovery flow queries this on startup rather than assuming every prior job finished cleanly. */
    @Query("SELECT * FROM optimization_jobs WHERE status IN ('QUEUED', 'RUNNING') AND isDeleted = 0 ORDER BY createdAt ASC")
    suspend fun findResumable(): List<OptimizationJobEntity>

    @Query("UPDATE optimization_jobs SET status = :status, updatedAt = :updatedAt WHERE rowId = :rowId")
    suspend fun updateStatus(rowId: Long, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE optimization_jobs SET completedCombinations = :completedCombinations, checkpointCombinationIndex = :checkpointCombinationIndex, updatedAt = :updatedAt WHERE rowId = :rowId")
    suspend fun updateProgress(rowId: Long, completedCombinations: Int, checkpointCombinationIndex: Int, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface OptimizationCombinationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(combination: OptimizationCombinationEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(combinations: List<OptimizationCombinationEntity>): List<Long>

    @Update
    suspend fun update(combination: OptimizationCombinationEntity)

    @Query("SELECT * FROM optimization_combinations WHERE rowId = :rowId")
    suspend fun findByRowId(rowId: Long): OptimizationCombinationEntity?

    @Query("SELECT * FROM optimization_combinations WHERE jobRowId = :jobRowId ORDER BY combinationIndex ASC")
    fun observeByJob(jobRowId: Long): Flow<List<OptimizationCombinationEntity>>

    /** "Support interruption recovery": ordered by combinationIndex so resuming a job means literally continuing this exact query's result, not re-deriving order from anything else. */
    @Query("SELECT * FROM optimization_combinations WHERE jobRowId = :jobRowId AND status = 'PENDING' ORDER BY combinationIndex ASC")
    suspend fun findPendingByJob(jobRowId: Long): List<OptimizationCombinationEntity>

    @Query("SELECT * FROM optimization_combinations WHERE jobRowId = :jobRowId AND status = 'COMPLETED' ORDER BY rank ASC")
    suspend fun findCompletedRanked(jobRowId: Long): List<OptimizationCombinationEntity>

    /** Phase 4B Slice 3, Step 4 addition: every COMPLETED combination regardless of rank, in deterministic [OptimizationCombinationEntity.combinationIndex] order -- the raw candidate list a ranking engine consumes, distinct from [findCompletedRanked]'s "already ranked" scope (see [com.jarvis.tidb.optimization.repository.OptimizationRepository.completedCombinations]'s own doc). */
    @Query("SELECT * FROM optimization_combinations WHERE jobRowId = :jobRowId AND status = 'COMPLETED' ORDER BY combinationIndex ASC")
    suspend fun findCompletedByJob(jobRowId: Long): List<OptimizationCombinationEntity>

    @Query("UPDATE optimization_combinations SET status = :status, backtestRunRowId = :backtestRunRowId, backtestResultRowId = :backtestResultRowId, updatedAt = :updatedAt WHERE rowId = :rowId")
    suspend fun markEvaluated(rowId: Long, status: String, backtestRunRowId: Long?, backtestResultRowId: Long?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE optimization_combinations SET status = :status, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE rowId = :rowId")
    suspend fun markFailed(rowId: Long, status: String, errorMessage: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE optimization_combinations SET rank = :rank, updatedAt = :updatedAt WHERE rowId = :rowId")
    suspend fun setRank(rowId: Long, rank: Int?, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM optimization_combinations WHERE jobRowId = :jobRowId AND status = 'COMPLETED'")
    suspend fun countCompleted(jobRowId: Long): Int
}

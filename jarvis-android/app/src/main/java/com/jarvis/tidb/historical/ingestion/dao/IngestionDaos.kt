package com.jarvis.tidb.historical.ingestion.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jarvis.tidb.historical.ingestion.entity.DataProviderEntity
import com.jarvis.tidb.historical.ingestion.entity.IngestionCheckpointEntity
import com.jarvis.tidb.historical.ingestion.entity.IngestionJobEntity
import com.jarvis.tidb.historical.ingestion.entity.IngestionJobLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DataProviderDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(provider: DataProviderEntity): Long

    @Update
    suspend fun update(provider: DataProviderEntity)

    @Query("SELECT * FROM data_providers WHERE providerId = :providerId AND isDeleted = 0")
    suspend fun findById(providerId: Long): DataProviderEntity?

    @Query("SELECT * FROM data_providers WHERE providerCode = :providerCode AND isDeleted = 0 LIMIT 1")
    suspend fun findByCode(providerCode: String): DataProviderEntity?

    @Query("SELECT * FROM data_providers WHERE isActive = 1 AND isDeleted = 0 ORDER BY priority ASC")
    fun observeActive(): Flow<List<DataProviderEntity>>

    @Query("SELECT * FROM data_providers WHERE isDeleted = 0 ORDER BY priority ASC")
    fun observeAll(): Flow<List<DataProviderEntity>>

    @Query("UPDATE data_providers SET isDeleted = 1, deletedAt = :now WHERE providerId = :providerId")
    suspend fun softDelete(providerId: Long, now: Long = System.currentTimeMillis())
}

@Dao
interface IngestionJobDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(job: IngestionJobEntity): Long

    @Update
    suspend fun update(job: IngestionJobEntity)

    @Query("SELECT * FROM ingestion_jobs WHERE jobId = :jobId")
    suspend fun findById(jobId: Long): IngestionJobEntity?

    @Query("SELECT * FROM ingestion_jobs WHERE jobId = :jobId")
    fun observeById(jobId: Long): Flow<IngestionJobEntity?>

    @Query("SELECT * FROM ingestion_jobs WHERE status IN ('PENDING', 'RUNNING', 'RETRYING') AND isDeleted = 0 ORDER BY createdAt ASC")
    fun observeActiveJobs(): Flow<List<IngestionJobEntity>>

    @Query(
        """
        SELECT * FROM ingestion_jobs
        WHERE status = 'RETRYING' AND nextRetryAt IS NOT NULL AND nextRetryAt <= :now AND isDeleted = 0
        ORDER BY nextRetryAt ASC
        """
    )
    suspend fun findDueForRetry(now: Long = System.currentTimeMillis()): List<IngestionJobEntity>

    @Query("SELECT * FROM ingestion_jobs WHERE instrumentId = :instrumentId AND isDeleted = 0 ORDER BY createdAt DESC")
    fun observeByInstrument(instrumentId: Long): Flow<List<IngestionJobEntity>>

    @Query("SELECT * FROM ingestion_jobs WHERE instrumentId = :instrumentId AND timeframe = :timeframe AND status = 'SUCCEEDED' AND isDeleted = 0 ORDER BY completedAt DESC LIMIT 1")
    suspend fun findLastSuccessful(instrumentId: Long, timeframe: String): IngestionJobEntity?

    @Query("SELECT * FROM ingestion_jobs WHERE isDeleted = 0 ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<IngestionJobEntity>>

    @Query("SELECT COUNT(*) FROM ingestion_jobs WHERE status IN ('PENDING', 'RUNNING', 'RETRYING') AND isDeleted = 0")
    fun observeActiveCount(): Flow<Int>
}

@Dao
interface IngestionJobLogDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(log: IngestionJobLogEntity): Long

    @Query("SELECT * FROM ingestion_job_logs WHERE jobId = :jobId ORDER BY timestamp ASC")
    fun observeByJob(jobId: Long): Flow<List<IngestionJobLogEntity>>

    @Query("SELECT * FROM ingestion_job_logs WHERE jobId = :jobId AND eventType = 'ERROR' ORDER BY timestamp ASC")
    suspend fun getErrors(jobId: Long): List<IngestionJobLogEntity>
}

@Dao
interface IngestionCheckpointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(checkpoint: IngestionCheckpointEntity): Long

    @Query(
        "SELECT * FROM ingestion_checkpoints WHERE providerId = :providerId AND instrumentId = :instrumentId AND timeframe = :timeframe LIMIT 1"
    )
    suspend fun find(providerId: Long, instrumentId: Long, timeframe: String): IngestionCheckpointEntity?

    @Query("SELECT * FROM ingestion_checkpoints WHERE instrumentId = :instrumentId")
    fun observeForInstrument(instrumentId: Long): Flow<List<IngestionCheckpointEntity>>
}

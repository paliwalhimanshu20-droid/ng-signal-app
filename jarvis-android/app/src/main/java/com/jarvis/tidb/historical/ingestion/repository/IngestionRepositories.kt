package com.jarvis.tidb.historical.ingestion.repository

import com.jarvis.tidb.historical.ingestion.entity.DataProviderEntity
import com.jarvis.tidb.historical.ingestion.entity.IngestionCheckpointEntity
import com.jarvis.tidb.historical.ingestion.entity.IngestionEventType
import com.jarvis.tidb.historical.ingestion.entity.IngestionJobEntity
import com.jarvis.tidb.historical.ingestion.entity.IngestionJobLogEntity
import kotlinx.coroutines.flow.Flow

interface DataProviderRepository {
    suspend fun register(provider: DataProviderEntity): Long
    suspend fun update(provider: DataProviderEntity)
    suspend fun getById(providerId: Long): DataProviderEntity?
    suspend fun getByCode(providerCode: String): DataProviderEntity?
    fun observeActive(): Flow<List<DataProviderEntity>>
    fun observeAll(): Flow<List<DataProviderEntity>>
    suspend fun deactivate(providerId: Long)
}

/**
 * Owns the full lifecycle of an ingestion run: creation, progress tracking, retry/recovery,
 * and the append-only log trail. This is the "engine" surface — the multi-source abstraction
 * (which concrete vendor client to call) lives in the app/orchestrator layer above this
 * repository, which only persists what happened.
 */
interface IngestionJobRepository {
    suspend fun createJob(job: IngestionJobEntity): Long
    suspend fun getJob(jobId: Long): IngestionJobEntity?
    fun observeJob(jobId: Long): Flow<IngestionJobEntity?>
    fun observeActiveJobs(): Flow<List<IngestionJobEntity>>
    fun observeByInstrument(instrumentId: Long): Flow<List<IngestionJobEntity>>
    fun observeRecent(limit: Int = 50): Flow<List<IngestionJobEntity>>
    fun observeActiveCount(): Flow<Int>
    suspend fun getDueForRetry(now: Long = System.currentTimeMillis()): List<IngestionJobEntity>
    suspend fun getLastSuccessful(instrumentId: Long, timeframe: String): IngestionJobEntity?

    /** Marks a job RUNNING and appends a STARTED/PROGRESS log line. */
    suspend fun markStarted(jobId: Long, actor: String = "SYSTEM")

    /** Updates progress counters mid-run; appends a PROGRESS log line. */
    suspend fun recordProgress(
        jobId: Long,
        rowsFetched: Long,
        rowsInserted: Long,
        rowsSkipped: Long,
        progressPercent: Double,
        message: String? = null
    )

    /** Terminal success. */
    suspend fun markSucceeded(jobId: Long, actor: String = "SYSTEM")

    /**
     * Terminal or retryable failure. If [retryCount] < the job's `maxRetries`, transitions to
     * RETRYING with [nextRetryAt] (caller supplies its backoff policy); otherwise transitions
     * to FAILED. Always appends an ERROR log line with [error].
     */
    suspend fun recordFailure(jobId: Long, error: String, nextRetryAt: Long?, actor: String = "SYSTEM")

    suspend fun cancel(jobId: Long, reason: String, actor: String = "SYSTEM")

    suspend fun appendLog(
        jobId: Long,
        attemptNumber: Int,
        eventType: IngestionEventType,
        message: String,
        detailsJson: String? = null
    ): Long

    fun observeLogs(jobId: Long): Flow<List<IngestionJobLogEntity>>
    suspend fun getErrorLogs(jobId: Long): List<IngestionJobLogEntity>
}

/** Incremental-update cursor state, keyed per (provider, instrument, timeframe). */
interface IngestionCheckpointRepository {
    suspend fun getCheckpoint(providerId: Long, instrumentId: Long, timeframe: String): IngestionCheckpointEntity?
    suspend fun advanceCheckpoint(
        providerId: Long,
        instrumentId: Long,
        timeframe: String,
        lastSuccessfulTimestamp: Long,
        cursorToken: String? = null
    )
    fun observeForInstrument(instrumentId: Long): Flow<List<IngestionCheckpointEntity>>
}

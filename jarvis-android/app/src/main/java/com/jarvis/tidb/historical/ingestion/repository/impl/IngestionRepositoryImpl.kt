package com.jarvis.tidb.historical.ingestion.repository.impl

import com.jarvis.tidb.historical.ingestion.dao.DataProviderDao
import com.jarvis.tidb.historical.ingestion.dao.IngestionCheckpointDao
import com.jarvis.tidb.historical.ingestion.dao.IngestionJobDao
import com.jarvis.tidb.historical.ingestion.dao.IngestionJobLogDao
import com.jarvis.tidb.historical.ingestion.entity.DataProviderEntity
import com.jarvis.tidb.historical.ingestion.entity.IngestionCheckpointEntity
import com.jarvis.tidb.historical.ingestion.entity.IngestionEventType
import com.jarvis.tidb.historical.ingestion.entity.IngestionJobEntity
import com.jarvis.tidb.historical.ingestion.entity.IngestionJobLogEntity
import com.jarvis.tidb.historical.ingestion.entity.IngestionJobStatus
import com.jarvis.tidb.historical.ingestion.repository.DataProviderRepository
import com.jarvis.tidb.historical.ingestion.repository.IngestionCheckpointRepository
import com.jarvis.tidb.historical.ingestion.repository.IngestionJobRepository
import kotlinx.coroutines.flow.Flow

class DataProviderRepositoryImpl(private val dao: DataProviderDao) : DataProviderRepository {
    override suspend fun register(provider: DataProviderEntity): Long = dao.insert(provider)
    override suspend fun update(provider: DataProviderEntity) =
        dao.update(provider.copy(audit = provider.audit.touched()))
    override suspend fun getById(providerId: Long): DataProviderEntity? = dao.findById(providerId)
    override suspend fun getByCode(providerCode: String): DataProviderEntity? = dao.findByCode(providerCode)
    override fun observeActive(): Flow<List<DataProviderEntity>> = dao.observeActive()
    override fun observeAll(): Flow<List<DataProviderEntity>> = dao.observeAll()
    override suspend fun deactivate(providerId: Long) = dao.softDelete(providerId)
}

class IngestionJobRepositoryImpl(
    private val jobDao: IngestionJobDao,
    private val logDao: IngestionJobLogDao
) : IngestionJobRepository {

    override suspend fun createJob(job: IngestionJobEntity): Long {
        val id = jobDao.insert(job)
        logDao.insert(
            IngestionJobLogEntity(
                jobId = id,
                attemptNumber = 0,
                eventType = IngestionEventType.STARTED,
                message = "Job created: ${job.jobType} for instrument=${job.instrumentId} timeframe=${job.timeframe}"
            )
        )
        return id
    }

    override suspend fun getJob(jobId: Long): IngestionJobEntity? = jobDao.findById(jobId)
    override fun observeJob(jobId: Long): Flow<IngestionJobEntity?> = jobDao.observeById(jobId)
    override fun observeActiveJobs(): Flow<List<IngestionJobEntity>> = jobDao.observeActiveJobs()
    override fun observeByInstrument(instrumentId: Long): Flow<List<IngestionJobEntity>> =
        jobDao.observeByInstrument(instrumentId)
    override fun observeRecent(limit: Int): Flow<List<IngestionJobEntity>> = jobDao.observeRecent(limit)
    override fun observeActiveCount(): Flow<Int> = jobDao.observeActiveCount()
    override suspend fun getDueForRetry(now: Long): List<IngestionJobEntity> = jobDao.findDueForRetry(now)
    override suspend fun getLastSuccessful(instrumentId: Long, timeframe: String): IngestionJobEntity? =
        jobDao.findLastSuccessful(instrumentId, timeframe)

    override suspend fun markStarted(jobId: Long, actor: String) {
        val job = jobDao.findById(jobId) ?: return
        jobDao.update(
            job.copy(
                status = IngestionJobStatus.RUNNING,
                startedAt = System.currentTimeMillis(),
                audit = job.audit.touched(actor = actor)
            )
        )
        appendLog(jobId, job.retryCount, IngestionEventType.STARTED, "Job started")
    }

    override suspend fun recordProgress(
        jobId: Long,
        rowsFetched: Long,
        rowsInserted: Long,
        rowsSkipped: Long,
        progressPercent: Double,
        message: String?
    ) {
        val job = jobDao.findById(jobId) ?: return
        jobDao.update(
            job.copy(
                rowsFetched = rowsFetched,
                rowsInserted = rowsInserted,
                rowsSkipped = rowsSkipped,
                progressPercent = progressPercent.coerceIn(0.0, 100.0),
                audit = job.audit.touched()
            )
        )
        if (message != null) {
            appendLog(jobId, job.retryCount, IngestionEventType.PROGRESS, message)
        }
    }

    override suspend fun markSucceeded(jobId: Long, actor: String) {
        val job = jobDao.findById(jobId) ?: return
        jobDao.update(
            job.copy(
                status = IngestionJobStatus.SUCCEEDED,
                progressPercent = 100.0,
                completedAt = System.currentTimeMillis(),
                lastError = null,
                nextRetryAt = null,
                audit = job.audit.touched(actor = actor)
            )
        )
        appendLog(jobId, job.retryCount, IngestionEventType.COMPLETED, "Job succeeded")
    }

    override suspend fun recordFailure(jobId: Long, error: String, nextRetryAt: Long?, actor: String) {
        val job = jobDao.findById(jobId) ?: return
        val newRetryCount = job.retryCount + 1
        val willRetry = nextRetryAt != null && newRetryCount <= job.maxRetries
        jobDao.update(
            job.copy(
                status = if (willRetry) IngestionJobStatus.RETRYING else IngestionJobStatus.FAILED,
                retryCount = newRetryCount,
                lastError = error,
                nextRetryAt = if (willRetry) nextRetryAt else null,
                completedAt = if (willRetry) null else System.currentTimeMillis(),
                audit = job.audit.touched(actor = actor)
            )
        )
        appendLog(jobId, newRetryCount, IngestionEventType.ERROR, error)
    }

    override suspend fun cancel(jobId: Long, reason: String, actor: String) {
        val job = jobDao.findById(jobId) ?: return
        jobDao.update(
            job.copy(
                status = IngestionJobStatus.CANCELLED,
                completedAt = System.currentTimeMillis(),
                audit = job.audit.touched(actor = actor)
            )
        )
        appendLog(jobId, job.retryCount, IngestionEventType.WARNING, "Job cancelled: $reason")
    }

    override suspend fun appendLog(
        jobId: Long,
        attemptNumber: Int,
        eventType: IngestionEventType,
        message: String,
        detailsJson: String?
    ): Long = logDao.insert(
        IngestionJobLogEntity(
            jobId = jobId,
            attemptNumber = attemptNumber,
            eventType = eventType,
            message = message,
            detailsJson = detailsJson
        )
    )

    override fun observeLogs(jobId: Long): Flow<List<IngestionJobLogEntity>> = logDao.observeByJob(jobId)
    override suspend fun getErrorLogs(jobId: Long): List<IngestionJobLogEntity> = logDao.getErrors(jobId)
}

class IngestionCheckpointRepositoryImpl(
    private val dao: IngestionCheckpointDao
) : IngestionCheckpointRepository {

    override suspend fun getCheckpoint(providerId: Long, instrumentId: Long, timeframe: String): IngestionCheckpointEntity? =
        dao.find(providerId, instrumentId, timeframe)

    override suspend fun advanceCheckpoint(
        providerId: Long,
        instrumentId: Long,
        timeframe: String,
        lastSuccessfulTimestamp: Long,
        cursorToken: String?
    ) {
        val existing = dao.find(providerId, instrumentId, timeframe)
        dao.upsert(
            (existing ?: IngestionCheckpointEntity(providerId = providerId, instrumentId = instrumentId, timeframe = timeframe))
                .copy(
                    lastSuccessfulTimestamp = lastSuccessfulTimestamp,
                    lastRunAt = System.currentTimeMillis(),
                    cursorToken = cursorToken ?: existing?.cursorToken,
                    audit = (existing?.audit ?: com.jarvis.tidb.core.common.AuditMetadata()).touched()
                )
        )
    }

    override fun observeForInstrument(instrumentId: Long): Flow<List<IngestionCheckpointEntity>> =
        dao.observeForInstrument(instrumentId)
}

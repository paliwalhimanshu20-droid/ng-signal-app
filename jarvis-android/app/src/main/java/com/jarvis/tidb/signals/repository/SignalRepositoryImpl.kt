package com.jarvis.tidb.signals.repository

import com.jarvis.tidb.signals.dao.SignalDao
import com.jarvis.tidb.signals.dao.SignalLifecycleDao
import com.jarvis.tidb.signals.entity.SignalEntity
import com.jarvis.tidb.signals.entity.SignalLifecycleEntity
import com.jarvis.tidb.signals.entity.SignalStatus
import com.jarvis.tidb.signals.entity.relation.SignalWithDetails
import com.jarvis.tidb.signals.entity.relation.SignalWithReasonsAndTags
import kotlinx.coroutines.flow.Flow

class SignalRepositoryImpl(
    private val signalDao: SignalDao,
    private val lifecycleDao: SignalLifecycleDao
) : SignalRepository {

    override suspend fun createSignal(signal: SignalEntity): Long {
        val signalId = signalDao.insert(signal)
        // Seed the lifecycle trail with the signal's initial status so the audit log always
        // has a starting point, even for signals that never transition further.
        lifecycleDao.insert(
            SignalLifecycleEntity(
                signalId = signalId,
                previousStatus = null,
                newStatus = signal.status.value,
                reason = "Signal created",
                changedBy = signal.createdBy
            )
        )
        return signalId
    }

    override suspend fun updateSignal(signal: SignalEntity) {
        signalDao.update(signal.copy(updatedAt = System.currentTimeMillis(), version = signal.version + 1))
    }

    override suspend fun transitionStatus(
        signalId: Long,
        newStatus: String,
        reason: String?,
        changedBy: String
    ) {
        val existing = signalDao.findByRowId(signalId)
        val now = System.currentTimeMillis()

        lifecycleDao.insert(
            SignalLifecycleEntity(
                signalId = signalId,
                previousStatus = existing?.status?.value,
                newStatus = newStatus,
                reason = reason,
                changedBy = changedBy
            )
        )

        // Only overwrite the coarse SignalEntity.status when newStatus maps onto one of the
        // four canonical values; granular milestones (e.g. "TARGET1 HIT") are recorded in the
        // lifecycle table only and don't change the coarse status field.
        val mapped = runCatching { SignalStatus.valueOf(newStatus) }.getOrNull()
        if (existing != null && mapped != null) {
            signalDao.update(
                existing.copy(status = mapped, updatedAt = now, updatedBy = changedBy, version = existing.version + 1)
            )
        }
    }

    override suspend fun softDeleteSignal(signalId: Long, deletedBy: String) {
        val now = System.currentTimeMillis()
        signalDao.softDelete(signalId, now)
        lifecycleDao.insert(
            SignalLifecycleEntity(
                signalId = signalId,
                previousStatus = null,
                newStatus = "DELETED",
                reason = "Soft deleted",
                changedBy = deletedBy
            )
        )
    }

    override fun observeById(signalId: Long): Flow<SignalEntity?> = signalDao.observeById(signalId)
    override fun observeByUuid(uuid: String): Flow<SignalEntity?> = signalDao.observeByUuid(uuid)

    override fun observeActiveSignals(): Flow<List<SignalEntity>> = signalDao.observeActiveSignals()
    override fun observeActiveCount(): Flow<Int> = signalDao.observeActiveCount()

    override fun observeByInstrument(instrumentId: Long): Flow<List<SignalEntity>> =
        signalDao.observeByInstrument(instrumentId)

    override fun observeActiveByInstrument(instrumentId: Long): Flow<List<SignalEntity>> =
        signalDao.observeActiveByInstrument(instrumentId)

    override fun observeByTimeframe(timeframe: String): Flow<List<SignalEntity>> =
        signalDao.observeByTimeframe(timeframe)

    override fun observeByMinConfidence(minConfidence: Double): Flow<List<SignalEntity>> =
        signalDao.observeByMinConfidence(minConfidence)

    override fun observeByStatus(status: String): Flow<List<SignalEntity>> = signalDao.observeByStatus(status)

    override fun observeBySignalType(signalType: String): Flow<List<SignalEntity>> =
        signalDao.observeBySignalType(signalType)

    override fun observeBetweenDates(startTime: Long, endTime: Long): Flow<List<SignalEntity>> =
        signalDao.observeBetweenDates(startTime, endTime)

    override fun observeByTag(tag: String): Flow<List<SignalEntity>> = signalDao.observeByTag(tag)

    override fun observeLatest(): Flow<SignalEntity?> = signalDao.observeLatest()

    override fun observeLatestForInstrument(instrumentId: Long): Flow<SignalEntity?> =
        signalDao.observeLatestForInstrument(instrumentId)

    override suspend fun findByUuid(uuid: String): SignalEntity? = signalDao.findByUuid(uuid)

    override fun observeWithDetails(signalId: Long): Flow<SignalWithDetails?> =
        signalDao.observeWithDetails(signalId)

    override fun observeWithDetailsByUuid(uuid: String): Flow<SignalWithDetails?> =
        signalDao.observeWithDetailsByUuid(uuid)

    override fun observeActiveWithReasonsAndTags(): Flow<List<SignalWithReasonsAndTags>> =
        signalDao.observeActiveWithReasonsAndTags()
}

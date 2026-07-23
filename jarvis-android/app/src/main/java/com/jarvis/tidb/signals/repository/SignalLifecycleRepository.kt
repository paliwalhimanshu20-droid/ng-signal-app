package com.jarvis.tidb.signals.repository

import com.jarvis.tidb.signals.dao.SignalLifecycleDao
import com.jarvis.tidb.signals.entity.SignalLifecycleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Direct write access is exposed here for cases outside the coarse ACTIVE/EXECUTED/EXPIRED/
 * CANCELLED status model (e.g. recording a granular "TARGET1 HIT" milestone that doesn't change
 * `SignalEntity.status`). For status changes that DO affect the coarse status, prefer
 * `SignalRepository.transitionStatus()`, which keeps this trail and the signal row in sync.
 */
interface SignalLifecycleRepository {
    suspend fun recordEvent(event: SignalLifecycleEntity): Long
    fun observeBySignal(signalId: Long): Flow<List<SignalLifecycleEntity>>
    fun observeLatestForSignal(signalId: Long): Flow<SignalLifecycleEntity?>
    fun observeByNewStatus(status: String): Flow<List<SignalLifecycleEntity>>
}

class SignalLifecycleRepositoryImpl(
    private val dao: SignalLifecycleDao
) : SignalLifecycleRepository {
    override suspend fun recordEvent(event: SignalLifecycleEntity): Long = dao.insert(event)
    override fun observeBySignal(signalId: Long): Flow<List<SignalLifecycleEntity>> = dao.observeBySignal(signalId)
    override fun observeLatestForSignal(signalId: Long): Flow<SignalLifecycleEntity?> =
        dao.observeLatestForSignal(signalId)
    override fun observeByNewStatus(status: String): Flow<List<SignalLifecycleEntity>> =
        dao.observeByNewStatus(status)
}

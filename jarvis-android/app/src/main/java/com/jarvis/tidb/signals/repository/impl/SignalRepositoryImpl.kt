package com.jarvis.tidb.signals.repository.impl

import com.jarvis.tidb.signals.dao.SignalDao
import com.jarvis.tidb.signals.dao.SignalLifecycleDao
import com.jarvis.tidb.signals.dao.SignalNoteDao
import com.jarvis.tidb.signals.dao.SignalReasonDao
import com.jarvis.tidb.signals.dao.SignalSnapshotDao
import com.jarvis.tidb.signals.dao.SignalTagDao
import com.jarvis.tidb.signals.entity.SignalEntity
import com.jarvis.tidb.signals.entity.SignalFullDetail
import com.jarvis.tidb.signals.entity.SignalLifecycleEntity
import com.jarvis.tidb.signals.entity.SignalNoteEntity
import com.jarvis.tidb.signals.entity.SignalReasonEntity
import com.jarvis.tidb.signals.entity.SignalSnapshotEntity
import com.jarvis.tidb.signals.entity.SignalTagEntity
import com.jarvis.tidb.signals.entity.SignalWithTags
import com.jarvis.tidb.signals.repository.SignalRepository
import kotlinx.coroutines.flow.Flow

class SignalRepositoryImpl(
    private val signalDao: SignalDao,
    private val reasonDao: SignalReasonDao,
    private val snapshotDao: SignalSnapshotDao,
    private val lifecycleDao: SignalLifecycleDao,
    private val tagDao: SignalTagDao,
    private val noteDao: SignalNoteDao
) : SignalRepository {

    override suspend fun createSignal(signal: SignalEntity): Long {
        val id = signalDao.insert(signal)
        lifecycleDao.insert(
            SignalLifecycleEntity(signalId = id, previousStatus = null, newStatus = signal.status.name, reason = "Signal generated", changedBy = signal.generatedBy)
        )
        return id
    }

    override suspend fun transitionStatus(signalId: Long, newStatus: String, reason: String?, changedBy: String) {
        val now = System.currentTimeMillis()
        val existing = signalDao.findByRowId(signalId)
        val mapped = runCatching { com.jarvis.tidb.signals.entity.SignalStatus.valueOf(newStatus) }.getOrNull()
        if (existing != null && mapped != null) {
            signalDao.update(existing.copy(status = mapped, updatedAt = now, updatedBy = changedBy, version = existing.version + 1))
        }
        lifecycleDao.insert(
            SignalLifecycleEntity(signalId = signalId, previousStatus = existing?.status?.name, newStatus = newStatus, reason = reason, changedBy = changedBy)
        )
    }

    override suspend fun exists(signalId: Long): Boolean = signalDao.exists(signalId)

    override suspend fun softDeleteSignal(signalId: Long, deletedBy: String) {
        val now = System.currentTimeMillis()
        signalDao.softDelete(signalId, now)
        lifecycleDao.insert(SignalLifecycleEntity(signalId = signalId, previousStatus = null, newStatus = "DELETED", reason = "Soft deleted", changedBy = deletedBy))
    }

    override fun observeById(signalId: Long): Flow<SignalEntity?> = signalDao.observeById(signalId)
    override fun observeByUuid(uuid: String): Flow<SignalEntity?> = signalDao.observeByUuid(uuid)
    override suspend fun findByUuid(uuid: String): SignalEntity? = signalDao.findByUuid(uuid)
    override fun observeActiveSignals(): Flow<List<SignalEntity>> = signalDao.observeActiveSignals()
    override fun observeActiveCount(): Flow<Int> = signalDao.observeActiveCount()
    override fun observeByInstrument(instrumentId: Long): Flow<List<SignalEntity>> = signalDao.observeByInstrument(instrumentId)
    override fun observeActiveByInstrument(instrumentId: Long): Flow<List<SignalEntity>> = signalDao.observeActiveByInstrument(instrumentId)
    override fun observeByTimeframe(timeframe: String): Flow<List<SignalEntity>> = signalDao.observeByTimeframe(timeframe)
    override fun observeByMinConfidence(minConfidence: Double): Flow<List<SignalEntity>> = signalDao.observeByMinConfidence(minConfidence)
    override fun observeByStatus(status: String): Flow<List<SignalEntity>> = signalDao.observeByStatus(status)
    override fun observeBySignalType(signalType: String): Flow<List<SignalEntity>> = signalDao.observeBySignalType(signalType)
    override fun observeBetweenDates(startTime: Long, endTime: Long): Flow<List<SignalEntity>> = signalDao.observeBetweenDates(startTime, endTime)
    override fun observeByTag(tag: String): Flow<List<SignalEntity>> = signalDao.observeByTag(tag)
    override fun observeLatest(): Flow<SignalEntity?> = signalDao.observeLatest()
    override fun observeLatestForInstrument(instrumentId: Long): Flow<SignalEntity?> = signalDao.observeLatestForInstrument(instrumentId)
    override suspend fun getWithTags(signalId: Long): SignalWithTags? = signalDao.getWithTags(signalId)
    override suspend fun getFullDetail(signalId: Long): SignalFullDetail? = signalDao.getFullDetail(signalId)

    override suspend fun addReason(reason: SignalReasonEntity): Long = reasonDao.insert(reason)
    override fun observeReasons(signalId: Long): Flow<List<SignalReasonEntity>> = reasonDao.observeBySignal(signalId)

    override suspend fun recordSnapshot(snapshot: SignalSnapshotEntity): Long = snapshotDao.insert(snapshot)
    override suspend fun getSnapshot(signalId: Long): SignalSnapshotEntity? = snapshotDao.findBySignal(signalId)

    override fun observeLifecycle(signalId: Long): Flow<List<SignalLifecycleEntity>> = lifecycleDao.observeBySignal(signalId)

    override suspend fun addTag(tag: SignalTagEntity): Long = tagDao.insert(tag)
    override fun observeTags(signalId: Long): Flow<List<SignalTagEntity>> = tagDao.observeBySignal(signalId)

    override suspend fun addNote(note: SignalNoteEntity): Long = noteDao.insert(note)
    override suspend fun removeNote(note: SignalNoteEntity) = noteDao.delete(note.noteId)
    override fun observeNotes(signalId: Long): Flow<List<SignalNoteEntity>> = noteDao.observeBySignal(signalId)
    override fun observeNotesByAuthor(author: String): Flow<List<SignalNoteEntity>> = noteDao.observeByAuthor(author)
}

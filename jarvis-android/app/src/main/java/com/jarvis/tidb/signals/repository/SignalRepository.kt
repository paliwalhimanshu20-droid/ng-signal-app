package com.jarvis.tidb.signals.repository

import com.jarvis.tidb.signals.entity.SignalEntity
import com.jarvis.tidb.signals.entity.SignalFullDetail
import com.jarvis.tidb.signals.entity.SignalLifecycleEntity
import com.jarvis.tidb.signals.entity.SignalNoteEntity
import com.jarvis.tidb.signals.entity.SignalReasonEntity
import com.jarvis.tidb.signals.entity.SignalSnapshotEntity
import com.jarvis.tidb.signals.entity.SignalTagEntity
import com.jarvis.tidb.signals.entity.SignalWithTags
import kotlinx.coroutines.flow.Flow

interface SignalRepository {
    suspend fun createSignal(signal: SignalEntity): Long
    suspend fun transitionStatus(signalId: Long, newStatus: String, reason: String? = null, changedBy: String = "SYSTEM")
    suspend fun exists(signalId: Long): Boolean
    suspend fun softDeleteSignal(signalId: Long, deletedBy: String = "SYSTEM")
    fun observeById(signalId: Long): Flow<SignalEntity?>
    fun observeByUuid(uuid: String): Flow<SignalEntity?>
    suspend fun findByUuid(uuid: String): SignalEntity?
    fun observeActiveSignals(): Flow<List<SignalEntity>>
    fun observeActiveCount(): Flow<Int>
    fun observeByInstrument(instrumentId: Long): Flow<List<SignalEntity>>
    fun observeActiveByInstrument(instrumentId: Long): Flow<List<SignalEntity>>
    fun observeByTimeframe(timeframe: String): Flow<List<SignalEntity>>
    fun observeByMinConfidence(minConfidence: Double): Flow<List<SignalEntity>>
    fun observeByStatus(status: String): Flow<List<SignalEntity>>
    fun observeBySignalType(signalType: String): Flow<List<SignalEntity>>
    fun observeBetweenDates(startTime: Long, endTime: Long): Flow<List<SignalEntity>>
    fun observeByTag(tag: String): Flow<List<SignalEntity>>
    fun observeLatest(): Flow<SignalEntity?>
    fun observeLatestForInstrument(instrumentId: Long): Flow<SignalEntity?>
    suspend fun getWithTags(signalId: Long): SignalWithTags?
    suspend fun getFullDetail(signalId: Long): SignalFullDetail?

    suspend fun addReason(reason: SignalReasonEntity): Long
    fun observeReasons(signalId: Long): Flow<List<SignalReasonEntity>>

    suspend fun recordSnapshot(snapshot: SignalSnapshotEntity): Long
    suspend fun getSnapshot(signalId: Long): SignalSnapshotEntity?

    fun observeLifecycle(signalId: Long): Flow<List<SignalLifecycleEntity>>

    suspend fun addTag(tag: SignalTagEntity): Long
    fun observeTags(signalId: Long): Flow<List<SignalTagEntity>>

    suspend fun addNote(note: SignalNoteEntity): Long
    suspend fun removeNote(note: SignalNoteEntity)
    fun observeNotes(signalId: Long): Flow<List<SignalNoteEntity>>
    fun observeNotesByAuthor(author: String): Flow<List<SignalNoteEntity>>
}

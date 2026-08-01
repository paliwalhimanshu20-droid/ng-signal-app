package com.jarvis.tidb.signals.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.jarvis.tidb.signals.entity.SignalEntity
import com.jarvis.tidb.signals.entity.SignalFullDetail
import com.jarvis.tidb.signals.entity.SignalLifecycleEntity
import com.jarvis.tidb.signals.entity.SignalNoteEntity
import com.jarvis.tidb.signals.entity.SignalReasonEntity
import com.jarvis.tidb.signals.entity.SignalSnapshotEntity
import com.jarvis.tidb.signals.entity.SignalTagEntity
import com.jarvis.tidb.signals.entity.SignalWithTags
import kotlinx.coroutines.flow.Flow

@Dao
interface SignalDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(signal: SignalEntity): Long

    @Update
    suspend fun update(signal: SignalEntity)

    @Query("SELECT * FROM signals WHERE signalId = :signalId")
    suspend fun findByRowId(signalId: Long): SignalEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM signals WHERE signalId = :signalId AND isDeleted = 0)")
    suspend fun exists(signalId: Long): Boolean

    @Query("UPDATE signals SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :deletedAt WHERE signalId = :signalId")
    suspend fun softDelete(signalId: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM signals WHERE signalId = :signalId AND isDeleted = 0")
    fun observeById(signalId: Long): Flow<SignalEntity?>

    @Query("SELECT * FROM signals WHERE uuid = :uuid AND isDeleted = 0")
    fun observeByUuid(uuid: String): Flow<SignalEntity?>

    @Query("SELECT * FROM signals WHERE uuid = :uuid AND isDeleted = 0")
    suspend fun findByUuid(uuid: String): SignalEntity?

    @Query("SELECT * FROM signals WHERE status = 'ACTIVE' AND isDeleted = 0 ORDER BY generatedAt DESC")
    fun observeActiveSignals(): Flow<List<SignalEntity>>

    @Query("SELECT COUNT(*) FROM signals WHERE status = 'ACTIVE' AND isDeleted = 0")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT * FROM signals WHERE instrumentId = :instrumentId AND isDeleted = 0 ORDER BY generatedAt DESC")
    fun observeByInstrument(instrumentId: Long): Flow<List<SignalEntity>>

    @Query("SELECT * FROM signals WHERE instrumentId = :instrumentId AND status = 'ACTIVE' AND isDeleted = 0 ORDER BY generatedAt DESC")
    fun observeActiveByInstrument(instrumentId: Long): Flow<List<SignalEntity>>

    @Query("SELECT * FROM signals WHERE timeframe = :timeframe AND isDeleted = 0 ORDER BY generatedAt DESC")
    fun observeByTimeframe(timeframe: String): Flow<List<SignalEntity>>

    @Query("SELECT * FROM signals WHERE confidenceScore >= :minConfidence AND isDeleted = 0 ORDER BY confidenceScore DESC")
    fun observeByMinConfidence(minConfidence: Double): Flow<List<SignalEntity>>

    @Query("SELECT * FROM signals WHERE status = :status AND isDeleted = 0 ORDER BY generatedAt DESC")
    fun observeByStatus(status: String): Flow<List<SignalEntity>>

    @Query("SELECT * FROM signals WHERE signalType = :signalType AND isDeleted = 0 ORDER BY generatedAt DESC")
    fun observeBySignalType(signalType: String): Flow<List<SignalEntity>>

    @Query("SELECT * FROM signals WHERE generatedAt BETWEEN :startTime AND :endTime AND isDeleted = 0 ORDER BY generatedAt DESC")
    fun observeBetweenDates(startTime: Long, endTime: Long): Flow<List<SignalEntity>>

    @Query("""
        SELECT s.* FROM signals s
        INNER JOIN signal_tags t ON t.signalId = s.signalId
        WHERE t.tag = :tag AND s.isDeleted = 0
        ORDER BY s.generatedAt DESC
    """)
    fun observeByTag(tag: String): Flow<List<SignalEntity>>

    @Query("SELECT * FROM signals WHERE isDeleted = 0 ORDER BY generatedAt DESC LIMIT 1")
    fun observeLatest(): Flow<SignalEntity?>

    @Query("SELECT * FROM signals WHERE instrumentId = :instrumentId AND isDeleted = 0 ORDER BY generatedAt DESC LIMIT 1")
    fun observeLatestForInstrument(instrumentId: Long): Flow<SignalEntity?>

    @Transaction
    @Query("SELECT * FROM signals WHERE signalId = :signalId")
    suspend fun getWithTags(signalId: Long): SignalWithTags?

    @Transaction
    @Query("SELECT * FROM signals WHERE signalId = :signalId")
    suspend fun getFullDetail(signalId: Long): SignalFullDetail?
}

@Dao
interface SignalReasonDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(reason: SignalReasonEntity): Long
    @Query("SELECT * FROM signal_reasons WHERE signalId = :signalId ORDER BY weight DESC")
    fun observeBySignal(signalId: Long): Flow<List<SignalReasonEntity>>
}

@Dao
interface SignalSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(snapshot: SignalSnapshotEntity): Long
    @Query("SELECT * FROM signal_snapshots WHERE signalId = :signalId")
    suspend fun findBySignal(signalId: Long): SignalSnapshotEntity?
}

@Dao
interface SignalLifecycleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: SignalLifecycleEntity): Long
    @Query("SELECT * FROM signal_lifecycle WHERE signalId = :signalId ORDER BY changedAt ASC")
    fun observeBySignal(signalId: Long): Flow<List<SignalLifecycleEntity>>
}

@Dao
interface SignalTagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: SignalTagEntity): Long
    @Query("SELECT * FROM signal_tags WHERE signalId = :signalId")
    fun observeBySignal(signalId: Long): Flow<List<SignalTagEntity>>
}

@Dao
interface SignalNoteDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(note: SignalNoteEntity): Long
    @Query("DELETE FROM signal_notes WHERE noteId = :noteId")
    suspend fun delete(noteId: Long)
    @Query("SELECT * FROM signal_notes WHERE signalId = :signalId ORDER BY createdAt ASC")
    fun observeBySignal(signalId: Long): Flow<List<SignalNoteEntity>>
    @Query("SELECT * FROM signal_notes WHERE author = :author ORDER BY createdAt DESC")
    fun observeByAuthor(author: String): Flow<List<SignalNoteEntity>>
}

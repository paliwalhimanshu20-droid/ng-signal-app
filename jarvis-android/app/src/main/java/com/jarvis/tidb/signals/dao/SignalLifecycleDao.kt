package com.jarvis.tidb.signals.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jarvis.tidb.signals.entity.SignalLifecycleEntity
import kotlinx.coroutines.flow.Flow

/** Append-only: no update/delete methods, this is an audit trail. */
@Dao
interface SignalLifecycleDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: SignalLifecycleEntity): Long

    @Query("SELECT * FROM signal_lifecycle_events WHERE signalId = :signalId ORDER BY changedAt ASC")
    fun observeBySignal(signalId: Long): Flow<List<SignalLifecycleEntity>>

    @Query("SELECT * FROM signal_lifecycle_events WHERE signalId = :signalId ORDER BY changedAt DESC LIMIT 1")
    fun observeLatestForSignal(signalId: Long): Flow<SignalLifecycleEntity?>

    @Query("SELECT * FROM signal_lifecycle_events WHERE newStatus = :status ORDER BY changedAt DESC")
    fun observeByNewStatus(status: String): Flow<List<SignalLifecycleEntity>>
}

package com.jarvis.tidb.signals.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jarvis.tidb.signals.entity.SignalSnapshotEntity
import kotlinx.coroutines.flow.Flow

/**
 * Deliberately has no `update` method. A snapshot is a frozen record of market conditions at
 * signal-generation time and must never change after being written — see [SignalSnapshotEntity].
 */
@Dao
interface SignalSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(snapshot: SignalSnapshotEntity): Long

    @Query("SELECT * FROM signal_snapshots WHERE signalId = :signalId LIMIT 1")
    fun observeBySignal(signalId: Long): Flow<SignalSnapshotEntity?>

    @Query("SELECT * FROM signal_snapshots WHERE uuid = :uuid LIMIT 1")
    suspend fun findByUuid(uuid: String): SignalSnapshotEntity?
}

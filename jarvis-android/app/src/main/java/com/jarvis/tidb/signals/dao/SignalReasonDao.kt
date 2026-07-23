package com.jarvis.tidb.signals.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jarvis.tidb.signals.entity.SignalReasonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SignalReasonDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(reason: SignalReasonEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(reasons: List<SignalReasonEntity>): List<Long>

    @Delete
    suspend fun delete(reason: SignalReasonEntity)

    @Query("SELECT * FROM signal_reasons WHERE signalId = :signalId ORDER BY weight DESC")
    fun observeBySignal(signalId: Long): Flow<List<SignalReasonEntity>>

    @Query("SELECT * FROM signal_reasons WHERE category = :category ORDER BY createdAt DESC")
    fun observeByCategory(category: String): Flow<List<SignalReasonEntity>>

    @Query("SELECT * FROM signal_reasons WHERE uuid = :uuid LIMIT 1")
    suspend fun findByUuid(uuid: String): SignalReasonEntity?
}

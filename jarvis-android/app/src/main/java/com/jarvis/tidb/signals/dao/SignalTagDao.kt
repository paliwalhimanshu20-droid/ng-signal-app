package com.jarvis.tidb.signals.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jarvis.tidb.signals.entity.SignalTagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SignalTagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: SignalTagEntity): Long

    @Delete
    suspend fun delete(tag: SignalTagEntity)

    @Query("DELETE FROM signal_tags WHERE signalId = :signalId AND tag = :tag")
    suspend fun deleteByValue(signalId: Long, tag: String)

    @Query("SELECT * FROM signal_tags WHERE signalId = :signalId ORDER BY tag ASC")
    fun observeBySignal(signalId: Long): Flow<List<SignalTagEntity>>

    @Query("SELECT DISTINCT tag FROM signal_tags ORDER BY tag ASC")
    fun observeAllDistinctTags(): Flow<List<String>>
}

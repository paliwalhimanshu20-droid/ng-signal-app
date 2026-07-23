package com.jarvis.tidb.signals.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jarvis.tidb.signals.entity.SignalNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SignalNoteDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(note: SignalNoteEntity): Long

    @Delete
    suspend fun delete(note: SignalNoteEntity)

    @Query("SELECT * FROM signal_notes WHERE signalId = :signalId ORDER BY createdAt ASC")
    fun observeBySignal(signalId: Long): Flow<List<SignalNoteEntity>>

    @Query("SELECT * FROM signal_notes WHERE author = :author ORDER BY createdAt DESC")
    fun observeByAuthor(author: String): Flow<List<SignalNoteEntity>>
}

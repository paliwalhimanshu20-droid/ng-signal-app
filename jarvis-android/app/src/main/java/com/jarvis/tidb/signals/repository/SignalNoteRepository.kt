package com.jarvis.tidb.signals.repository

import com.jarvis.tidb.signals.dao.SignalNoteDao
import com.jarvis.tidb.signals.entity.SignalNoteEntity
import kotlinx.coroutines.flow.Flow

interface SignalNoteRepository {
    suspend fun addNote(note: SignalNoteEntity): Long
    suspend fun removeNote(note: SignalNoteEntity)
    fun observeBySignal(signalId: Long): Flow<List<SignalNoteEntity>>
    fun observeByAuthor(author: String): Flow<List<SignalNoteEntity>>
}

class SignalNoteRepositoryImpl(
    private val dao: SignalNoteDao
) : SignalNoteRepository {
    override suspend fun addNote(note: SignalNoteEntity): Long = dao.insert(note)
    override suspend fun removeNote(note: SignalNoteEntity) = dao.delete(note)
    override fun observeBySignal(signalId: Long): Flow<List<SignalNoteEntity>> = dao.observeBySignal(signalId)
    override fun observeByAuthor(author: String): Flow<List<SignalNoteEntity>> = dao.observeByAuthor(author)
}

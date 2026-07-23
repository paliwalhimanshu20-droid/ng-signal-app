package com.jarvis.tidb.signals.repository

import com.jarvis.tidb.signals.dao.SignalTagDao
import com.jarvis.tidb.signals.entity.SignalTagEntity
import kotlinx.coroutines.flow.Flow

interface SignalTagRepository {
    suspend fun addTag(signalId: Long, tag: String): Long
    suspend fun removeTag(signalId: Long, tag: String)
    fun observeBySignal(signalId: Long): Flow<List<SignalTagEntity>>
    fun observeAllDistinctTags(): Flow<List<String>>
}

class SignalTagRepositoryImpl(
    private val dao: SignalTagDao
) : SignalTagRepository {
    override suspend fun addTag(signalId: Long, tag: String): Long =
        dao.insert(SignalTagEntity(signalId = signalId, tag = tag))

    override suspend fun removeTag(signalId: Long, tag: String) = dao.deleteByValue(signalId, tag)
    override fun observeBySignal(signalId: Long): Flow<List<SignalTagEntity>> = dao.observeBySignal(signalId)
    override fun observeAllDistinctTags(): Flow<List<String>> = dao.observeAllDistinctTags()
}

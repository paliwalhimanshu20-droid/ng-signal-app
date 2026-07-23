package com.jarvis.tidb.signals.repository

import com.jarvis.tidb.signals.dao.SignalReasonDao
import com.jarvis.tidb.signals.entity.SignalReasonEntity
import kotlinx.coroutines.flow.Flow

interface SignalReasonRepository {
    suspend fun addReason(reason: SignalReasonEntity): Long
    suspend fun addReasons(reasons: List<SignalReasonEntity>): List<Long>
    suspend fun removeReason(reason: SignalReasonEntity)
    fun observeBySignal(signalId: Long): Flow<List<SignalReasonEntity>>
    fun observeByCategory(category: String): Flow<List<SignalReasonEntity>>
    suspend fun findByUuid(uuid: String): SignalReasonEntity?
}

class SignalReasonRepositoryImpl(
    private val dao: SignalReasonDao
) : SignalReasonRepository {
    override suspend fun addReason(reason: SignalReasonEntity): Long = dao.insert(reason)
    override suspend fun addReasons(reasons: List<SignalReasonEntity>): List<Long> = dao.insertAll(reasons)
    override suspend fun removeReason(reason: SignalReasonEntity) = dao.delete(reason)
    override fun observeBySignal(signalId: Long): Flow<List<SignalReasonEntity>> = dao.observeBySignal(signalId)
    override fun observeByCategory(category: String): Flow<List<SignalReasonEntity>> = dao.observeByCategory(category)
    override suspend fun findByUuid(uuid: String): SignalReasonEntity? = dao.findByUuid(uuid)
}

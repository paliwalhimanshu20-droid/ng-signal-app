package com.jarvis.tidb.signals.repository

import com.jarvis.tidb.signals.dao.SignalSnapshotDao
import com.jarvis.tidb.signals.entity.SignalSnapshotEntity
import kotlinx.coroutines.flow.Flow

/** No update method by design — see [SignalSnapshotEntity]: snapshots are immutable once written. */
interface SignalSnapshotRepository {
    suspend fun recordSnapshot(snapshot: SignalSnapshotEntity): Long
    fun observeBySignal(signalId: Long): Flow<SignalSnapshotEntity?>
    suspend fun findByUuid(uuid: String): SignalSnapshotEntity?
}

class SignalSnapshotRepositoryImpl(
    private val dao: SignalSnapshotDao
) : SignalSnapshotRepository {
    override suspend fun recordSnapshot(snapshot: SignalSnapshotEntity): Long = dao.insert(snapshot)
    override fun observeBySignal(signalId: Long): Flow<SignalSnapshotEntity?> = dao.observeBySignal(signalId)
    override suspend fun findByUuid(uuid: String): SignalSnapshotEntity? = dao.findByUuid(uuid)
}

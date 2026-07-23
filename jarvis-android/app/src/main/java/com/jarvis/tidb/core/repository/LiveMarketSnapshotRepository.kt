package com.jarvis.tidb.core.repository

import com.jarvis.tidb.core.dao.LiveMarketSnapshotDao
import com.jarvis.tidb.core.entity.LiveMarketSnapshotEntity
import kotlinx.coroutines.flow.Flow

interface LiveMarketSnapshotRepository {
    suspend fun upsert(snapshot: LiveMarketSnapshotEntity)
    suspend fun upsertAll(snapshots: List<LiveMarketSnapshotEntity>)
    fun observe(instrumentId: Long): Flow<LiveMarketSnapshotEntity?>
    fun observeMany(instrumentIds: List<Long>): Flow<List<LiveMarketSnapshotEntity>>
    fun observeAll(): Flow<List<LiveMarketSnapshotEntity>>
    suspend fun clear(instrumentId: Long)
    suspend fun softDelete(instrumentId: Long, actor: String = "SYSTEM")
}

class LiveMarketSnapshotRepositoryImpl(
    private val dao: LiveMarketSnapshotDao
) : LiveMarketSnapshotRepository {

    override suspend fun upsert(snapshot: LiveMarketSnapshotEntity) = dao.upsert(snapshot)

    override suspend fun upsertAll(snapshots: List<LiveMarketSnapshotEntity>) = dao.upsertAll(snapshots)

    override fun observe(instrumentId: Long): Flow<LiveMarketSnapshotEntity?> = dao.observe(instrumentId)

    override fun observeMany(instrumentIds: List<Long>): Flow<List<LiveMarketSnapshotEntity>> =
        dao.observeMany(instrumentIds)

    override fun observeAll(): Flow<List<LiveMarketSnapshotEntity>> = dao.observeAll()

    override suspend fun clear(instrumentId: Long) = dao.clear(instrumentId)

    override suspend fun softDelete(instrumentId: Long, actor: String) =
        dao.softDelete(instrumentId, System.currentTimeMillis(), actor)
}

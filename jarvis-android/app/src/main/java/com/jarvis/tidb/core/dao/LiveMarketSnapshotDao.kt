package com.jarvis.tidb.core.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jarvis.tidb.core.entity.LiveMarketSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LiveMarketSnapshotDao {

    /**
     * Always REPLACE — instrumentId is the primary key, so an upsert here is what keeps this
     * table at exactly one row per instrument.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: LiveMarketSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(snapshots: List<LiveMarketSnapshotEntity>)

    @Query("SELECT * FROM live_market_snapshots WHERE instrumentId = :instrumentId AND isDeleted = 0")
    fun observe(instrumentId: Long): Flow<LiveMarketSnapshotEntity?>

    @Query("SELECT * FROM live_market_snapshots WHERE instrumentId IN (:instrumentIds) AND isDeleted = 0")
    fun observeMany(instrumentIds: List<Long>): Flow<List<LiveMarketSnapshotEntity>>

    @Query("SELECT * FROM live_market_snapshots WHERE isDeleted = 0")
    fun observeAll(): Flow<List<LiveMarketSnapshotEntity>>

    /** Physical delete, preserved for admin/test use — prefer [softDelete] (Revision 1 §3). */
    @Query("DELETE FROM live_market_snapshots WHERE instrumentId = :instrumentId")
    suspend fun clear(instrumentId: Long)

    @Query(
        """
        UPDATE live_market_snapshots
        SET isDeleted = 1, deletedAt = :now, updatedAt = :now, updatedBy = :actor, version = version + 1
        WHERE instrumentId = :instrumentId
        """
    )
    suspend fun softDelete(instrumentId: Long, now: Long, actor: String = "SYSTEM")
}

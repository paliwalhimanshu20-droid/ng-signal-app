package com.jarvis.tidb.core.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jarvis.tidb.core.entity.EventSeverity
import com.jarvis.tidb.core.entity.MarketEventEntity
import com.jarvis.tidb.core.entity.MarketEventType
import kotlinx.coroutines.flow.Flow

@Dao
interface MarketEventDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: MarketEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<MarketEventEntity>): List<Long>

    /** Physical delete, preserved for admin/test use — prefer [softDelete] (Revision 1 §3). */
    @Delete
    suspend fun delete(event: MarketEventEntity)

    @Query(
        """
        UPDATE market_events
        SET isDeleted = 1, deletedAt = :now, updatedAt = :now, updatedBy = :actor, version = version + 1
        WHERE eventId = :eventId
        """
    )
    suspend fun softDelete(eventId: Long, now: Long, actor: String = "SYSTEM")

    @Query("SELECT * FROM market_events WHERE uuid = :uuid AND isDeleted = 0 LIMIT 1")
    suspend fun getByUuid(uuid: String): MarketEventEntity?

    @Query("SELECT * FROM market_events WHERE instrumentId = :instrumentId AND isDeleted = 0 ORDER BY timestamp DESC")
    fun observeByInstrument(instrumentId: Long): Flow<List<MarketEventEntity>>

    @Query(
        """
        SELECT * FROM market_events
        WHERE instrumentId = :instrumentId AND eventType = :eventType AND isDeleted = 0
        ORDER BY timestamp DESC
        """
    )
    fun observeByInstrumentAndType(
        instrumentId: Long,
        eventType: MarketEventType
    ): Flow<List<MarketEventEntity>>

    @Query(
        """
        SELECT * FROM market_events
        WHERE severity IN (:minSeverities) AND isDeleted = 0
        ORDER BY timestamp DESC LIMIT :limit
        """
    )
    fun observeBySeverity(
        minSeverities: List<EventSeverity> = listOf(EventSeverity.HIGH, EventSeverity.CRITICAL),
        limit: Int = 100
    ): Flow<List<MarketEventEntity>>

    @Query(
        """
        SELECT * FROM market_events
        WHERE instrumentId = :instrumentId AND timestamp BETWEEN :fromEpochMillis AND :toEpochMillis
          AND isDeleted = 0
        ORDER BY timestamp ASC
        """
    )
    fun observeInRange(
        instrumentId: Long,
        fromEpochMillis: Long,
        toEpochMillis: Long
    ): Flow<List<MarketEventEntity>>

    @Query("SELECT COUNT(*) FROM market_events WHERE isDeleted = 0")
    suspend fun count(): Int
}

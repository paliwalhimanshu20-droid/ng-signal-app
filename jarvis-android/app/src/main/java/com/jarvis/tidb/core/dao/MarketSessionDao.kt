package com.jarvis.tidb.core.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jarvis.tidb.core.entity.MarketSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MarketSessionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: MarketSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<MarketSessionEntity>): List<Long>

    @Update
    suspend fun update(session: MarketSessionEntity)

    /** Physical delete, preserved for admin/test use — prefer [softDelete] (Revision 1 §3). */
    @Delete
    suspend fun delete(session: MarketSessionEntity)

    @Query(
        """
        UPDATE market_sessions
        SET isDeleted = 1, deletedAt = :now, updatedAt = :now, updatedBy = :actor, version = version + 1
        WHERE sessionId = :sessionId
        """
    )
    suspend fun softDelete(sessionId: Long, now: Long, actor: String = "SYSTEM")

    @Query("SELECT * FROM market_sessions WHERE sessionId = :sessionId AND isDeleted = 0")
    fun observeById(sessionId: Long): Flow<MarketSessionEntity?>

    @Query("SELECT * FROM market_sessions WHERE uuid = :uuid AND isDeleted = 0 LIMIT 1")
    suspend fun getByUuid(uuid: String): MarketSessionEntity?

    @Query("SELECT * FROM market_sessions WHERE exchangeId = :exchangeId AND isDeleted = 0 ORDER BY openTime ASC")
    fun observeByExchange(exchangeId: Long): Flow<List<MarketSessionEntity>>

    @Query("SELECT * FROM market_sessions WHERE exchangeId = :exchangeId AND holidayFlag = 1 AND isDeleted = 0")
    fun observeHolidays(exchangeId: Long): Flow<List<MarketSessionEntity>>

    @Query("SELECT COUNT(*) FROM market_sessions WHERE isDeleted = 0")
    suspend fun count(): Int
}

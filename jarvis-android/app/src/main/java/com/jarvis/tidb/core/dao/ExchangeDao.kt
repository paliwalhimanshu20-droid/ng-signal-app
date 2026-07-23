package com.jarvis.tidb.core.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.jarvis.tidb.core.entity.ExchangeEntity
import com.jarvis.tidb.core.entity.ExchangeWithInstruments
import com.jarvis.tidb.core.entity.ExchangeWithSessions
import com.jarvis.tidb.core.entity.RecordStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ExchangeDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(exchange: ExchangeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exchanges: List<ExchangeEntity>): List<Long>

    @Update
    suspend fun update(exchange: ExchangeEntity)

    /**
     * Physical delete. Preserved from Module 1 v1 for admin/test-cleanup use only —
     * normal application code should call [softDelete] instead (see Revision 1 §3).
     */
    @Delete
    suspend fun delete(exchange: ExchangeEntity)

    @Query(
        """
        UPDATE exchanges
        SET isDeleted = 1, deletedAt = :now, updatedAt = :now, updatedBy = :actor, version = version + 1
        WHERE exchangeId = :exchangeId
        """
    )
    suspend fun softDelete(exchangeId: Long, now: Long, actor: String = "SYSTEM")

    @Query("SELECT * FROM exchanges WHERE exchangeId = :exchangeId AND isDeleted = 0")
    fun observeById(exchangeId: Long): Flow<ExchangeEntity?>

    @Query("SELECT * FROM exchanges WHERE uuid = :uuid AND isDeleted = 0 LIMIT 1")
    suspend fun getByUuid(uuid: String): ExchangeEntity?

    @Query("SELECT * FROM exchanges WHERE exchangeCode = :exchangeCode AND isDeleted = 0 LIMIT 1")
    suspend fun getByCode(exchangeCode: String): ExchangeEntity?

    @Query("SELECT * FROM exchanges WHERE status = :status AND isDeleted = 0 ORDER BY exchangeName ASC")
    fun observeByStatus(status: RecordStatus = RecordStatus.ACTIVE): Flow<List<ExchangeEntity>>

    @Query("SELECT * FROM exchanges WHERE isDeleted = 0 ORDER BY exchangeName ASC")
    fun observeAll(): Flow<List<ExchangeEntity>>

    @Transaction
    @Query("SELECT * FROM exchanges WHERE exchangeId = :exchangeId AND isDeleted = 0")
    fun observeWithInstruments(exchangeId: Long): Flow<ExchangeWithInstruments?>

    @Transaction
    @Query("SELECT * FROM exchanges WHERE exchangeId = :exchangeId AND isDeleted = 0")
    fun observeWithSessions(exchangeId: Long): Flow<ExchangeWithSessions?>

    @Query("SELECT COUNT(*) FROM exchanges WHERE isDeleted = 0")
    suspend fun count(): Int
}

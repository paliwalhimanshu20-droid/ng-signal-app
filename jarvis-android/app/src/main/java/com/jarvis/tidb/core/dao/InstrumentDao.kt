package com.jarvis.tidb.core.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.jarvis.tidb.core.entity.AssetClass
import com.jarvis.tidb.core.entity.InstrumentEntity
import com.jarvis.tidb.core.entity.InstrumentFullDetail
import com.jarvis.tidb.core.entity.InstrumentWithContracts
import com.jarvis.tidb.core.entity.InstrumentWithLiveSnapshot
import com.jarvis.tidb.core.entity.RecordStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface InstrumentDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(instrument: InstrumentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(instruments: List<InstrumentEntity>): List<Long>

    @Update
    suspend fun update(instrument: InstrumentEntity)

    /** Physical delete, preserved for admin/test use — prefer [softDelete] (Revision 1 §3). */
    @Delete
    suspend fun delete(instrument: InstrumentEntity)

    @Query(
        """
        UPDATE instruments
        SET isDeleted = 1, deletedAt = :now, updatedAt = :now, updatedBy = :actor, version = version + 1
        WHERE instrumentId = :instrumentId
        """
    )
    suspend fun softDelete(instrumentId: Long, now: Long, actor: String = "SYSTEM")

    @Query("SELECT * FROM instruments WHERE instrumentId = :instrumentId AND isDeleted = 0")
    fun observeById(instrumentId: Long): Flow<InstrumentEntity?>

    @Query("SELECT * FROM instruments WHERE uuid = :uuid AND isDeleted = 0 LIMIT 1")
    suspend fun getByUuid(uuid: String): InstrumentEntity?

    @Query("SELECT * FROM instruments WHERE symbol = :symbol AND isDeleted = 0 LIMIT 1")
    suspend fun getBySymbol(symbol: String): InstrumentEntity?

    @Query("SELECT * FROM instruments WHERE brokerInstrumentKey = :key AND isDeleted = 0 LIMIT 1")
    suspend fun getByBrokerInstrumentKey(key: String): InstrumentEntity?

    @Query("SELECT * FROM instruments WHERE exchangeId = :exchangeId AND isDeleted = 0 ORDER BY symbol ASC")
    fun observeByExchange(exchangeId: Long): Flow<List<InstrumentEntity>>

    @Query("SELECT * FROM instruments WHERE assetClass = :assetClass AND isDeleted = 0 ORDER BY symbol ASC")
    fun observeByAssetClass(assetClass: AssetClass): Flow<List<InstrumentEntity>>

    @Query("SELECT * FROM instruments WHERE status = :status AND isDeleted = 0 ORDER BY symbol ASC")
    fun observeByStatus(status: RecordStatus = RecordStatus.ACTIVE): Flow<List<InstrumentEntity>>

    @Query("SELECT * FROM instruments WHERE isDeleted = 0 ORDER BY symbol ASC")
    fun observeAll(): Flow<List<InstrumentEntity>>

    @Transaction
    @Query("SELECT * FROM instruments WHERE instrumentId = :instrumentId AND isDeleted = 0")
    fun observeWithContracts(instrumentId: Long): Flow<InstrumentWithContracts?>

    @Transaction
    @Query("SELECT * FROM instruments WHERE instrumentId = :instrumentId AND isDeleted = 0")
    fun observeWithLiveSnapshot(instrumentId: Long): Flow<InstrumentWithLiveSnapshot?>

    @Transaction
    @Query("SELECT * FROM instruments WHERE instrumentId = :instrumentId AND isDeleted = 0")
    fun observeFullDetail(instrumentId: Long): Flow<InstrumentFullDetail?>

    @Query("SELECT COUNT(*) FROM instruments WHERE isDeleted = 0")
    suspend fun count(): Int
}

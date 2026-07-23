package com.jarvis.tidb.core.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jarvis.tidb.core.entity.ContractEntity
import com.jarvis.tidb.core.entity.ContractTradingStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ContractDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(contract: ContractEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contracts: List<ContractEntity>): List<Long>

    @Update
    suspend fun update(contract: ContractEntity)

    /** Physical delete, preserved for admin/test use — prefer [softDelete] (Revision 1 §3). */
    @Delete
    suspend fun delete(contract: ContractEntity)

    @Query(
        """
        UPDATE contracts
        SET isDeleted = 1, deletedAt = :now, updatedAt = :now, updatedBy = :actor, version = version + 1
        WHERE contractId = :contractId
        """
    )
    suspend fun softDelete(contractId: Long, now: Long, actor: String = "SYSTEM")

    @Query("SELECT * FROM contracts WHERE contractId = :contractId AND isDeleted = 0")
    fun observeById(contractId: Long): Flow<ContractEntity?>

    @Query("SELECT * FROM contracts WHERE uuid = :uuid AND isDeleted = 0 LIMIT 1")
    suspend fun getByUuid(uuid: String): ContractEntity?

    @Query("SELECT * FROM contracts WHERE instrumentId = :instrumentId AND isDeleted = 0 ORDER BY expiryDate ASC")
    fun observeByInstrument(instrumentId: Long): Flow<List<ContractEntity>>

    @Query(
        """
        SELECT * FROM contracts
        WHERE instrumentId = :instrumentId AND tradingStatus = :status AND isDeleted = 0
        ORDER BY expiryDate ASC
        """
    )
    fun observeByInstrumentAndStatus(
        instrumentId: Long,
        status: ContractTradingStatus = ContractTradingStatus.ACTIVE
    ): Flow<List<ContractEntity>>

    @Query(
        """
        SELECT * FROM contracts
        WHERE instrumentId = :instrumentId AND tradingStatus = 'ACTIVE' AND isDeleted = 0
        ORDER BY expiryDate ASC LIMIT 1
        """
    )
    suspend fun getNearestActiveContract(instrumentId: Long): ContractEntity?

    @Query(
        """
        SELECT * FROM contracts
        WHERE expiryDate BETWEEN :fromEpochMillis AND :toEpochMillis AND isDeleted = 0
        ORDER BY expiryDate ASC
        """
    )
    fun observeExpiringBetween(fromEpochMillis: Long, toEpochMillis: Long): Flow<List<ContractEntity>>

    @Query("SELECT COUNT(*) FROM contracts WHERE isDeleted = 0")
    suspend fun count(): Int
}

package com.jarvis.tidb.analytics.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.jarvis.tidb.analytics.entity.TradeEntity
import com.jarvis.tidb.analytics.entity.TradeExecutionEntity
import com.jarvis.tidb.analytics.entity.TradeExitEntity
import com.jarvis.tidb.analytics.entity.TradeFeesEntity
import com.jarvis.tidb.analytics.entity.TradeJournalEntity
import com.jarvis.tidb.analytics.entity.TradeFullHistory
import com.jarvis.tidb.analytics.entity.TradeStatus
import com.jarvis.tidb.analytics.entity.TradeWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(trade: TradeEntity): Long

    @Update
    suspend fun update(trade: TradeEntity)

    @Query("SELECT * FROM trades WHERE rowId = :rowId")
    suspend fun findByRowId(rowId: Long): TradeEntity?

    @Query("SELECT * FROM trades WHERE uuid = :uuid")
    suspend fun findByUuid(uuid: String): TradeEntity?

    @Query("SELECT * FROM trades WHERE isDeleted = 0 ORDER BY entryTimestamp DESC")
    fun observeAll(): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades WHERE signalId = :signalId AND isDeleted = 0")
    fun observeBySignal(signalId: Long): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades WHERE instrumentId = :instrumentId AND isDeleted = 0 ORDER BY entryTimestamp DESC")
    fun observeByInstrument(instrumentId: Long): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades WHERE entryTimestamp BETWEEN :startMillis AND :endMillis AND isDeleted = 0 ORDER BY entryTimestamp DESC")
    fun observeByDateRange(startMillis: Long, endMillis: Long): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades WHERE status = :status AND isDeleted = 0 ORDER BY entryTimestamp DESC")
    fun observeByStatus(status: TradeStatus): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades WHERE strategyId = :strategyId AND isDeleted = 0 ORDER BY entryTimestamp DESC")
    fun observeByStrategy(strategyId: String): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades WHERE status IN ('OPEN', 'PARTIALLY_CLOSED') AND isDeleted = 0")
    fun observeOpenTrades(): Flow<List<TradeEntity>>

    @Transaction
    @Query("SELECT * FROM trades WHERE rowId = :rowId")
    suspend fun getWithDetails(rowId: Long): TradeWithDetails?

    @Transaction
    @Query("SELECT * FROM trades WHERE rowId = :rowId")
    suspend fun getFullHistory(rowId: Long): TradeFullHistory?

    @Query("UPDATE trades SET isDeleted = 1, deletedAt = :deletedAt WHERE rowId = :rowId")
    suspend fun softDelete(rowId: Long, deletedAt: Long = System.currentTimeMillis())
}

@Dao
interface TradeExecutionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(execution: TradeExecutionEntity): Long

    @Query("SELECT * FROM trade_executions WHERE tradeRowId = :tradeRowId ORDER BY executedAt ASC")
    fun observeByTrade(tradeRowId: Long): Flow<List<TradeExecutionEntity>>

    @Query("SELECT * FROM trade_executions WHERE executionType = :type ORDER BY executedAt DESC")
    fun observeByType(type: String): Flow<List<TradeExecutionEntity>>
}

@Dao
interface TradeExitDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(exit: TradeExitEntity): Long

    @Query("SELECT * FROM trade_exits WHERE tradeRowId = :tradeRowId ORDER BY exitedAt ASC")
    fun observeByTrade(tradeRowId: Long): Flow<List<TradeExitEntity>>

    @Query("SELECT * FROM trade_exits WHERE exitReason = :reason ORDER BY exitedAt DESC")
    fun observeByReason(reason: String): Flow<List<TradeExitEntity>>
}

@Dao
interface TradeFeesDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(fee: TradeFeesEntity): Long

    @Query("SELECT * FROM trade_fees WHERE tradeRowId = :tradeRowId")
    fun observeByTrade(tradeRowId: Long): Flow<List<TradeFeesEntity>>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM trade_fees WHERE tradeRowId = :tradeRowId")
    suspend fun totalFeesForTrade(tradeRowId: Long): Double
}

@Dao
interface TradeJournalDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: TradeJournalEntity): Long

    @Query("SELECT * FROM trade_journal WHERE tradeRowId = :tradeRowId AND isDeleted = 0 ORDER BY createdAt ASC")
    fun observeByTrade(tradeRowId: Long): Flow<List<TradeJournalEntity>>

    @Query("SELECT * FROM trade_journal WHERE author = :author AND isDeleted = 0 ORDER BY createdAt DESC")
    fun observeByAuthor(author: String): Flow<List<TradeJournalEntity>>
}

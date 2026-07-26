package com.jarvis.tidb.analytics.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.jarvis.tidb.analytics.entity.BacktestConfigurationEntity
import com.jarvis.tidb.analytics.entity.BacktestEntity
import com.jarvis.tidb.analytics.entity.BacktestResultEntity
import com.jarvis.tidb.analytics.entity.BacktestRunEntity
import com.jarvis.tidb.analytics.entity.BacktestRunWithDetails
import com.jarvis.tidb.analytics.entity.BacktestStatus
import com.jarvis.tidb.analytics.entity.BacktestTradeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BacktestDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(backtest: BacktestEntity): Long

    @Update
    suspend fun update(backtest: BacktestEntity)

    @Query("SELECT * FROM backtests WHERE rowId = :rowId")
    suspend fun findByRowId(rowId: Long): BacktestEntity?

    @Query("SELECT * FROM backtests WHERE strategyId = :strategyId AND isDeleted = 0 ORDER BY createdAt DESC")
    fun observeByStrategy(strategyId: String): Flow<List<BacktestEntity>>

    @Query("SELECT * FROM backtests WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BacktestEntity>>

    @Query("UPDATE backtests SET isDeleted = 1, deletedAt = :deletedAt WHERE rowId = :rowId")
    suspend fun softDelete(rowId: Long, deletedAt: Long = System.currentTimeMillis())
}

@Dao
interface BacktestConfigurationDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(configuration: BacktestConfigurationEntity): Long

    @Query("SELECT * FROM backtest_configurations WHERE backtestRowId = :backtestRowId ORDER BY configVersion DESC")
    fun observeByBacktest(backtestRowId: Long): Flow<List<BacktestConfigurationEntity>>

    @Query("SELECT * FROM backtest_configurations WHERE backtestRowId = :backtestRowId ORDER BY configVersion DESC LIMIT 1")
    suspend fun latestForBacktest(backtestRowId: Long): BacktestConfigurationEntity?
}

@Dao
interface BacktestRunDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(run: BacktestRunEntity): Long

    @Update
    suspend fun update(run: BacktestRunEntity)

    @Query("SELECT * FROM backtest_runs WHERE rowId = :rowId")
    suspend fun findByRowId(rowId: Long): BacktestRunEntity?

    @Query("SELECT * FROM backtest_runs WHERE backtestRowId = :backtestRowId ORDER BY startedAt DESC")
    fun observeByBacktest(backtestRowId: Long): Flow<List<BacktestRunEntity>>

    @Query("SELECT * FROM backtest_runs WHERE status = :status ORDER BY startedAt DESC")
    fun observeByStatus(status: BacktestStatus): Flow<List<BacktestRunEntity>>

    @Transaction
    @Query("SELECT * FROM backtest_runs WHERE rowId = :rowId")
    suspend fun getWithDetails(rowId: Long): BacktestRunWithDetails?
}

@Dao
interface BacktestTradeDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(trades: List<BacktestTradeEntity>): List<Long>

    @Query("SELECT * FROM backtest_trades WHERE runRowId = :runRowId ORDER BY entryTimestamp ASC")
    fun observeByRun(runRowId: Long): Flow<List<BacktestTradeEntity>>

    @Query("SELECT * FROM backtest_trades WHERE instrumentId = :instrumentId ORDER BY entryTimestamp DESC")
    fun observeByInstrument(instrumentId: Long): Flow<List<BacktestTradeEntity>>
}

@Dao
interface BacktestResultDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(result: BacktestResultEntity): Long

    @Query("SELECT * FROM backtest_results WHERE runRowId = :runRowId")
    suspend fun findByRun(runRowId: Long): BacktestResultEntity?

    @Query("""
        SELECT br.* FROM backtest_results br
        INNER JOIN backtest_runs r ON r.rowId = br.runRowId
        WHERE r.backtestRowId = :backtestRowId
        ORDER BY br.rowId DESC
    """)
    fun observeByBacktest(backtestRowId: Long): Flow<List<BacktestResultEntity>>
}

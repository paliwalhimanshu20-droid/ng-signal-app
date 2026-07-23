package com.jarvis.tidb.analytics.repository

import com.jarvis.tidb.analytics.entity.BacktestConfigurationEntity
import com.jarvis.tidb.analytics.entity.BacktestEntity
import com.jarvis.tidb.analytics.entity.BacktestResultEntity
import com.jarvis.tidb.analytics.entity.BacktestRunEntity
import com.jarvis.tidb.analytics.entity.BacktestRunWithDetails
import com.jarvis.tidb.analytics.entity.BacktestStatus
import com.jarvis.tidb.analytics.entity.BacktestTradeEntity
import kotlinx.coroutines.flow.Flow

/**
 * Owns the backtest data model: definitions, versioned configurations, runs, synthetic trades,
 * and result summaries. Does not implement or invoke a simulation engine — that's a future
 * module's responsibility; this repository only stores what the engine produces.
 */
interface BacktestRepository {

    suspend fun createBacktest(backtest: BacktestEntity): Long

    suspend fun updateBacktest(backtest: BacktestEntity)

    suspend fun getBacktest(rowId: Long): BacktestEntity?

    fun observeBacktestsByStrategy(strategyId: String): Flow<List<BacktestEntity>>

    fun observeAllBacktests(): Flow<List<BacktestEntity>>

    suspend fun addConfiguration(configuration: BacktestConfigurationEntity): Long

    suspend fun latestConfiguration(backtestRowId: Long): BacktestConfigurationEntity?

    suspend fun startRun(run: BacktestRunEntity): Long

    suspend fun updateRun(run: BacktestRunEntity)

    fun observeRunsByBacktest(backtestRowId: Long): Flow<List<BacktestRunEntity>>

    fun observeRunsByStatus(status: BacktestStatus): Flow<List<BacktestRunEntity>>

    suspend fun getRunWithDetails(runRowId: Long): BacktestRunWithDetails?

    suspend fun recordGeneratedTrades(trades: List<BacktestTradeEntity>): List<Long>

    fun observeTradesByRun(runRowId: Long): Flow<List<BacktestTradeEntity>>

    suspend fun recordResult(result: BacktestResultEntity): Long

    suspend fun getResultForRun(runRowId: Long): BacktestResultEntity?

    fun observeResultsByBacktest(backtestRowId: Long): Flow<List<BacktestResultEntity>>
}

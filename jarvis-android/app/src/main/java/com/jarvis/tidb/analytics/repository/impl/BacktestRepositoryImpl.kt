package com.jarvis.tidb.analytics.repository.impl

import com.jarvis.tidb.analytics.dao.BacktestConfigurationDao
import com.jarvis.tidb.analytics.dao.BacktestDao
import com.jarvis.tidb.analytics.dao.BacktestResultDao
import com.jarvis.tidb.analytics.dao.BacktestRunDao
import com.jarvis.tidb.analytics.dao.BacktestTradeDao
import com.jarvis.tidb.analytics.entity.BacktestConfigurationEntity
import com.jarvis.tidb.analytics.entity.BacktestEntity
import com.jarvis.tidb.analytics.entity.BacktestResultEntity
import com.jarvis.tidb.analytics.entity.BacktestRunEntity
import com.jarvis.tidb.analytics.entity.BacktestRunWithDetails
import com.jarvis.tidb.analytics.entity.BacktestStatus
import com.jarvis.tidb.analytics.entity.BacktestTradeEntity
import com.jarvis.tidb.analytics.repository.BacktestRepository
import kotlinx.coroutines.flow.Flow

class BacktestRepositoryImpl(
    private val backtestDao: BacktestDao,
    private val configurationDao: BacktestConfigurationDao,
    private val runDao: BacktestRunDao,
    private val tradeDao: BacktestTradeDao,
    private val resultDao: BacktestResultDao
) : BacktestRepository {

    override suspend fun createBacktest(backtest: BacktestEntity): Long = backtestDao.insert(backtest)

    override suspend fun updateBacktest(backtest: BacktestEntity) = backtestDao.update(backtest)

    override suspend fun getBacktest(rowId: Long): BacktestEntity? = backtestDao.findByRowId(rowId)

    override fun observeBacktestsByStrategy(strategyId: String): Flow<List<BacktestEntity>> =
        backtestDao.observeByStrategy(strategyId)

    override fun observeAllBacktests(): Flow<List<BacktestEntity>> = backtestDao.observeAll()

    override suspend fun addConfiguration(configuration: BacktestConfigurationEntity): Long =
        configurationDao.insert(configuration)

    override suspend fun latestConfiguration(backtestRowId: Long): BacktestConfigurationEntity? =
        configurationDao.latestForBacktest(backtestRowId)

    override suspend fun startRun(run: BacktestRunEntity): Long = runDao.insert(run)

    override suspend fun updateRun(run: BacktestRunEntity) = runDao.update(run)

    override fun observeRunsByBacktest(backtestRowId: Long): Flow<List<BacktestRunEntity>> =
        runDao.observeByBacktest(backtestRowId)

    override fun observeRunsByStatus(status: BacktestStatus): Flow<List<BacktestRunEntity>> =
        runDao.observeByStatus(status)

    override suspend fun getRunWithDetails(runRowId: Long): BacktestRunWithDetails? =
        runDao.getWithDetails(runRowId)

    override suspend fun recordGeneratedTrades(trades: List<BacktestTradeEntity>): List<Long> =
        tradeDao.insertAll(trades)

    override fun observeTradesByRun(runRowId: Long): Flow<List<BacktestTradeEntity>> =
        tradeDao.observeByRun(runRowId)

    override suspend fun recordResult(result: BacktestResultEntity): Long = resultDao.insert(result)

    override suspend fun getResultForRun(runRowId: Long): BacktestResultEntity? = resultDao.findByRun(runRowId)

    override fun observeResultsByBacktest(backtestRowId: Long): Flow<List<BacktestResultEntity>> =
        resultDao.observeByBacktest(backtestRowId)
}

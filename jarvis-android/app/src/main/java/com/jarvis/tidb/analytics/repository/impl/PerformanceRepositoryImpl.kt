package com.jarvis.tidb.analytics.repository.impl

import com.jarvis.tidb.analytics.dao.InstrumentPerformanceDao
import com.jarvis.tidb.analytics.dao.MonthlyPerformanceDao
import com.jarvis.tidb.analytics.dao.PerformanceMetricDao
import com.jarvis.tidb.analytics.dao.PerformanceSnapshotDao
import com.jarvis.tidb.analytics.dao.StrategyPerformanceDao
import com.jarvis.tidb.analytics.entity.InstrumentPerformanceEntity
import com.jarvis.tidb.analytics.entity.MonthlyPerformanceEntity
import com.jarvis.tidb.analytics.entity.PerformanceMetricEntity
import com.jarvis.tidb.analytics.entity.PerformanceScope
import com.jarvis.tidb.analytics.entity.PerformanceSnapshotEntity
import com.jarvis.tidb.analytics.entity.PerformanceSnapshotWithMetrics
import com.jarvis.tidb.analytics.entity.StrategyPerformanceEntity
import com.jarvis.tidb.analytics.repository.PerformanceRepository
import kotlinx.coroutines.flow.Flow

class PerformanceRepositoryImpl(
    private val snapshotDao: PerformanceSnapshotDao,
    private val metricDao: PerformanceMetricDao,
    private val strategyDao: StrategyPerformanceDao,
    private val instrumentDao: InstrumentPerformanceDao,
    private val monthlyDao: MonthlyPerformanceDao
) : PerformanceRepository {

    override suspend fun recordSnapshot(
        snapshot: PerformanceSnapshotEntity,
        metrics: List<PerformanceMetricEntity>
    ): Long {
        val snapshotRowId = snapshotDao.insert(snapshot)
        if (metrics.isNotEmpty()) {
            metricDao.insertAll(metrics.map { it.copy(snapshotRowId = snapshotRowId) })
        }
        return snapshotRowId
    }

    override fun observeSnapshotsByScope(scope: PerformanceScope, scopeKey: String): Flow<List<PerformanceSnapshotEntity>> =
        snapshotDao.observeByScope(scope, scopeKey)

    override fun observeSnapshotsByDateRange(startMillis: Long, endMillis: Long): Flow<List<PerformanceSnapshotEntity>> =
        snapshotDao.observeByDateRange(startMillis, endMillis)

    override suspend fun getSnapshotWithMetrics(rowId: Long): PerformanceSnapshotWithMetrics? =
        snapshotDao.getWithMetrics(rowId)

    override suspend fun upsertStrategyPerformance(performance: StrategyPerformanceEntity): Long =
        strategyDao.upsert(performance)

    override fun observeStrategyPerformance(strategyId: String): Flow<StrategyPerformanceEntity?> =
        strategyDao.observeByStrategy(strategyId)

    override fun observeAllStrategyPerformance(): Flow<List<StrategyPerformanceEntity>> = strategyDao.observeAll()

    override suspend fun upsertInstrumentPerformance(performance: InstrumentPerformanceEntity): Long =
        instrumentDao.upsert(performance)

    override fun observeInstrumentPerformance(instrumentId: Long): Flow<InstrumentPerformanceEntity?> =
        instrumentDao.observeByInstrument(instrumentId)

    override fun observeAllInstrumentPerformance(): Flow<List<InstrumentPerformanceEntity>> = instrumentDao.observeAll()

    override suspend fun upsertMonthlyPerformance(performance: MonthlyPerformanceEntity): Long =
        monthlyDao.upsert(performance)

    override suspend fun getMonthlyPerformance(yearMonth: String): MonthlyPerformanceEntity? =
        monthlyDao.findByMonth(yearMonth)

    override fun observeMonthlyPerformance(): Flow<List<MonthlyPerformanceEntity>> = monthlyDao.observeAll()

    override fun observeMonthlyPerformanceRange(startYearMonth: String, endYearMonth: String): Flow<List<MonthlyPerformanceEntity>> =
        monthlyDao.observeByRange(startYearMonth, endYearMonth)
}

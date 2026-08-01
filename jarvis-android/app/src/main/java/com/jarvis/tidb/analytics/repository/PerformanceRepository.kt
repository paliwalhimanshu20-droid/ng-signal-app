package com.jarvis.tidb.analytics.repository

import com.jarvis.tidb.analytics.entity.InstrumentPerformanceEntity
import com.jarvis.tidb.analytics.entity.MonthlyPerformanceEntity
import com.jarvis.tidb.analytics.entity.PerformanceMetricEntity
import com.jarvis.tidb.analytics.entity.PerformanceScope
import com.jarvis.tidb.analytics.entity.PerformanceSnapshotEntity
import com.jarvis.tidb.analytics.entity.PerformanceSnapshotWithMetrics
import com.jarvis.tidb.analytics.entity.StrategyPerformanceEntity
import kotlinx.coroutines.flow.Flow

interface PerformanceRepository {

    suspend fun recordSnapshot(snapshot: PerformanceSnapshotEntity, metrics: List<PerformanceMetricEntity>): Long

    fun observeSnapshotsByScope(scope: PerformanceScope, scopeKey: String): Flow<List<PerformanceSnapshotEntity>>

    fun observeSnapshotsByDateRange(startMillis: Long, endMillis: Long): Flow<List<PerformanceSnapshotEntity>>

    suspend fun getSnapshotWithMetrics(rowId: Long): PerformanceSnapshotWithMetrics?

    suspend fun upsertStrategyPerformance(performance: StrategyPerformanceEntity): Long

    fun observeStrategyPerformance(strategyId: String): Flow<StrategyPerformanceEntity?>

    fun observeAllStrategyPerformance(): Flow<List<StrategyPerformanceEntity>>

    suspend fun upsertInstrumentPerformance(performance: InstrumentPerformanceEntity): Long

    fun observeInstrumentPerformance(instrumentId: Long): Flow<InstrumentPerformanceEntity?>

    fun observeAllInstrumentPerformance(): Flow<List<InstrumentPerformanceEntity>>

    suspend fun upsertMonthlyPerformance(performance: MonthlyPerformanceEntity): Long

    suspend fun getMonthlyPerformance(yearMonth: String): MonthlyPerformanceEntity?

    fun observeMonthlyPerformance(): Flow<List<MonthlyPerformanceEntity>>

    fun observeMonthlyPerformanceRange(startYearMonth: String, endYearMonth: String): Flow<List<MonthlyPerformanceEntity>>
}

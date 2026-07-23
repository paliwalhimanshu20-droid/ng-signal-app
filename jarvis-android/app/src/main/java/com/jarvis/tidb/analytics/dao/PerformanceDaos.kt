package com.jarvis.tidb.analytics.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.jarvis.tidb.analytics.entity.InstrumentPerformanceEntity
import com.jarvis.tidb.analytics.entity.MonthlyPerformanceEntity
import com.jarvis.tidb.analytics.entity.PerformanceMetricEntity
import com.jarvis.tidb.analytics.entity.PerformanceScope
import com.jarvis.tidb.analytics.entity.PerformanceSnapshotEntity
import com.jarvis.tidb.analytics.entity.PerformanceSnapshotWithMetrics
import com.jarvis.tidb.analytics.entity.StrategyPerformanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PerformanceSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(snapshot: PerformanceSnapshotEntity): Long

    @Query("SELECT * FROM performance_snapshots WHERE scope = :scope AND scopeKey = :scopeKey ORDER BY snapshotAt DESC")
    fun observeByScope(scope: PerformanceScope, scopeKey: String): Flow<List<PerformanceSnapshotEntity>>

    @Query("SELECT * FROM performance_snapshots WHERE snapshotAt BETWEEN :startMillis AND :endMillis ORDER BY snapshotAt DESC")
    fun observeByDateRange(startMillis: Long, endMillis: Long): Flow<List<PerformanceSnapshotEntity>>

    @Transaction
    @Query("SELECT * FROM performance_snapshots WHERE rowId = :rowId")
    suspend fun getWithMetrics(rowId: Long): PerformanceSnapshotWithMetrics?
}

@Dao
interface PerformanceMetricDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metric: PerformanceMetricEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(metrics: List<PerformanceMetricEntity>): List<Long>

    @Query("SELECT * FROM performance_metrics WHERE snapshotRowId = :snapshotRowId")
    fun observeBySnapshot(snapshotRowId: Long): Flow<List<PerformanceMetricEntity>>

    @Query("SELECT * FROM performance_metrics WHERE metricName = :metricName ORDER BY rowId DESC")
    fun observeByMetricName(metricName: String): Flow<List<PerformanceMetricEntity>>
}

@Dao
interface StrategyPerformanceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(performance: StrategyPerformanceEntity): Long

    @Update
    suspend fun update(performance: StrategyPerformanceEntity)

    @Query("SELECT * FROM strategy_performance WHERE strategyId = :strategyId")
    fun observeByStrategy(strategyId: String): Flow<StrategyPerformanceEntity?>

    @Query("SELECT * FROM strategy_performance ORDER BY netProfit DESC")
    fun observeAll(): Flow<List<StrategyPerformanceEntity>>
}

@Dao
interface InstrumentPerformanceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(performance: InstrumentPerformanceEntity): Long

    @Update
    suspend fun update(performance: InstrumentPerformanceEntity)

    @Query("SELECT * FROM instrument_performance WHERE instrumentId = :instrumentId")
    fun observeByInstrument(instrumentId: Long): Flow<InstrumentPerformanceEntity?>

    @Query("SELECT * FROM instrument_performance ORDER BY netProfit DESC")
    fun observeAll(): Flow<List<InstrumentPerformanceEntity>>
}

@Dao
interface MonthlyPerformanceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(performance: MonthlyPerformanceEntity): Long

    @Update
    suspend fun update(performance: MonthlyPerformanceEntity)

    @Query("SELECT * FROM monthly_performance WHERE yearMonth = :yearMonth AND isDeleted = 0")
    suspend fun findByMonth(yearMonth: String): MonthlyPerformanceEntity?

    @Query("SELECT * FROM monthly_performance WHERE isDeleted = 0 ORDER BY yearMonth DESC")
    fun observeAll(): Flow<List<MonthlyPerformanceEntity>>

    @Query("SELECT * FROM monthly_performance WHERE yearMonth BETWEEN :startYearMonth AND :endYearMonth AND isDeleted = 0 ORDER BY yearMonth ASC")
    fun observeByRange(startYearMonth: String, endYearMonth: String): Flow<List<MonthlyPerformanceEntity>>
}

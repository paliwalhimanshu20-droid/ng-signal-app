package com.jarvis.tidb.historical.quality.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jarvis.tidb.historical.quality.entity.CandleQualityReportEntity
import com.jarvis.tidb.historical.quality.entity.CorporateActionEntity
import com.jarvis.tidb.historical.quality.entity.QualityIssueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CandleQualityReportDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(report: CandleQualityReportEntity): Long

    @Query("SELECT * FROM candle_quality_reports WHERE reportId = :reportId")
    suspend fun findById(reportId: Long): CandleQualityReportEntity?

    @Query(
        "SELECT * FROM candle_quality_reports WHERE instrumentId = :instrumentId AND timeframe = :timeframe ORDER BY generatedAt DESC LIMIT 1"
    )
    suspend fun getLatest(instrumentId: Long, timeframe: String): CandleQualityReportEntity?

    @Query("SELECT * FROM candle_quality_reports WHERE instrumentId = :instrumentId ORDER BY generatedAt DESC")
    fun observeByInstrument(instrumentId: Long): Flow<List<CandleQualityReportEntity>>

    @Query("SELECT * FROM candle_quality_reports WHERE qualityScore < :threshold ORDER BY qualityScore ASC")
    fun observeBelowThreshold(threshold: Double): Flow<List<CandleQualityReportEntity>>
}

@Dao
interface QualityIssueDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(issue: QualityIssueEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(issues: List<QualityIssueEntity>): List<Long>

    @Update
    suspend fun update(issue: QualityIssueEntity)

    @Query("SELECT * FROM quality_issues WHERE reportId = :reportId ORDER BY severity DESC")
    fun observeByReport(reportId: Long): Flow<List<QualityIssueEntity>>

    @Query("SELECT * FROM quality_issues WHERE resolved = 0 AND severity = 'CRITICAL' ORDER BY createdAt ASC")
    fun observeUnresolvedCritical(): Flow<List<QualityIssueEntity>>

    @Query("UPDATE quality_issues SET resolved = 1, resolvedAt = :now WHERE issueId = :issueId")
    suspend fun markResolved(issueId: Long, now: Long = System.currentTimeMillis())
}

@Dao
interface CorporateActionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(action: CorporateActionEntity): Long

    @Update
    suspend fun update(action: CorporateActionEntity)

    @Query("SELECT * FROM corporate_actions WHERE instrumentId = :instrumentId ORDER BY effectiveDate DESC")
    fun observeByInstrument(instrumentId: Long): Flow<List<CorporateActionEntity>>

    @Query("SELECT * FROM corporate_actions WHERE applied = 0 ORDER BY effectiveDate ASC")
    fun observeUnapplied(): Flow<List<CorporateActionEntity>>

    @Query("UPDATE corporate_actions SET applied = 1, appliedAt = :now WHERE actionId = :actionId")
    suspend fun markApplied(actionId: Long, now: Long = System.currentTimeMillis())
}

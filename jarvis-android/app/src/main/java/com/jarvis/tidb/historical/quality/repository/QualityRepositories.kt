package com.jarvis.tidb.historical.quality.repository

import com.jarvis.tidb.historical.quality.entity.CandleQualityReportEntity
import com.jarvis.tidb.historical.quality.entity.CorporateActionEntity
import com.jarvis.tidb.historical.quality.entity.QualityIssueEntity
import kotlinx.coroutines.flow.Flow

interface QualityReportRepository {
    /** Persists [report] plus every finding in [issues] (issueId/reportId wiring handled here) as one unit. */
    suspend fun publishReport(report: CandleQualityReportEntity, issues: List<QualityIssueEntity>): Long
    suspend fun getLatest(instrumentId: Long, timeframe: String): CandleQualityReportEntity?
    fun observeByInstrument(instrumentId: Long): Flow<List<CandleQualityReportEntity>>
    fun observeBelowThreshold(threshold: Double): Flow<List<CandleQualityReportEntity>>
    fun observeIssues(reportId: Long): Flow<List<QualityIssueEntity>>
    fun observeUnresolvedCritical(): Flow<List<QualityIssueEntity>>
    suspend fun resolveIssue(issueId: Long)
}

interface CorporateActionRepository {
    suspend fun record(action: CorporateActionEntity): Long
    fun observeByInstrument(instrumentId: Long): Flow<List<CorporateActionEntity>>
    fun observeUnapplied(): Flow<List<CorporateActionEntity>>
    suspend fun markApplied(actionId: Long)
}

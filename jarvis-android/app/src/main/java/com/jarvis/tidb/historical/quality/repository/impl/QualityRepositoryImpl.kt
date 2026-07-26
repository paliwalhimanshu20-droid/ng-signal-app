package com.jarvis.tidb.historical.quality.repository.impl

import com.jarvis.tidb.historical.quality.dao.CandleQualityReportDao
import com.jarvis.tidb.historical.quality.dao.CorporateActionDao
import com.jarvis.tidb.historical.quality.dao.QualityIssueDao
import com.jarvis.tidb.historical.quality.entity.CandleQualityReportEntity
import com.jarvis.tidb.historical.quality.entity.CorporateActionEntity
import com.jarvis.tidb.historical.quality.entity.QualityIssueEntity
import com.jarvis.tidb.historical.quality.repository.CorporateActionRepository
import com.jarvis.tidb.historical.quality.repository.QualityReportRepository
import kotlinx.coroutines.flow.Flow

class QualityReportRepositoryImpl(
    private val reportDao: CandleQualityReportDao,
    private val issueDao: QualityIssueDao
) : QualityReportRepository {

    override suspend fun publishReport(report: CandleQualityReportEntity, issues: List<QualityIssueEntity>): Long {
        val reportId = reportDao.insert(report)
        if (issues.isNotEmpty()) {
            issueDao.insertAll(issues.map { it.copy(reportId = reportId) })
        }
        return reportId
    }

    override suspend fun getLatest(instrumentId: Long, timeframe: String): CandleQualityReportEntity? =
        reportDao.getLatest(instrumentId, timeframe)

    override fun observeByInstrument(instrumentId: Long): Flow<List<CandleQualityReportEntity>> =
        reportDao.observeByInstrument(instrumentId)

    override fun observeBelowThreshold(threshold: Double): Flow<List<CandleQualityReportEntity>> =
        reportDao.observeBelowThreshold(threshold)

    override fun observeIssues(reportId: Long): Flow<List<QualityIssueEntity>> = issueDao.observeByReport(reportId)

    override fun observeUnresolvedCritical(): Flow<List<QualityIssueEntity>> = issueDao.observeUnresolvedCritical()

    override suspend fun resolveIssue(issueId: Long) = issueDao.markResolved(issueId)
}

class CorporateActionRepositoryImpl(private val dao: CorporateActionDao) : CorporateActionRepository {
    override suspend fun record(action: CorporateActionEntity): Long = dao.insert(action)
    override fun observeByInstrument(instrumentId: Long): Flow<List<CorporateActionEntity>> =
        dao.observeByInstrument(instrumentId)
    override fun observeUnapplied(): Flow<List<CorporateActionEntity>> = dao.observeUnapplied()
    override suspend fun markApplied(actionId: Long) = dao.markApplied(actionId)
}

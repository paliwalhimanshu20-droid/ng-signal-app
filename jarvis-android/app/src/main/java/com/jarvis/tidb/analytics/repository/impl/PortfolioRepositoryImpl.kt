package com.jarvis.tidb.analytics.repository.impl

import com.jarvis.tidb.analytics.dao.CapitalMovementDao
import com.jarvis.tidb.analytics.dao.PortfolioAllocationDao
import com.jarvis.tidb.analytics.dao.PortfolioDao
import com.jarvis.tidb.analytics.dao.PortfolioPositionDao
import com.jarvis.tidb.analytics.dao.PortfolioRiskDao
import com.jarvis.tidb.analytics.dao.PortfolioSnapshotDao
import com.jarvis.tidb.analytics.entity.CapitalMovementEntity
import com.jarvis.tidb.analytics.entity.PortfolioAllocationEntity
import com.jarvis.tidb.analytics.entity.PortfolioEntity
import com.jarvis.tidb.analytics.entity.PortfolioPositionEntity
import com.jarvis.tidb.analytics.entity.PortfolioRiskEntity
import com.jarvis.tidb.analytics.entity.PortfolioSnapshotEntity
import com.jarvis.tidb.analytics.entity.PortfolioSnapshotType
import com.jarvis.tidb.analytics.entity.PortfolioWithDetails
import com.jarvis.tidb.analytics.entity.PositionStatus
import com.jarvis.tidb.analytics.repository.PortfolioRepository
import kotlinx.coroutines.flow.Flow

class PortfolioRepositoryImpl(
    private val portfolioDao: PortfolioDao,
    private val positionDao: PortfolioPositionDao,
    private val allocationDao: PortfolioAllocationDao,
    private val riskDao: PortfolioRiskDao,
    private val capitalMovementDao: CapitalMovementDao,
    private val snapshotDao: PortfolioSnapshotDao
) : PortfolioRepository {

    override suspend fun createPortfolio(portfolio: PortfolioEntity): Long = portfolioDao.insert(portfolio)

    override suspend fun updatePortfolio(portfolio: PortfolioEntity) = portfolioDao.update(portfolio)

    override suspend fun getPortfolio(rowId: Long): PortfolioEntity? = portfolioDao.findByRowId(rowId)

    override fun observePortfolios(): Flow<List<PortfolioEntity>> = portfolioDao.observeAll()

    override fun observePortfolioWithDetails(rowId: Long): Flow<PortfolioWithDetails?> =
        portfolioDao.observeWithDetails(rowId)

    override suspend fun openPosition(position: PortfolioPositionEntity): Long = positionDao.insert(position)

    override suspend fun updatePosition(position: PortfolioPositionEntity) = positionDao.update(position)

    override fun observePositions(portfolioRowId: Long, status: PositionStatus): Flow<List<PortfolioPositionEntity>> =
        positionDao.observeByPortfolioAndStatus(portfolioRowId, status)

    override fun observePositionsByInstrument(instrumentId: Long): Flow<List<PortfolioPositionEntity>> =
        positionDao.observeByInstrument(instrumentId)

    override suspend fun recordAllocation(allocation: PortfolioAllocationEntity): Long =
        allocationDao.insert(allocation)

    override fun observeAllocations(portfolioRowId: Long): Flow<List<PortfolioAllocationEntity>> =
        allocationDao.observeByPortfolio(portfolioRowId)

    override suspend fun latestAllocation(portfolioRowId: Long, scopeKey: String): PortfolioAllocationEntity? =
        allocationDao.latestForScope(portfolioRowId, scopeKey)

    override suspend fun recordRiskSnapshot(risk: PortfolioRiskEntity): Long = riskDao.insert(risk)

    override fun observeRiskSnapshots(portfolioRowId: Long): Flow<List<PortfolioRiskEntity>> =
        riskDao.observeByPortfolio(portfolioRowId)

    override suspend fun latestRiskSnapshot(portfolioRowId: Long): PortfolioRiskEntity? =
        riskDao.latestForPortfolio(portfolioRowId)

    override suspend fun recordCapitalMovement(movement: CapitalMovementEntity): Long =
        capitalMovementDao.insert(movement)

    override fun observeCapitalMovements(portfolioRowId: Long): Flow<List<CapitalMovementEntity>> =
        capitalMovementDao.observeByPortfolio(portfolioRowId)

    override fun observeCapitalMovementsByRange(
        portfolioRowId: Long,
        startMillis: Long,
        endMillis: Long
    ): Flow<List<CapitalMovementEntity>> = capitalMovementDao.observeByPortfolioAndRange(portfolioRowId, startMillis, endMillis)

    override suspend fun captureSnapshot(snapshot: PortfolioSnapshotEntity): Long = snapshotDao.insert(snapshot)

    override fun observeSnapshots(portfolioRowId: Long): Flow<List<PortfolioSnapshotEntity>> =
        snapshotDao.observeByPortfolio(portfolioRowId)

    override fun observeSnapshotsByType(portfolioRowId: Long, type: PortfolioSnapshotType): Flow<List<PortfolioSnapshotEntity>> =
        snapshotDao.observeByPortfolioAndType(portfolioRowId, type)

    override fun observeSnapshotsByRange(portfolioRowId: Long, startMillis: Long, endMillis: Long): Flow<List<PortfolioSnapshotEntity>> =
        snapshotDao.observeByPortfolioAndRange(portfolioRowId, startMillis, endMillis)

    override suspend fun latestSnapshot(portfolioRowId: Long): PortfolioSnapshotEntity? =
        snapshotDao.latestForPortfolio(portfolioRowId)
}

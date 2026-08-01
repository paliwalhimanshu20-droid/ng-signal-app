package com.jarvis.tidb.analytics.repository

import com.jarvis.tidb.analytics.entity.CapitalMovementEntity
import com.jarvis.tidb.analytics.entity.PortfolioAllocationEntity
import com.jarvis.tidb.analytics.entity.PortfolioEntity
import com.jarvis.tidb.analytics.entity.PortfolioPositionEntity
import com.jarvis.tidb.analytics.entity.PortfolioRiskEntity
import com.jarvis.tidb.analytics.entity.PortfolioSnapshotEntity
import com.jarvis.tidb.analytics.entity.PortfolioSnapshotType
import com.jarvis.tidb.analytics.entity.PortfolioWithDetails
import com.jarvis.tidb.analytics.entity.PositionStatus
import kotlinx.coroutines.flow.Flow

interface PortfolioRepository {

    suspend fun createPortfolio(portfolio: PortfolioEntity): Long

    suspend fun updatePortfolio(portfolio: PortfolioEntity)

    suspend fun getPortfolio(rowId: Long): PortfolioEntity?

    fun observePortfolios(): Flow<List<PortfolioEntity>>

    fun observePortfolioWithDetails(rowId: Long): Flow<PortfolioWithDetails?>

    suspend fun openPosition(position: PortfolioPositionEntity): Long

    suspend fun updatePosition(position: PortfolioPositionEntity)

    fun observePositions(portfolioRowId: Long, status: PositionStatus): Flow<List<PortfolioPositionEntity>>

    fun observePositionsByInstrument(instrumentId: Long): Flow<List<PortfolioPositionEntity>>

    suspend fun recordAllocation(allocation: PortfolioAllocationEntity): Long

    fun observeAllocations(portfolioRowId: Long): Flow<List<PortfolioAllocationEntity>>

    suspend fun latestAllocation(portfolioRowId: Long, scopeKey: String): PortfolioAllocationEntity?

    suspend fun recordRiskSnapshot(risk: PortfolioRiskEntity): Long

    fun observeRiskSnapshots(portfolioRowId: Long): Flow<List<PortfolioRiskEntity>>

    suspend fun latestRiskSnapshot(portfolioRowId: Long): PortfolioRiskEntity?

    suspend fun recordCapitalMovement(movement: CapitalMovementEntity): Long

    fun observeCapitalMovements(portfolioRowId: Long): Flow<List<CapitalMovementEntity>>

    fun observeCapitalMovementsByRange(portfolioRowId: Long, startMillis: Long, endMillis: Long): Flow<List<CapitalMovementEntity>>

    /** v1.0 consolidation item 4 — immutable, point-in-time portfolio state capture for daily/monthly reports, AI comparisons, and portfolio-evolution charts. */
    suspend fun captureSnapshot(snapshot: PortfolioSnapshotEntity): Long

    fun observeSnapshots(portfolioRowId: Long): Flow<List<PortfolioSnapshotEntity>>

    fun observeSnapshotsByType(portfolioRowId: Long, type: PortfolioSnapshotType): Flow<List<PortfolioSnapshotEntity>>

    fun observeSnapshotsByRange(portfolioRowId: Long, startMillis: Long, endMillis: Long): Flow<List<PortfolioSnapshotEntity>>

    suspend fun latestSnapshot(portfolioRowId: Long): PortfolioSnapshotEntity?
}

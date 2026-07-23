package com.jarvis.tidb.analytics.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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

@Dao
interface PortfolioDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(portfolio: PortfolioEntity): Long

    @Update
    suspend fun update(portfolio: PortfolioEntity)

    @Query("SELECT * FROM portfolios WHERE rowId = :rowId")
    suspend fun findByRowId(rowId: Long): PortfolioEntity?

    @Query("SELECT * FROM portfolios WHERE isDeleted = 0 ORDER BY name ASC")
    fun observeAll(): Flow<List<PortfolioEntity>>

    @Transaction
    @Query("SELECT * FROM portfolios WHERE rowId = :rowId")
    fun observeWithDetails(rowId: Long): Flow<PortfolioWithDetails?>
}

@Dao
interface PortfolioPositionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(position: PortfolioPositionEntity): Long

    @Update
    suspend fun update(position: PortfolioPositionEntity)

    @Query("SELECT * FROM portfolio_positions WHERE portfolioRowId = :portfolioRowId AND status = :status")
    fun observeByPortfolioAndStatus(portfolioRowId: Long, status: PositionStatus): Flow<List<PortfolioPositionEntity>>

    @Query("SELECT * FROM portfolio_positions WHERE instrumentId = :instrumentId ORDER BY openedAt DESC")
    fun observeByInstrument(instrumentId: Long): Flow<List<PortfolioPositionEntity>>
}

@Dao
interface PortfolioAllocationDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(allocation: PortfolioAllocationEntity): Long

    @Query("SELECT * FROM portfolio_allocations WHERE portfolioRowId = :portfolioRowId ORDER BY asOf DESC")
    fun observeByPortfolio(portfolioRowId: Long): Flow<List<PortfolioAllocationEntity>>

    @Query("SELECT * FROM portfolio_allocations WHERE portfolioRowId = :portfolioRowId AND scopeKey = :scopeKey ORDER BY asOf DESC LIMIT 1")
    suspend fun latestForScope(portfolioRowId: Long, scopeKey: String): PortfolioAllocationEntity?
}

@Dao
interface PortfolioRiskDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(risk: PortfolioRiskEntity): Long

    @Query("SELECT * FROM portfolio_risk WHERE portfolioRowId = :portfolioRowId ORDER BY computedAt DESC")
    fun observeByPortfolio(portfolioRowId: Long): Flow<List<PortfolioRiskEntity>>

    @Query("SELECT * FROM portfolio_risk WHERE portfolioRowId = :portfolioRowId ORDER BY computedAt DESC LIMIT 1")
    suspend fun latestForPortfolio(portfolioRowId: Long): PortfolioRiskEntity?
}

@Dao
interface CapitalMovementDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(movement: CapitalMovementEntity): Long

    @Query("SELECT * FROM capital_movements WHERE portfolioRowId = :portfolioRowId ORDER BY occurredAt DESC")
    fun observeByPortfolio(portfolioRowId: Long): Flow<List<CapitalMovementEntity>>

    @Query("SELECT * FROM capital_movements WHERE portfolioRowId = :portfolioRowId AND occurredAt BETWEEN :startMillis AND :endMillis ORDER BY occurredAt DESC")
    fun observeByPortfolioAndRange(portfolioRowId: Long, startMillis: Long, endMillis: Long): Flow<List<CapitalMovementEntity>>
}

@Dao
interface PortfolioSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(snapshot: PortfolioSnapshotEntity): Long

    @Query("SELECT * FROM portfolio_snapshots WHERE portfolioRowId = :portfolioRowId ORDER BY snapshotAt DESC")
    fun observeByPortfolio(portfolioRowId: Long): Flow<List<PortfolioSnapshotEntity>>

    @Query("SELECT * FROM portfolio_snapshots WHERE portfolioRowId = :portfolioRowId AND snapshotType = :type ORDER BY snapshotAt DESC")
    fun observeByPortfolioAndType(portfolioRowId: Long, type: PortfolioSnapshotType): Flow<List<PortfolioSnapshotEntity>>

    @Query("SELECT * FROM portfolio_snapshots WHERE portfolioRowId = :portfolioRowId AND snapshotAt BETWEEN :startMillis AND :endMillis ORDER BY snapshotAt ASC")
    fun observeByPortfolioAndRange(portfolioRowId: Long, startMillis: Long, endMillis: Long): Flow<List<PortfolioSnapshotEntity>>

    @Query("SELECT * FROM portfolio_snapshots WHERE portfolioRowId = :portfolioRowId ORDER BY snapshotAt DESC LIMIT 1")
    suspend fun latestForPortfolio(portfolioRowId: Long): PortfolioSnapshotEntity?
}

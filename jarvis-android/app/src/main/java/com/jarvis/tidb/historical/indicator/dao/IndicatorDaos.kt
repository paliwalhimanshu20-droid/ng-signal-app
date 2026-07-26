package com.jarvis.tidb.historical.indicator.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jarvis.tidb.historical.indicator.entity.IndicatorComputationRunEntity
import com.jarvis.tidb.historical.indicator.entity.IndicatorDefinitionEntity
import com.jarvis.tidb.historical.indicator.entity.IndicatorValueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IndicatorDefinitionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(definition: IndicatorDefinitionEntity): Long

    @Update
    suspend fun update(definition: IndicatorDefinitionEntity)

    @Query("SELECT * FROM indicator_definitions WHERE indicatorDefId = :id")
    suspend fun findById(id: Long): IndicatorDefinitionEntity?

    @Query("SELECT * FROM indicator_definitions WHERE name = :name ORDER BY definitionVersion DESC LIMIT 1")
    suspend fun findLatestByName(name: String): IndicatorDefinitionEntity?

    @Query("SELECT * FROM indicator_definitions WHERE indicatorType = :type AND isActive = 1")
    fun observeByType(type: String): Flow<List<IndicatorDefinitionEntity>>

    @Query("SELECT * FROM indicator_definitions WHERE isActive = 1 ORDER BY name ASC")
    fun observeActive(): Flow<List<IndicatorDefinitionEntity>>
}

@Dao
interface IndicatorValueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(value: IndicatorValueEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(values: List<IndicatorValueEntity>): List<Long>

    @Query(
        """
        SELECT * FROM indicator_values
        WHERE indicatorDefId = :indicatorDefId AND instrumentId = :instrumentId AND timeframe = :timeframe
          AND timestamp BETWEEN :fromTs AND :toTs
        ORDER BY timestamp ASC
        """
    )
    fun observeRange(
        indicatorDefId: Long,
        instrumentId: Long,
        timeframe: String,
        fromTs: Long,
        toTs: Long
    ): Flow<List<IndicatorValueEntity>>

    @Query(
        """
        SELECT * FROM indicator_values
        WHERE indicatorDefId = :indicatorDefId AND instrumentId = :instrumentId AND timeframe = :timeframe
        ORDER BY timestamp DESC LIMIT :limit
        """
    )
    suspend fun getLatest(indicatorDefId: Long, instrumentId: Long, timeframe: String, limit: Int = 1): List<IndicatorValueEntity>

    @Query(
        """
        SELECT COUNT(*) FROM indicator_values
        WHERE indicatorDefId = :indicatorDefId AND instrumentId = :instrumentId AND timeframe = :timeframe
          AND timestamp BETWEEN :fromTs AND :toTs
        """
    )
    suspend fun countInRange(indicatorDefId: Long, instrumentId: Long, timeframe: String, fromTs: Long, toTs: Long): Int

    @Query(
        "DELETE FROM indicator_values WHERE indicatorDefId = :indicatorDefId AND instrumentId = :instrumentId AND timeframe = :timeframe AND version = :version"
    )
    suspend fun deleteVersion(indicatorDefId: Long, instrumentId: Long, timeframe: String, version: Int)
}

@Dao
interface IndicatorComputationRunDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(run: IndicatorComputationRunEntity): Long

    @Update
    suspend fun update(run: IndicatorComputationRunEntity)

    @Query("SELECT * FROM indicator_computation_runs WHERE runId = :runId")
    suspend fun findById(runId: Long): IndicatorComputationRunEntity?

    @Query("SELECT * FROM indicator_computation_runs WHERE status IN ('PENDING', 'RUNNING') ORDER BY createdAt ASC")
    fun observeActive(): Flow<List<IndicatorComputationRunEntity>>

    @Query("SELECT * FROM indicator_computation_runs WHERE indicatorDefId = :indicatorDefId AND instrumentId = :instrumentId AND timeframe = :timeframe ORDER BY completedAt DESC LIMIT 1")
    suspend fun getLastRun(indicatorDefId: Long, instrumentId: Long, timeframe: String): IndicatorComputationRunEntity?
}

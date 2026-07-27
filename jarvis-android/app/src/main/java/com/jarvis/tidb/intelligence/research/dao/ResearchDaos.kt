package com.jarvis.tidb.intelligence.research.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jarvis.tidb.intelligence.research.entity.ExperimentEntity
import com.jarvis.tidb.intelligence.research.entity.ExperimentResultEntity
import com.jarvis.tidb.intelligence.research.entity.ExperimentRunEntity
import com.jarvis.tidb.intelligence.research.entity.HypothesisEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HypothesisDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(hypothesis: HypothesisEntity): Long

    @Update
    suspend fun update(hypothesis: HypothesisEntity)

    @Query("SELECT * FROM hypotheses WHERE hypothesisId = :hypothesisId")
    suspend fun findById(hypothesisId: Long): HypothesisEntity?

    @Query("SELECT * FROM hypotheses WHERE status = :status ORDER BY proposedAt DESC")
    fun observeByStatus(status: String): Flow<List<HypothesisEntity>>

    @Query("SELECT * FROM hypotheses ORDER BY proposedAt DESC")
    fun observeAll(): Flow<List<HypothesisEntity>>
}

@Dao
interface ExperimentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(experiment: ExperimentEntity): Long

    @Update
    suspend fun update(experiment: ExperimentEntity)

    @Query("SELECT * FROM experiments WHERE experimentId = :experimentId")
    suspend fun findById(experimentId: Long): ExperimentEntity?

    @Query("SELECT * FROM experiments WHERE hypothesisId = :hypothesisId ORDER BY createdAt DESC")
    fun observeForHypothesis(hypothesisId: Long): Flow<List<ExperimentEntity>>

    @Query("SELECT * FROM experiments WHERE status = :status ORDER BY createdAt DESC")
    fun observeByStatus(status: String): Flow<List<ExperimentEntity>>
}

@Dao
interface ExperimentRunDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(run: ExperimentRunEntity): Long

    @Update
    suspend fun update(run: ExperimentRunEntity)

    @Query("SELECT * FROM experiment_runs WHERE runId = :runId")
    suspend fun findById(runId: Long): ExperimentRunEntity?

    @Query("SELECT * FROM experiment_runs WHERE experimentId = :experimentId ORDER BY createdAt DESC")
    fun observeForExperiment(experimentId: Long): Flow<List<ExperimentRunEntity>>
}

@Dao
interface ExperimentResultDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(result: ExperimentResultEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(results: List<ExperimentResultEntity>): List<Long>

    @Query("SELECT * FROM experiment_results WHERE runId = :runId ORDER BY recordedAt ASC")
    fun observeForRun(runId: Long): Flow<List<ExperimentResultEntity>>

    @Query("SELECT * FROM experiment_results WHERE producedInsightRowId = :insightRowId")
    fun observeByProducedInsight(insightRowId: Long): Flow<List<ExperimentResultEntity>>
}

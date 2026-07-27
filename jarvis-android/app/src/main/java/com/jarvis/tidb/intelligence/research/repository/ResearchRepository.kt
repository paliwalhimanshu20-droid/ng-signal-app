package com.jarvis.tidb.intelligence.research.repository

import com.jarvis.tidb.intelligence.research.entity.ExperimentEntity
import com.jarvis.tidb.intelligence.research.entity.ExperimentResultEntity
import com.jarvis.tidb.intelligence.research.entity.ExperimentRunEntity
import com.jarvis.tidb.intelligence.research.entity.HypothesisEntity
import com.jarvis.tidb.intelligence.research.entity.HypothesisStatus
import com.jarvis.tidb.intelligence.research.entity.ExperimentStatus
import kotlinx.coroutines.flow.Flow

/** Single facade over the Hypothesis -> Experiment -> ExperimentRun -> ExperimentResult research chain. */
interface ResearchRepository {

    suspend fun proposeHypothesis(hypothesis: HypothesisEntity): Long
    suspend fun updateHypothesisStatus(hypothesisId: Long, status: HypothesisStatus)
    suspend fun getHypothesis(hypothesisId: Long): HypothesisEntity?
    fun observeHypothesesByStatus(status: HypothesisStatus): Flow<List<HypothesisEntity>>
    fun observeAllHypotheses(): Flow<List<HypothesisEntity>>

    suspend fun designExperiment(experiment: ExperimentEntity): Long
    suspend fun updateExperimentStatus(experimentId: Long, status: ExperimentStatus)
    fun observeExperimentsForHypothesis(hypothesisId: Long): Flow<List<ExperimentEntity>>

    suspend fun startRun(run: ExperimentRunEntity): Long
    suspend fun completeRun(runId: Long, completedAt: Long, succeeded: Boolean)
    fun observeRunsForExperiment(experimentId: Long): Flow<List<ExperimentRunEntity>>

    suspend fun recordResult(result: ExperimentResultEntity): Long
    suspend fun recordResults(results: List<ExperimentResultEntity>): List<Long>
    fun observeResultsForRun(runId: Long): Flow<List<ExperimentResultEntity>>
}

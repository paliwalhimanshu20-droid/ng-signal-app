package com.jarvis.tidb.intelligence.research.repository.impl

import com.jarvis.tidb.intelligence.research.dao.ExperimentDao
import com.jarvis.tidb.intelligence.research.dao.ExperimentResultDao
import com.jarvis.tidb.intelligence.research.dao.ExperimentRunDao
import com.jarvis.tidb.intelligence.research.dao.HypothesisDao
import com.jarvis.tidb.intelligence.research.entity.ExperimentEntity
import com.jarvis.tidb.intelligence.research.entity.ExperimentResultEntity
import com.jarvis.tidb.intelligence.research.entity.ExperimentRunEntity
import com.jarvis.tidb.intelligence.research.entity.ExperimentRunStatus
import com.jarvis.tidb.intelligence.research.entity.ExperimentStatus
import com.jarvis.tidb.intelligence.research.entity.HypothesisEntity
import com.jarvis.tidb.intelligence.research.entity.HypothesisStatus
import com.jarvis.tidb.intelligence.research.repository.ResearchRepository
import kotlinx.coroutines.flow.Flow

class ResearchRepositoryImpl(
    private val hypothesisDao: HypothesisDao,
    private val experimentDao: ExperimentDao,
    private val runDao: ExperimentRunDao,
    private val resultDao: ExperimentResultDao
) : ResearchRepository {

    override suspend fun proposeHypothesis(hypothesis: HypothesisEntity): Long = hypothesisDao.insert(hypothesis)

    override suspend fun updateHypothesisStatus(hypothesisId: Long, status: HypothesisStatus) {
        val existing = hypothesisDao.findById(hypothesisId) ?: return
        hypothesisDao.update(existing.copy(status = status, audit = existing.audit.touched()))
    }

    override suspend fun getHypothesis(hypothesisId: Long): HypothesisEntity? = hypothesisDao.findById(hypothesisId)

    override fun observeHypothesesByStatus(status: HypothesisStatus): Flow<List<HypothesisEntity>> =
        hypothesisDao.observeByStatus(status.value)

    override fun observeAllHypotheses(): Flow<List<HypothesisEntity>> = hypothesisDao.observeAll()

    override suspend fun designExperiment(experiment: ExperimentEntity): Long = experimentDao.insert(experiment)

    override suspend fun updateExperimentStatus(experimentId: Long, status: ExperimentStatus) {
        val existing = experimentDao.findById(experimentId) ?: return
        experimentDao.update(existing.copy(status = status, audit = existing.audit.touched()))
    }

    override fun observeExperimentsForHypothesis(hypothesisId: Long): Flow<List<ExperimentEntity>> =
        experimentDao.observeForHypothesis(hypothesisId)

    override suspend fun startRun(run: ExperimentRunEntity): Long =
        runDao.insert(run.copy(status = ExperimentRunStatus.RUNNING, startedAt = run.startedAt ?: System.currentTimeMillis()))

    override suspend fun completeRun(runId: Long, completedAt: Long, succeeded: Boolean) {
        val existing = runDao.findById(runId) ?: return
        runDao.update(
            existing.copy(
                status = if (succeeded) ExperimentRunStatus.SUCCEEDED else ExperimentRunStatus.FAILED,
                completedAt = completedAt,
                audit = existing.audit.touched()
            )
        )
    }

    override fun observeRunsForExperiment(experimentId: Long): Flow<List<ExperimentRunEntity>> =
        runDao.observeForExperiment(experimentId)

    override suspend fun recordResult(result: ExperimentResultEntity): Long = resultDao.insert(result)

    override suspend fun recordResults(results: List<ExperimentResultEntity>): List<Long> = resultDao.insertAll(results)

    override fun observeResultsForRun(runId: Long): Flow<List<ExperimentResultEntity>> = resultDao.observeForRun(runId)
}

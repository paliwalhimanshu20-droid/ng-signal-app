package com.jarvis.tidb.optimization.repository

import com.jarvis.tidb.optimization.dao.OptimizationCombinationDao
import com.jarvis.tidb.optimization.dao.OptimizationJobDao
import com.jarvis.tidb.optimization.entity.OptimizationCombinationEntity
import com.jarvis.tidb.optimization.entity.OptimizationCombinationStatus
import com.jarvis.tidb.optimization.entity.OptimizationJobEntity
import com.jarvis.tidb.optimization.entity.OptimizationJobStatus
import com.jarvis.tidb.optimization.searchspace.SearchSpaceRegistry
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Phase 3B, Section 1 -- Optimization Persistence." The orchestration layer between Phase 3's
 * pure, in-memory [com.jarvis.tidb.optimization.algorithm.OptimizationAlgorithm] /
 * [SearchSpaceRegistry] and real, durable storage. [createJob] is the one place those two
 * connect: it looks up the real [com.jarvis.tidb.optimization.searchspace.SearchSpace] for a
 * component, runs the real algorithm to generate the real combination list, and persists every
 * one of them as its own [OptimizationCombinationEntity] row before returning -- "persist every
 * parameter combination" means the full plan exists in the database before anything is evaluated
 * against it, not that combinations get written as a side effect of evaluation later.
 *
 * Evaluating a combination (actually running a backtest for it) is intentionally NOT this
 * interface's job -- that needs Module 5's backtest simulator, which does not exist yet (see the
 * Phase 3 delivery notes). [markCombinationEvaluated] is this repository's side of that future
 * handoff: whatever runs a backtest for a combination calls this with the resulting
 * `backtestRunRowId`/`backtestResultRowId`, and this repository's job is only to record that
 * honestly and keep progress/checkpoint state correct -- not to fabricate what evaluation would
 * have produced.
 */
interface OptimizationRepository {

    /** Generates and persists a job's full combination plan via the real Phase 3 algorithm + search space. Throws [IllegalStateException] if [componentId] has no registered search space, or whatever [OptimizationAlgorithm.generateCombinations] itself throws (e.g. grid search exceeding [budget]) -- this method does not swallow either. */
    suspend fun createJob(
        componentId: String,
        algorithmId: String,
        instrumentId: Long,
        timeframeValue: String,
        periodStart: Long,
        periodEnd: Long,
        budget: Int,
        randomSeed: Long? = null,
    ): OptimizationJobEntity

    suspend fun getJob(jobRowId: Long): OptimizationJobEntity?
    fun observeJob(jobRowId: Long): Flow<OptimizationJobEntity?>
    fun observeAllJobs(): Flow<List<OptimizationJobEntity>>

    /** "Support interruption recovery": jobs an app restart could have interrupted mid-flight -- see [OptimizationJobDao.findResumable]'s own docstring. */
    suspend fun findResumableJobs(): List<OptimizationJobEntity>

    /** The real resume mechanism: combinations still PENDING for this job, in deterministic order. A resumed job is "keep evaluating from the start of this list" -- nothing about resuming re-derives the plan, it's the same persisted rows [createJob] wrote. */
    suspend fun pendingCombinations(jobRowId: Long): List<OptimizationCombinationEntity>

    suspend fun markJobRunning(jobRowId: Long)
    suspend fun markJobCancelled(jobRowId: Long)
    suspend fun markJobFailed(jobRowId: Long, errorMessage: String)

    /** Marks one combination as actually evaluated (see this interface's own class docstring), and rolls the job's completedCombinations/checkpointCombinationIndex forward -- also marks the job COMPLETED automatically once every combination is done. */
    suspend fun markCombinationEvaluated(combinationRowId: Long, backtestRunRowId: Long?, backtestResultRowId: Long?)

    suspend fun markCombinationFailed(combinationRowId: Long, errorMessage: String)

    /** "Phase 3C, Section 1 -- Evidence Validation Engine": the real evidence a ranked job has to offer today -- completed, ranked combinations, best-first. Empty until a job has both run [OptimizationRepository.rankCombinations] AND actually been evaluated; never fabricated when empty. */
    suspend fun rankedCombinations(jobRowId: Long): List<OptimizationCombinationEntity>

    /** "Persist rankings": [rankedRowIdsBestFirst] is the caller's own ranking decision (e.g. by Sharpe ratio, once real results exist) -- this repository only persists the ordering it's given, rank 1 = best, matching [OptimizationCombinationEntity.rank]'s own doc. */
    suspend fun rankCombinations(jobRowId: Long, rankedRowIdsBestFirst: List<Long>)
}

@Singleton
class OptimizationRepositoryImpl @Inject constructor(
    private val jobDao: OptimizationJobDao,
    private val combinationDao: OptimizationCombinationDao,
    private val searchSpaceRegistry: SearchSpaceRegistry,
    algorithms: Set<@JvmSuppressWildcards com.jarvis.tidb.optimization.algorithm.OptimizationAlgorithm>,
) : OptimizationRepository {

    /** Same `associateBy` shape as [SearchSpaceRegistry] -- a `Set<OptimizationAlgorithm>` is what Hilt's `@Binds @IntoSet` multibinding actually produces; there is no direct `Map<String, OptimizationAlgorithm>` injection point, so this class builds its own lookup the same way SearchSpaceRegistry builds `byComponent`. */
    private val algorithmsById: Map<String, com.jarvis.tidb.optimization.algorithm.OptimizationAlgorithm> = algorithms.associateBy { it.algorithmId }

    override suspend fun createJob(
        componentId: String,
        algorithmId: String,
        instrumentId: Long,
        timeframeValue: String,
        periodStart: Long,
        periodEnd: Long,
        budget: Int,
        randomSeed: Long?,
    ): OptimizationJobEntity {
        val searchSpace = searchSpaceRegistry.forComponent(componentId)
            ?: throw IllegalStateException("No SearchSpaceProvider registered for '$componentId'.")
        val algorithm = algorithmsById[algorithmId]
            ?: throw IllegalStateException("No OptimizationAlgorithm registered for '$algorithmId'.")

        val combinations = algorithm.generateCombinations(searchSpace, budget, randomSeed)

        val job = OptimizationJobEntity(
            componentId = componentId,
            algorithmId = algorithmId,
            instrumentId = instrumentId,
            timeframeValue = timeframeValue,
            periodStart = periodStart,
            periodEnd = periodEnd,
            budget = budget,
            randomSeed = randomSeed,
            statusValue = OptimizationJobStatus.QUEUED.name,
            totalCombinations = combinations.size,
        )
        val jobRowId = jobDao.insert(job)

        val combinationEntities = combinations.mapIndexed { index, params ->
            OptimizationCombinationEntity(
                jobRowId = jobRowId,
                combinationIndex = index,
                parametersJson = toJson(params),
                statusValue = OptimizationCombinationStatus.PENDING.name,
            )
        }
        combinationDao.insertAll(combinationEntities)

        return job.copy(rowId = jobRowId)
    }

    override suspend fun getJob(jobRowId: Long): OptimizationJobEntity? = jobDao.findByRowId(jobRowId)

    override fun observeJob(jobRowId: Long): Flow<OptimizationJobEntity?> = jobDao.observeByRowId(jobRowId)

    override fun observeAllJobs(): Flow<List<OptimizationJobEntity>> = jobDao.observeAll()

    override suspend fun findResumableJobs(): List<OptimizationJobEntity> = jobDao.findResumable()

    override suspend fun pendingCombinations(jobRowId: Long): List<OptimizationCombinationEntity> =
        combinationDao.findPendingByJob(jobRowId)

    override suspend fun markJobRunning(jobRowId: Long) {
        jobDao.updateStatus(jobRowId, OptimizationJobStatus.RUNNING.name)
    }

    override suspend fun markJobCancelled(jobRowId: Long) {
        jobDao.updateStatus(jobRowId, OptimizationJobStatus.CANCELLED.name)
    }

    override suspend fun markJobFailed(jobRowId: Long, errorMessage: String) {
        jobDao.updateStatus(jobRowId, OptimizationJobStatus.FAILED.name)
        val job = jobDao.findByRowId(jobRowId) ?: return
        jobDao.update(job.copy(errorMessage = errorMessage))
    }

    override suspend fun markCombinationEvaluated(combinationRowId: Long, backtestRunRowId: Long?, backtestResultRowId: Long?) {
        combinationDao.markEvaluated(combinationRowId, OptimizationCombinationStatus.COMPLETED.name, backtestRunRowId, backtestResultRowId)
        advanceJobProgress(combinationRowId)
    }

    override suspend fun markCombinationFailed(combinationRowId: Long, errorMessage: String) {
        combinationDao.markFailed(combinationRowId, OptimizationCombinationStatus.FAILED.name, errorMessage)
        advanceJobProgress(combinationRowId)
    }

    override suspend fun rankedCombinations(jobRowId: Long): List<OptimizationCombinationEntity> =
        combinationDao.findCompletedRanked(jobRowId)

    override suspend fun rankCombinations(jobRowId: Long, rankedRowIdsBestFirst: List<Long>) {
        rankedRowIdsBestFirst.forEachIndexed { index, rowId ->
            combinationDao.setRank(rowId, index + 1)
        }
    }

    /** Shared by markCombinationEvaluated/markCombinationFailed: rolls the parent job's completedCombinations/checkpointCombinationIndex forward, and marks the job COMPLETED once every combination is out of PENDING/RUNNING. */
    private suspend fun advanceJobProgress(combinationRowId: Long) {
        val combination = combinationDao.findByRowId(combinationRowId) ?: return
        val jobRowId = combination.jobRowId
        val completed = combinationDao.countCompleted(jobRowId)
        jobDao.updateProgress(jobRowId, completedCombinations = completed, checkpointCombinationIndex = combination.combinationIndex)

        val job = jobDao.findByRowId(jobRowId) ?: return
        val remainingPending = combinationDao.findPendingByJob(jobRowId)
        if (remainingPending.isEmpty() && job.statusValue != OptimizationJobStatus.CANCELLED.name) {
            jobDao.updateStatus(jobRowId, OptimizationJobStatus.COMPLETED.name)
        }
    }

    private fun toJson(params: Map<String, Double>): String {
        val json = JSONObject()
        params.forEach { (key, value) -> json.put(key, value) }
        return json.toString()
    }
}

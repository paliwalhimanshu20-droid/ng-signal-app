package com.jarvis.tidb.optimization.repository

import com.jarvis.tidb.optimization.algorithm.GridSearchAlgorithm
import com.jarvis.tidb.optimization.algorithm.OptimizationAlgorithm
import com.jarvis.tidb.optimization.dao.OptimizationCombinationDao
import com.jarvis.tidb.optimization.dao.OptimizationJobDao
import com.jarvis.tidb.optimization.entity.OptimizationCombinationEntity
import com.jarvis.tidb.optimization.entity.OptimizationCombinationStatus
import com.jarvis.tidb.optimization.entity.OptimizationJobEntity
import com.jarvis.tidb.optimization.entity.OptimizationJobStatus
import com.jarvis.tidb.optimization.searchspace.EmaSearchSpaceProvider
import com.jarvis.tidb.optimization.searchspace.SearchSpaceRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

/**
 * "Phase 3B, Section 1 -- Optimization Persistence." Real Room/SQLite testing isn't available in
 * this project's JVM unit test setup (no Robolectric configured, CI only runs `./gradlew test`,
 * not an instrumented/device test) -- consistent with every other repository test in this
 * codebase, this exercises [OptimizationRepositoryImpl]'s actual orchestration logic (the
 * hand-written, bug-prone part) against in-memory fakes of the DAO interfaces, not the DAOs'
 * generated SQL itself.
 */
class OptimizationRepositoryTest {

    /** Minimal in-memory fake -- enough to prove the repository's own logic, not a DAO implementation to trust for SQL correctness. */
    private class FakeJobDao : OptimizationJobDao {
        val rowIdSeq = AtomicLong(1)
        val store = mutableMapOf<Long, OptimizationJobEntity>()

        override suspend fun insert(job: OptimizationJobEntity): Long {
            val rowId = rowIdSeq.getAndIncrement()
            store[rowId] = job.copy(rowId = rowId)
            return rowId
        }

        override suspend fun update(job: OptimizationJobEntity) { store[job.rowId] = job }
        override suspend fun findByRowId(rowId: Long): OptimizationJobEntity? = store[rowId]
        override fun observeByRowId(rowId: Long): Flow<OptimizationJobEntity?> = flowOf(store[rowId])
        override fun observeAll(): Flow<List<OptimizationJobEntity>> = flowOf(store.values.toList())
        override fun observeByComponent(componentId: String): Flow<List<OptimizationJobEntity>> =
            flowOf(store.values.filter { it.componentId == componentId })
        override suspend fun findResumable(): List<OptimizationJobEntity> =
            store.values.filter { it.statusValue in setOf("QUEUED", "RUNNING") }
        override suspend fun updateStatus(rowId: Long, status: String, updatedAt: Long) {
            store[rowId] = store.getValue(rowId).copy(statusValue = status)
        }
        override suspend fun updateProgress(rowId: Long, completedCombinations: Int, checkpointCombinationIndex: Int, updatedAt: Long) {
            store[rowId] = store.getValue(rowId).copy(completedCombinations = completedCombinations, checkpointCombinationIndex = checkpointCombinationIndex)
        }
    }

    private class FakeCombinationDao : OptimizationCombinationDao {
        val rowIdSeq = AtomicLong(1)
        val store = mutableMapOf<Long, OptimizationCombinationEntity>()

        override suspend fun insert(combination: OptimizationCombinationEntity): Long {
            val rowId = rowIdSeq.getAndIncrement()
            store[rowId] = combination.copy(rowId = rowId)
            return rowId
        }
        override suspend fun insertAll(combinations: List<OptimizationCombinationEntity>): List<Long> =
            combinations.map { insert(it) }
        override suspend fun update(combination: OptimizationCombinationEntity) { store[combination.rowId] = combination }
        override suspend fun findByRowId(rowId: Long): OptimizationCombinationEntity? = store[rowId]
        override fun observeByJob(jobRowId: Long): Flow<List<OptimizationCombinationEntity>> =
            flowOf(store.values.filter { it.jobRowId == jobRowId }.sortedBy { it.combinationIndex })
        override suspend fun findPendingByJob(jobRowId: Long): List<OptimizationCombinationEntity> =
            store.values.filter { it.jobRowId == jobRowId && it.statusValue == "PENDING" }.sortedBy { it.combinationIndex }
        override suspend fun findCompletedRanked(jobRowId: Long): List<OptimizationCombinationEntity> =
            store.values.filter { it.jobRowId == jobRowId && it.statusValue == "COMPLETED" }.sortedBy { it.rank ?: Int.MAX_VALUE }
        override suspend fun findCompletedByJob(jobRowId: Long): List<OptimizationCombinationEntity> =
            store.values.filter { it.jobRowId == jobRowId && it.statusValue == "COMPLETED" }.sortedBy { it.combinationIndex }
        override suspend fun markEvaluated(rowId: Long, status: String, backtestRunRowId: Long?, backtestResultRowId: Long?, updatedAt: Long) {
            store[rowId] = store.getValue(rowId).copy(statusValue = status, backtestRunRowId = backtestRunRowId, backtestResultRowId = backtestResultRowId)
        }
        override suspend fun markFailed(rowId: Long, status: String, errorMessage: String?, updatedAt: Long) {
            store[rowId] = store.getValue(rowId).copy(statusValue = status, errorMessage = errorMessage)
        }
        override suspend fun setRank(rowId: Long, rank: Int?, updatedAt: Long) {
            store[rowId] = store.getValue(rowId).copy(rank = rank)
        }
        override suspend fun countCompleted(jobRowId: Long): Int =
            store.values.count { it.jobRowId == jobRowId && it.statusValue == "COMPLETED" }
    }

    private fun buildRepository(jobDao: FakeJobDao = FakeJobDao(), combinationDao: FakeCombinationDao = FakeCombinationDao()): OptimizationRepositoryImpl {
        val searchSpaceRegistry = SearchSpaceRegistry(setOf(EmaSearchSpaceProvider()))
        val algorithms: Set<OptimizationAlgorithm> = setOf(GridSearchAlgorithm())
        return OptimizationRepositoryImpl(jobDao, combinationDao, searchSpaceRegistry, algorithms)
    }

    @Test
    fun `createJob generates and persists the real combination list, not a placeholder count`() = runTest {
        val jobDao = FakeJobDao()
        val combinationDao = FakeCombinationDao()
        val repo = buildRepository(jobDao, combinationDao)

        // Narrow EMA's real 2..300 space isn't directly overridable here, so budget must cover it (299).
        val job = repo.createJob(
            componentId = "INDICATOR:EMA", algorithmId = "GRID_SEARCH",
            instrumentId = 1L, timeframeValue = "1d", periodStart = 0L, periodEnd = 100L, budget = 400,
        )

        assertEquals(299, job.totalCombinations)
        assertEquals(299, combinationDao.store.size)
        assertTrue(combinationDao.store.values.all { it.jobRowId == job.rowId })
        assertTrue(combinationDao.store.values.all { it.statusValue == OptimizationCombinationStatus.PENDING.name })
        // Every combinationIndex 0..298 present exactly once -- the deterministic ordering resume depends on.
        assertEquals((0..298).toList(), combinationDao.store.values.map { it.combinationIndex }.sorted())
    }

    @Test
    fun `createJob throws for an unregistered component rather than silently creating an empty job`() = runTest {
        val repo = buildRepository()
        try {
            repo.createJob("INDICATOR:DOES_NOT_EXIST", "GRID_SEARCH", 1L, "1d", 0L, 100L, 10)
            org.junit.Assert.fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("DOES_NOT_EXIST"))
        }
    }

    @Test
    fun `marking every combination evaluated automatically completes the job`() = runTest {
        val jobDao = FakeJobDao()
        val combinationDao = FakeCombinationDao()
        val repo = buildRepository(jobDao, combinationDao)
        val job = repo.createJob("INDICATOR:EMA", "GRID_SEARCH", 1L, "1d", 0L, 100L, budget = 400)
        val allRowIds = combinationDao.store.keys.toList()

        allRowIds.forEach { rowId -> repo.markCombinationEvaluated(rowId, backtestRunRowId = 999L, backtestResultRowId = 998L) }

        val finalJob = jobDao.findByRowId(job.rowId)!!
        assertEquals(OptimizationJobStatus.COMPLETED.name, finalJob.statusValue)
        assertEquals(299, finalJob.completedCombinations)
        assertTrue(repo.pendingCombinations(job.rowId).isEmpty())
    }

    @Test
    fun `interrupting after partial progress leaves the job resumable with the correct pending set`() = runTest {
        val jobDao = FakeJobDao()
        val combinationDao = FakeCombinationDao()
        val repo = buildRepository(jobDao, combinationDao)
        val job = repo.createJob("INDICATOR:EMA", "GRID_SEARCH", 1L, "1d", 0L, 100L, budget = 400)
        repo.markJobRunning(job.rowId)

        val firstTen = combinationDao.store.values.sortedBy { it.combinationIndex }.take(10).map { it.rowId }
        firstTen.forEach { rowId -> repo.markCombinationEvaluated(rowId, 1L, 1L) }

        // Simulate interruption: nothing marks the job COMPLETED or FAILED -- it's still RUNNING.
        val resumable = repo.findResumableJobs()
        assertEquals(1, resumable.size)
        assertEquals(job.rowId, resumable.first().rowId)

        val stillPending = repo.pendingCombinations(job.rowId)
        assertEquals(289, stillPending.size) // 299 - 10
        assertEquals(10, stillPending.first().combinationIndex) // resumes exactly where it left off
    }

    @Test
    fun `cancelling a job stops it from being reported resumable`() = runTest {
        val jobDao = FakeJobDao()
        val repo = buildRepository(jobDao)
        val job = repo.createJob("INDICATOR:EMA", "GRID_SEARCH", 1L, "1d", 0L, 100L, budget = 400)

        repo.markJobCancelled(job.rowId)

        assertTrue(repo.findResumableJobs().isEmpty())
        assertEquals(OptimizationJobStatus.CANCELLED.name, jobDao.findByRowId(job.rowId)!!.statusValue)
    }

    @Test
    fun `rankCombinations persists rank 1 as the best, in the exact order given`() = runTest {
        val combinationDao = FakeCombinationDao()
        val repo = buildRepository(combinationDao = combinationDao)
        val job = repo.createJob("INDICATOR:EMA", "GRID_SEARCH", 1L, "1d", 0L, 100L, budget = 400)
        val threeRowIds = combinationDao.store.keys.sorted().take(3)

        repo.rankCombinations(job.rowId, rankedRowIdsBestFirst = threeRowIds)

        assertEquals(1, combinationDao.store[threeRowIds[0]]!!.rank)
        assertEquals(2, combinationDao.store[threeRowIds[1]]!!.rank)
        assertEquals(3, combinationDao.store[threeRowIds[2]]!!.rank)
        assertNull(combinationDao.store.values.first { it.rowId !in threeRowIds }.rank)
    }
}

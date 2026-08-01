package com.jarvis.tidb.optimization.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jarvis.tidb.analytics.entity.BacktestEntity
import com.jarvis.tidb.analytics.entity.BacktestResultEntity
import com.jarvis.tidb.analytics.entity.BacktestRunEntity
import com.jarvis.tidb.core.common.AuditMetadata
import com.jarvis.tidb.core.common.GlobalId
import com.jarvis.tidb.core.common.SoftDeleteMetadata

/**
 * "Phase 3B, Section 1 -- Optimization Persistence." [OptimizationJobEntity] is one optimization
 * request (a componentId + algorithm + instrument/timeframe/date-range + budget). It generates
 * [OptimizationCombinationEntity] rows upfront, one per combination
 * [com.jarvis.tidb.optimization.algorithm.OptimizationAlgorithm.generateCombinations] produced --
 * every combination this job will ever test is a real row from the moment the job is created,
 * not something invented after the fact, which is what "persist every parameter combination"
 * actually requires: the full plan exists in the database before any of it runs.
 *
 * [OptimizationCombinationEntity.backtestRunRowId] / [OptimizationCombinationEntity.
 * backtestResultRowId] are the deliberate reuse point for [BacktestRunEntity] /
 * [BacktestResultEntity] -- per this phase's explicit "DO NOT duplicate existing schemas"
 * instruction, a combination's actual evaluation (once Module 5's backtest simulator exists to
 * produce one) IS a real [BacktestRunEntity]/[BacktestResultEntity] row; these two columns just
 * link back to it. Both stay null until that evaluation happens -- an honest, queryable
 * "not evaluated yet" state, not a placeholder value.
 */
enum class OptimizationJobStatus { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }

enum class OptimizationCombinationStatus { PENDING, RUNNING, COMPLETED, FAILED, SKIPPED }

@Entity(
    tableName = "optimization_jobs",
    foreignKeys = [
        ForeignKey(
            entity = BacktestEntity::class,
            parentColumns = ["rowId"],
            childColumns = ["backtestRowId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["componentId"]),
        Index(value = ["status"]),
        Index(value = ["instrumentId"]),
    ],
)
data class OptimizationJobEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    /** Matches [com.jarvis.tidb.optimization.searchspace.SearchSpaceProvider.componentId] -- e.g. "INDICATOR:EMA". */
    val componentId: String,

    /** Matches [com.jarvis.tidb.optimization.algorithm.OptimizationAlgorithm.algorithmId] -- e.g. "GRID_SEARCH", "RANDOM_SEARCH". */
    val algorithmId: String,

    val instrumentId: Long,

    /** Stored as the raw Timeframe string value, same convention as [com.jarvis.tidb.historical.ingestion.entity] -- see that field's own comment for why this module avoids importing Module 1's Timeframe type directly. */
    @ColumnInfo(name = "timeframe")
    val timeframeValue: String,

    val periodStart: Long,
    val periodEnd: Long,

    /** The budget this job was created with -- see [com.jarvis.tidb.optimization.algorithm.OptimizationAlgorithm]'s own docstring for what this means per algorithm. */
    val budget: Int,

    val randomSeed: Long? = null,

    @ColumnInfo(name = "status")
    val statusValue: String = OptimizationJobStatus.QUEUED.name,

    /** The real, generated combination count for this job -- equals the number of [OptimizationCombinationEntity] rows created for it. */
    val totalCombinations: Int,

    val completedCombinations: Int = 0,

    /** "Support interruption recovery": the last combinationIndex known to be fully processed. -1 means nothing has started yet. Resuming means re-querying [OptimizationCombinationEntity] rows with status=PENDING for this job, NOT trusting this index alone -- see [com.jarvis.tidb.optimization.repository.OptimizationRepository.pendingCombinations]'s own docstring for why. */
    val checkpointCombinationIndex: Int = -1,

    /** Links to the [BacktestEntity] this job's combinations get evaluated under, once Module 5's simulator exists to populate it. Null until then. */
    val backtestRowId: Long? = null,

    val errorMessage: String? = null,

    val startedAt: Long? = null,
    val completedAt: Long? = null,

    @Embedded
    val audit: AuditMetadata = AuditMetadata(),

    @Embedded
    val softDelete: SoftDeleteMetadata = SoftDeleteMetadata(),
)

@Entity(
    tableName = "optimization_combinations",
    foreignKeys = [
        ForeignKey(
            entity = OptimizationJobEntity::class,
            parentColumns = ["rowId"],
            childColumns = ["jobRowId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = BacktestRunEntity::class,
            parentColumns = ["rowId"],
            childColumns = ["backtestRunRowId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = BacktestResultEntity::class,
            parentColumns = ["rowId"],
            childColumns = ["backtestResultRowId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["jobRowId"]),
        Index(value = ["status"]),
        Index(value = ["jobRowId", "combinationIndex"], unique = true),
    ],
)
data class OptimizationCombinationEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,

    val uuid: String = GlobalId.new(),

    val jobRowId: Long,

    /** Position in the deterministic sequence [OptimizationAlgorithm.generateCombinations] produced -- what makes "resume from where we left off" well-defined instead of order-dependent on iteration order of a Map/Set. */
    val combinationIndex: Int,

    /** The exact parameter combination, e.g. {"period":14.0} -- same shape [com.jarvis.tidb.optimization.searchspace.ParameterSpec]-derived maps already use, serialized once here so it survives interruption/resume without needing to regenerate the whole sequence. */
    val parametersJson: String,

    @ColumnInfo(name = "status")
    val statusValue: String = OptimizationCombinationStatus.PENDING.name,

    /** Set once this combination has actually been evaluated -- see this file's own class docstring on why this links to, rather than duplicates, [BacktestRunEntity]. */
    val backtestRunRowId: Long? = null,

    /** Set once this combination has actually been evaluated -- see this file's own class docstring on why this links to, rather than duplicates, [BacktestResultEntity]. */
    val backtestResultRowId: Long? = null,

    /** "Persist rankings": null until the job's combinations have been evaluated and ranked (see [com.jarvis.tidb.optimization.repository.OptimizationRepository.rankCombinations]) -- 1 is the best-ranked combination in that job, matching typical "top N" query expectations. */
    val rank: Int? = null,

    val errorMessage: String? = null,

    @Embedded
    val audit: AuditMetadata = AuditMetadata(),
)

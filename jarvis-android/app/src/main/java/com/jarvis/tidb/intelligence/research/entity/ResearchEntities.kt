package com.jarvis.tidb.intelligence.research.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jarvis.tidb.core.common.AuditMetadata
import com.jarvis.tidb.core.common.GlobalId

/**
 * TRADING-006 — Module 5 Phase 3: Research Engine.
 *
 * Entirely new. `Hypothesis -> Experiment -> ExperimentRun -> ExperimentResult` is a fresh
 * four-level chain; nothing in Modules 1-4 modeled a testable hypothesis or a formal experiment.
 *
 * IMPORTANT — no new `LearningInsightEntity` is defined here. TRADING-006's brief lists
 * `LearningInsightEntity` under this phase, but that exact concept already exists at
 * `com.jarvis.tidb.analytics.entity.LearningInsightEntity` (Module 3, table `learning_insights`)
 * and is append-only there by established convention. Redefining it here would either collide
 * (same class name, two tables) or fork "what an insight is" across two modules — both violate
 * "do not redesign existing architecture." Instead, [ExperimentResultEntity.producedInsightRowId]
 * is a logical-only reference into the existing `learning_insights` table: when an experiment's
 * result is significant enough to promote to a durable insight, the caller writes a row via the
 * existing `analytics.repository.LearningRepository` and records its `rowId` back here.
 */

enum class HypothesisStatus(val value: String) {
    DRAFT("DRAFT"),
    ACTIVE("ACTIVE"),
    TESTING("TESTING"),
    SUPPORTED("SUPPORTED"),
    REFUTED("REFUTED"),
    RETIRED("RETIRED");

    companion object {
        fun from(value: String): HypothesisStatus = entries.firstOrNull { it.value == value } ?: DRAFT
    }
}

/** A testable, falsifiable statement about market or trading behavior — the root of the research chain. */
@Entity(
    tableName = "hypotheses",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["status"]),
        Index(value = ["proposedAt"])
    ]
)
data class HypothesisEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "hypothesisId") val hypothesisId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "statement") val statement: String,
    @ColumnInfo(name = "rationale") val rationale: String? = null,
    @ColumnInfo(name = "status") val status: HypothesisStatus = HypothesisStatus.DRAFT,
    @ColumnInfo(name = "proposedBy") val proposedBy: String = "SYSTEM",
    @ColumnInfo(name = "proposedAt") val proposedAt: Long = System.currentTimeMillis(),
    @Embedded val audit: AuditMetadata = AuditMetadata()
)

enum class ExperimentType(val value: String) {
    BACKTEST_COMPARISON("BACKTEST_COMPARISON"),
    PARAMETER_SWEEP("PARAMETER_SWEEP"),
    AB_STRATEGY("AB_STRATEGY"),
    OBSERVATIONAL("OBSERVATIONAL");

    companion object {
        fun from(value: String): ExperimentType = entries.firstOrNull { it.value == value } ?: OBSERVATIONAL
    }
}

enum class ExperimentStatus(val value: String) {
    PLANNED("PLANNED"),
    RUNNING("RUNNING"),
    COMPLETED("COMPLETED"),
    ABORTED("ABORTED");

    companion object {
        fun from(value: String): ExperimentStatus = entries.firstOrNull { it.value == value } ?: PLANNED
    }
}

/** A designed test of one [HypothesisEntity]. One hypothesis may have several experiments over time (e.g. re-tested with different parameters). */
@Entity(
    tableName = "experiments",
    foreignKeys = [
        ForeignKey(
            entity = HypothesisEntity::class,
            parentColumns = ["hypothesisId"],
            childColumns = ["hypothesisId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["hypothesisId"]),
        Index(value = ["status"])
    ]
)
data class ExperimentEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "experimentId") val experimentId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "hypothesisId") val hypothesisId: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "experimentType") val experimentType: ExperimentType,
    @ColumnInfo(name = "designDescription") val designDescription: String,
    @ColumnInfo(name = "status") val status: ExperimentStatus = ExperimentStatus.PLANNED,
    /** No separate `createdAt` field — [AuditMetadata.createdAt] on [audit] already covers it; avoids a duplicate-column collision. */
    @Embedded val audit: AuditMetadata = AuditMetadata()
)

enum class ExperimentRunStatus(val value: String) {
    QUEUED("QUEUED"),
    RUNNING("RUNNING"),
    SUCCEEDED("SUCCEEDED"),
    FAILED("FAILED");

    companion object {
        fun from(value: String): ExperimentRunStatus = entries.firstOrNull { it.value == value } ?: QUEUED
    }
}

/**
 * One concrete execution of an [ExperimentEntity] with a specific parameter set.
 * [relatedBacktestRunRowId] is a logical-only reference into
 * `analytics.entity.BacktestRunEntity.rowId` for experiments that execute via the existing
 * backtesting engine (Module 3) rather than duplicating run-tracking here.
 */
@Entity(
    tableName = "experiment_runs",
    foreignKeys = [
        ForeignKey(
            entity = ExperimentEntity::class,
            parentColumns = ["experimentId"],
            childColumns = ["experimentId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["experimentId"]),
        Index(value = ["status"]),
        Index(value = ["relatedBacktestRunRowId"])
    ]
)
data class ExperimentRunEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "runId") val runId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "experimentId") val experimentId: Long,
    @ColumnInfo(name = "runLabel") val runLabel: String,
    @ColumnInfo(name = "parametersJson") val parametersJson: String? = null,
    @ColumnInfo(name = "status") val status: ExperimentRunStatus = ExperimentRunStatus.QUEUED,
    @ColumnInfo(name = "startedAt") val startedAt: Long? = null,
    @ColumnInfo(name = "completedAt") val completedAt: Long? = null,
    @ColumnInfo(name = "relatedBacktestRunRowId") val relatedBacktestRunRowId: Long? = null,
    @Embedded val audit: AuditMetadata = AuditMetadata()
)

enum class ExperimentConclusion(val value: String) {
    HYPOTHESIS_SUPPORTED("HYPOTHESIS_SUPPORTED"),
    HYPOTHESIS_REFUTED("HYPOTHESIS_REFUTED"),
    INCONCLUSIVE("INCONCLUSIVE");

    companion object {
        fun from(value: String): ExperimentConclusion = entries.firstOrNull { it.value == value } ?: INCONCLUSIVE
    }
}

/** One measured outcome of an [ExperimentRunEntity]. A run may produce several results (one per metric tracked). */
@Entity(
    tableName = "experiment_results",
    foreignKeys = [
        ForeignKey(
            entity = ExperimentRunEntity::class,
            parentColumns = ["runId"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["runId"]),
        Index(value = ["conclusion"]),
        Index(value = ["producedInsightRowId"])
    ]
)
data class ExperimentResultEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "resultId") val resultId: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = GlobalId.new(),
    @ColumnInfo(name = "runId") val runId: Long,
    @ColumnInfo(name = "metricName") val metricName: String,
    @ColumnInfo(name = "metricValue") val metricValue: Double,
    @ColumnInfo(name = "conclusion") val conclusion: ExperimentConclusion? = null,
    @ColumnInfo(name = "summary") val summary: String? = null,
    /** Logical-only reference into `analytics.entity.LearningInsightEntity.rowId` (`learning_insights` table) — see file header. Deliberately not a Room `@ForeignKey` since it targets a table outside this package's ownership. */
    @ColumnInfo(name = "producedInsightRowId") val producedInsightRowId: Long? = null,
    @ColumnInfo(name = "recordedAt") val recordedAt: Long = System.currentTimeMillis()
)

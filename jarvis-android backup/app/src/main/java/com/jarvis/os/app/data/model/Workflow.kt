package com.jarvis.os.app.data.model

import java.time.Instant

// --- Sprint 11: Workflow Engine ---------------------------------------------------

enum class WorkflowStepStatus { PENDING, RUNNING, SUCCEEDED, FAILED, RETRYING, SKIPPED }

/** @param dependsOn stepIds that must SUCCEED before this step is eligible to run -- WorkflowEngine.run rejects a definition containing a cycle rather than silently deadlocking (see that class's docstring). */
data class WorkflowStep(
    val stepId: String,
    val name: String,
    val dependsOn: Set<String> = emptySet(),
    val maxRetries: Int = 0,
)

data class WorkflowDefinition(
    val workflowId: String,
    val name: String,
    val steps: List<WorkflowStep>,
)

data class WorkflowHistoryEntry(
    val timestamp: Instant,
    val stepId: String,
    val status: WorkflowStepStatus,
    val detail: String? = null,
)

data class WorkflowRunRecord(
    val runId: String,
    val workflowId: String,
    val startedAt: Instant,
    val completedAt: Instant?,
    val stepStatuses: Map<String, WorkflowStepStatus>,
    val history: List<WorkflowHistoryEntry>,
) {
    val succeeded: Boolean get() = completedAt != null && stepStatuses.values.all { it == WorkflowStepStatus.SUCCEEDED || it == WorkflowStepStatus.SKIPPED }
}

package com.jarvis.os.app.core.workflow

import com.jarvis.os.app.data.model.WorkflowDefinition
import com.jarvis.os.app.data.model.WorkflowHistoryEntry
import com.jarvis.os.app.data.model.WorkflowRunRecord
import com.jarvis.os.app.data.model.WorkflowStep
import com.jarvis.os.app.data.model.WorkflowStepStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 11 "Workflow Engine": dependency management (topological
 * execution order, Kahn's algorithm -- no third-party graph library,
 * per this codebase's stdlib-first preference), retry and recovery (a
 * step that throws or returns false is retried up to its
 * maxRetries before the whole run is marked failed), workflow history
 * (append-only WorkflowHistoryEntry list) and progress monitoring
 * (`runs` StateFlow, readable mid-run by an Executive Dashboard since
 * it's updated after every single step, not just at the end).
 *
 * `execute` is a caller-supplied suspend function rather than this
 * engine owning a fixed notion of "what a step does" -- a workflow
 * step might call a Tool, an Agent, or nothing at all (see
 * WorkflowEngineTest for a step that's pure bookkeeping) -- this
 * engine's only real job is ordering, retrying and recording, which is
 * exactly Sprint 11's four Workflow Engine bullets and nothing more.
 */
interface WorkflowEngine {
    val runs: StateFlow<List<WorkflowRunRecord>>

    /**
     * Returns the completed WorkflowRunRecord. Throws
     * IllegalArgumentException if `definition.steps` contains a
     * dependency cycle or references a dependsOn stepId that doesn't
     * exist in the same definition -- checked before any step runs, so
     * a broken definition never partially executes.
     */
    suspend fun run(definition: WorkflowDefinition, execute: suspend (WorkflowStep) -> Boolean): WorkflowRunRecord
}

@Singleton
class DefaultWorkflowEngine @Inject constructor() : WorkflowEngine {

    private val _runs = MutableStateFlow<List<WorkflowRunRecord>>(emptyList())
    override val runs: StateFlow<List<WorkflowRunRecord>> = _runs.asStateFlow()

    override suspend fun run(definition: WorkflowDefinition, execute: suspend (WorkflowStep) -> Boolean): WorkflowRunRecord {
        val order = topologicalOrder(definition.steps)

        val runId = UUID.randomUUID().toString()
        val statuses = definition.steps.associate { it.stepId to WorkflowStepStatus.PENDING }.toMutableMap()
        val history = mutableListOf<WorkflowHistoryEntry>()

        fun pushState() {
            _runs.update { list ->
                val record = WorkflowRunRecord(runId, definition.workflowId, list.firstOrNull { it.runId == runId }?.startedAt ?: Instant.now(), null, statuses.toMap(), history.toList())
                if (list.any { it.runId == runId }) list.map { if (it.runId == runId) record else it } else list + record
            }
        }
        pushState()

        var failedFast = false
        for (stepId in order) {
            val step = definition.steps.first { it.stepId == stepId }
            if (failedFast) {
                statuses[stepId] = WorkflowStepStatus.SKIPPED
                history += WorkflowHistoryEntry(Instant.now(), stepId, WorkflowStepStatus.SKIPPED, "Skipped -- an earlier dependency failed")
                pushState()
                continue
            }
            // A step whose dependsOn didn't all SUCCEED is skipped, not run --
            // topologicalOrder only guarantees ORDER, not that every
            // upstream step actually succeeded.
            if (step.dependsOn.any { statuses[it] != WorkflowStepStatus.SUCCEEDED }) {
                statuses[stepId] = WorkflowStepStatus.SKIPPED
                history += WorkflowHistoryEntry(Instant.now(), stepId, WorkflowStepStatus.SKIPPED, "Skipped -- a dependency did not succeed")
                pushState()
                continue
            }

            var attempt = 0
            var succeeded = false
            while (true) {
                statuses[stepId] = if (attempt == 0) WorkflowStepStatus.RUNNING else WorkflowStepStatus.RETRYING
                pushState()
                succeeded = try {
                    execute(step)
                } catch (e: Exception) {
                    history += WorkflowHistoryEntry(Instant.now(), stepId, WorkflowStepStatus.FAILED, "Threw: ${e.message}")
                    false
                }
                if (succeeded || attempt >= step.maxRetries) break
                attempt++
            }
            statuses[stepId] = if (succeeded) WorkflowStepStatus.SUCCEEDED else WorkflowStepStatus.FAILED
            history += WorkflowHistoryEntry(Instant.now(), stepId, statuses[stepId]!!, if (succeeded) null else "Failed after ${attempt + 1} attempt(s)")
            pushState()
            if (!succeeded) failedFast = true
        }

        val finalRecord = WorkflowRunRecord(
            runId, definition.workflowId,
            _runs.value.first { it.runId == runId }.startedAt,
            Instant.now(), statuses.toMap(), history.toList(),
        )
        _runs.update { list -> list.map { if (it.runId == runId) finalRecord else it } }
        return finalRecord
    }

    /** Kahn's algorithm. Throws IllegalArgumentException on an unknown dependsOn reference or a cycle, rather than looping forever or silently dropping steps. */
    private fun topologicalOrder(steps: List<WorkflowStep>): List<String> {
        val ids = steps.map { it.stepId }.toSet()
        for (step in steps) {
            for (dep in step.dependsOn) {
                require(dep in ids) { "WorkflowStep '${step.stepId}' depends on unknown step '$dep'" }
            }
        }
        val inDegree = steps.associate { it.stepId to it.dependsOn.size }.toMutableMap()
        val dependents = steps.associate { it.stepId to mutableListOf<String>() }
        for (step in steps) for (dep in step.dependsOn) dependents.getValue(dep).add(step.stepId)

        val queue = ArrayDeque(inDegree.filterValues { it == 0 }.keys.sorted())
        val order = mutableListOf<String>()
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            order += current
            for (dependent in dependents.getValue(current)) {
                inDegree[dependent] = inDegree.getValue(dependent) - 1
                if (inDegree.getValue(dependent) == 0) queue.addLast(dependent)
            }
        }
        require(order.size == steps.size) { "WorkflowDefinition has a dependency cycle -- cannot determine an execution order" }
        return order
    }
}

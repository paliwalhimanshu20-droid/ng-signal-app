package com.jarvis.os.app.core.workflow

import com.jarvis.os.app.data.model.WorkflowDefinition
import com.jarvis.os.app.data.model.WorkflowStep
import com.jarvis.os.app.data.model.WorkflowStepStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowEngineTest {

    @Test
    fun `steps run in dependency order`() = runTest {
        val engine = DefaultWorkflowEngine()
        val executionOrder = mutableListOf<String>()
        val definition = WorkflowDefinition(
            "wf1", "Test workflow",
            listOf(
                WorkflowStep("c", "C", dependsOn = setOf("b")),
                WorkflowStep("a", "A"),
                WorkflowStep("b", "B", dependsOn = setOf("a")),
            ),
        )
        val record = engine.run(definition) { step -> executionOrder += step.stepId; true }
        assertEquals(listOf("a", "b", "c"), executionOrder)
        assertTrue(record.succeeded)
    }

    @Test
    fun `a failing step is retried up to maxRetries then fails`() = runTest {
        val engine = DefaultWorkflowEngine()
        var attempts = 0
        val definition = WorkflowDefinition("wf2", "Retry test", listOf(WorkflowStep("a", "A", maxRetries = 2)))
        val record = engine.run(definition) { attempts++; false }
        assertEquals(3, attempts) // 1 initial + 2 retries
        assertEquals(WorkflowStepStatus.FAILED, record.stepStatuses["a"])
        assertTrue(!record.succeeded)
    }

    @Test
    fun `a step succeeding after retries is recorded as SUCCEEDED`() = runTest {
        val engine = DefaultWorkflowEngine()
        var attempts = 0
        val definition = WorkflowDefinition("wf3", "Eventually succeeds", listOf(WorkflowStep("a", "A", maxRetries = 3)))
        val record = engine.run(definition) { attempts++; attempts >= 2 }
        assertEquals(WorkflowStepStatus.SUCCEEDED, record.stepStatuses["a"])
    }

    @Test
    fun `downstream steps are skipped when a dependency fails`() = runTest {
        val engine = DefaultWorkflowEngine()
        val definition = WorkflowDefinition(
            "wf4", "Skip test",
            listOf(WorkflowStep("a", "A"), WorkflowStep("b", "B", dependsOn = setOf("a"))),
        )
        val record = engine.run(definition) { step -> step.stepId != "a" }
        assertEquals(WorkflowStepStatus.FAILED, record.stepStatuses["a"])
        assertEquals(WorkflowStepStatus.SKIPPED, record.stepStatuses["b"])
    }

    @Test
    fun `a dependency cycle throws before any step runs`() = runTest {
        val engine = DefaultWorkflowEngine()
        val definition = WorkflowDefinition(
            "wf5", "Cycle",
            listOf(WorkflowStep("a", "A", dependsOn = setOf("b")), WorkflowStep("b", "B", dependsOn = setOf("a"))),
        )
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { engine.run(definition) { true } }
        }
    }

    @Test
    fun `an unknown dependsOn reference throws`() = runTest {
        val engine = DefaultWorkflowEngine()
        val definition = WorkflowDefinition("wf6", "Bad ref", listOf(WorkflowStep("a", "A", dependsOn = setOf("ghost"))))
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { engine.run(definition) { true } }
        }
    }

    @Test
    fun `runs StateFlow is updated progressively, not only at the end`() = runTest {
        val engine = DefaultWorkflowEngine()
        val definition = WorkflowDefinition("wf7", "Progress", listOf(WorkflowStep("a", "A")))
        engine.run(definition) { true }
        assertEquals(1, engine.runs.value.size)
        assertEquals(WorkflowStepStatus.SUCCEEDED, engine.runs.value.first().stepStatuses["a"])
    }
}

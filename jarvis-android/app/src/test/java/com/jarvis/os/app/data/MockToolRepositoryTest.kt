package com.jarvis.os.app.data

import com.jarvis.os.app.core.tools.CalculatorTool
import com.jarvis.os.app.core.tools.ProjectNoteTool
import com.jarvis.os.app.core.tools.ToolResult
import com.jarvis.os.app.data.model.ApprovalOutcome
import com.jarvis.os.app.data.repository.MockApprovalRepository
import com.jarvis.os.app.data.repository.MockToolRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockToolRepositoryTest {

    private fun repo(approvals: MockApprovalRepository = MockApprovalRepository()) =
        MockToolRepository(setOf(CalculatorTool(), ProjectNoteTool()), approvals) to approvals

    @Test
    fun `LOW risk tool executes without any approval`() = runTest {
        val (tools, _) = repo()
        val result = tools.execute("calculator", "2 + 2 * 3")
        assertTrue(result is ToolResult.Success)
        assertEquals("8.0", (result as ToolResult.Success).output)
    }

    @Test
    fun `MODERATE risk tool without approvalId requests approval instead of running`() = runTest {
        val (tools, approvals) = repo()
        val result = tools.execute("project_note", "hello")
        assertTrue(result is ToolResult.Failure)
        assertEquals(1, approvals.items.value.size)
        assertEquals(ApprovalOutcome.PENDING, approvals.items.value.first().outcome)
    }

    @Test
    fun `MODERATE risk tool runs once its approval is APPROVED`() = runTest {
        val (tools, approvals) = repo()
        tools.execute("project_note", "hello") // creates the pending approval as a side effect
        val approvalId = approvals.items.value.first().approvalId
        approvals.approve(approvalId, "owner")

        val result = tools.execute("project_note", "hello", approvalId)
        assertTrue(result is ToolResult.Success)
    }

    @Test
    fun `approval for one tool cannot authorize a different tool`() = runTest {
        val (tools, approvals) = repo()
        val approval = approvals.requestApproval(
            kind = com.jarvis.os.app.data.model.ApprovalKind.TOOL_EXECUTION,
            title = "Run tool: Calculator",
            reason = "test",
            riskLevel = com.jarvis.os.app.data.model.RiskLevel.MODERATE,
            relatedToolId = "calculator",
        )
        approvals.approve(approval.approvalId, "owner")

        val result = tools.execute("project_note", "hello", approval.approvalId)
        assertTrue(result is ToolResult.Failure)
    }

    @Test
    fun `unknown tool id fails cleanly`() = runTest {
        val (tools, _) = repo()
        val result = tools.execute("nonexistent", "x")
        assertTrue(result is ToolResult.Failure)
    }
}

package com.jarvis.os.app.data

import com.jarvis.os.app.data.model.ApprovalKind
import com.jarvis.os.app.data.model.ApprovalOutcome
import com.jarvis.os.app.data.model.RiskLevel
import com.jarvis.os.app.data.repository.ApprovalOperationError
import com.jarvis.os.app.data.repository.MockApprovalRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MockApprovalRepositoryTest {

    private fun newApproval(repo: MockApprovalRepository) = repo.requestApproval(
        kind = ApprovalKind.CONNECTION_REQUEST,
        title = "Connect Test Provider",
        reason = "test",
        riskLevel = RiskLevel.MODERATE,
        requestedBy = "owner",
    )

    @Test
    fun `requesting an approval creates it PENDING and audits creation`() {
        val repo = MockApprovalRepository()
        val approval = newApproval(repo)

        assertEquals(ApprovalOutcome.PENDING, approval.outcome)
        val record = repo.auditLog.value.first { it.approvalId == approval.approvalId }
        assertNull(record.previousState)
        assertEquals(ApprovalOutcome.PENDING, record.newState)
        assertEquals("Request created", record.reason)
    }

    @Test
    fun `full lifecycle - approve then revoke then approve again`() {
        val repo = MockApprovalRepository()
        val approval = newApproval(repo)

        repo.approve(approval.approvalId, "owner")
        assertEquals(ApprovalOutcome.APPROVED, repo.items.value.first { it.approvalId == approval.approvalId }.outcome)

        repo.revoke(approval.approvalId, "owner", reason = "no longer needed")
        assertEquals(ApprovalOutcome.REVOKED, repo.items.value.first { it.approvalId == approval.approvalId }.outcome)

        repo.approve(approval.approvalId, "owner") // "Approve Again"
        assertEquals(ApprovalOutcome.APPROVED, repo.items.value.first { it.approvalId == approval.approvalId }.outcome)

        // Every one of those four transitions (create, approve, revoke,
        // approve-again) must have left its own permanent record.
        val history = repo.auditLog.value.filter { it.approvalId == approval.approvalId }.sortedBy { it.timestamp }
        assertEquals(4, history.size)
        assertEquals(listOf(null, ApprovalOutcome.PENDING, ApprovalOutcome.APPROVED, ApprovalOutcome.REVOKED), history.map { it.previousState })
        assertEquals(listOf(ApprovalOutcome.PENDING, ApprovalOutcome.APPROVED, ApprovalOutcome.REVOKED, ApprovalOutcome.APPROVED), history.map { it.newState })
    }

    @Test
    fun `rejected pending approval cannot later be approved`() {
        val repo = MockApprovalRepository()
        val approval = newApproval(repo)
        repo.reject(approval.approvalId, "owner")

        assertThrows(ApprovalOperationError::class.java) { repo.approve(approval.approvalId, "owner") }
    }

    @Test
    fun `cancelled and expired approvals are terminal`() {
        val repo = MockApprovalRepository()
        val cancelled = newApproval(repo)
        repo.cancel(cancelled.approvalId, "owner")
        assertThrows(ApprovalOperationError::class.java) { repo.approve(cancelled.approvalId, "owner") }

        val expired = newApproval(repo)
        repo.expire(expired.approvalId, "system")
        assertThrows(ApprovalOperationError::class.java) { repo.approve(expired.approvalId, "owner") }
    }

    @Test
    fun `revoke requires an approved approval, not a pending one`() {
        val repo = MockApprovalRepository()
        val approval = newApproval(repo)

        assertThrows(ApprovalOperationError::class.java) { repo.revoke(approval.approvalId, "owner") }
    }

    @Test
    fun `audit log is append-only - resolving many approvals never shrinks or rewrites earlier entries`() {
        val repo = MockApprovalRepository()
        val a1 = newApproval(repo)
        val sizeAfterCreate = repo.auditLog.value.size

        repo.approve(a1.approvalId, "owner")
        val firstRecordStillPresent = repo.auditLog.value.any {
            it.approvalId == a1.approvalId && it.previousState == null && it.newState == ApprovalOutcome.PENDING
        }
        assertTrue(firstRecordStillPresent)
        assertTrue(repo.auditLog.value.size > sizeAfterCreate)
    }

    @Test
    fun `every accepted transition is published on the transitions flow`() = runTest(UnconfinedTestDispatcher()) {
        val repo = MockApprovalRepository()
        val seen = mutableListOf<ApprovalOutcome>()
        val approval = newApproval(repo)

        val job = launch { repo.transitions.collect { seen += it.newState } }
        repo.approve(approval.approvalId, "owner")
        repo.revoke(approval.approvalId, "owner")
        job.cancel()

        assertEquals(listOf(ApprovalOutcome.APPROVED, ApprovalOutcome.REVOKED), seen)
    }
}

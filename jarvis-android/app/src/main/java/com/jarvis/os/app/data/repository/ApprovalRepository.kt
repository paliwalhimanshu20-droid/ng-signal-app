package com.jarvis.os.app.data.repository

import com.jarvis.os.app.data.model.ApprovalItem
import com.jarvis.os.app.data.model.ApprovalKind
import com.jarvis.os.app.data.model.ApprovalOutcome
import com.jarvis.os.app.data.model.RiskLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Same "mirrors the real backend's method shapes, mock data for now" pattern as ConnectionRepository — see that file's module docstring. Mirrors jarvis.approval.engine.ApprovalEngine.confirm(approval_id, approved, approved_by). */
interface ApprovalRepository {
    val items: StateFlow<List<ApprovalItem>>
    fun approve(approvalId: String, approvedBy: String)
    fun reject(approvalId: String, approvedBy: String)
}

@Singleton
class MockApprovalRepository @Inject constructor() : ApprovalRepository {
    private val _items = MutableStateFlow(seed())
    override val items: StateFlow<List<ApprovalItem>> = _items.asStateFlow()

    override fun approve(approvalId: String, approvedBy: String) = resolve(approvalId, ApprovalOutcome.APPROVED, approvedBy)
    override fun reject(approvalId: String, approvedBy: String) = resolve(approvalId, ApprovalOutcome.REJECTED, approvedBy)

    private fun resolve(approvalId: String, outcome: ApprovalOutcome, resolvedBy: String) {
        _items.update { list ->
            list.map {
                if (it.approvalId == approvalId && it.outcome == ApprovalOutcome.WAITING) {
                    it.copy(outcome = outcome, resolvedAt = Instant.now(), resolvedBy = resolvedBy)
                } else it
            }
        }
    }

    private fun seed(): List<ApprovalItem> = listOf(
        ApprovalItem(
            UUID.randomUUID().toString(), ApprovalKind.CONNECTION_REQUEST, "Connect Claude",
            "New AI provider connection requested.", RiskLevel.MODERATE, ApprovalOutcome.WAITING,
            Instant.now(), null, null,
        ),
        ApprovalItem(
            UUID.randomUUID().toString(), ApprovalKind.PERMISSION_REQUEST, "Deploy NG Signal Pro update",
            "Tier 2 action requires owner approval.", RiskLevel.HIGH, ApprovalOutcome.WAITING,
            Instant.now(), null, null,
        ),
        ApprovalItem(
            UUID.randomUUID().toString(), ApprovalKind.CONNECTION_REQUEST, "Connect GitHub",
            "Repository read access requested.", RiskLevel.LOW, ApprovalOutcome.APPROVED,
            Instant.now(), Instant.now(), "owner",
        ),
    )
}

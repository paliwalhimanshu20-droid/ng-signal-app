package com.jarvis.os.app.data.repository

import com.jarvis.os.app.data.model.ApprovalAuditRecord
import com.jarvis.os.app.data.model.ApprovalItem
import com.jarvis.os.app.data.model.ApprovalKind
import com.jarvis.os.app.data.model.ApprovalOutcome
import com.jarvis.os.app.data.model.RiskLevel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 9 Final: same "mirrors the real backend's method shapes, mock
 * data for now" pattern as ConnectionRepository (see that file's module
 * docstring) and the same validated-state-machine shape PR1 gave
 * connections -- `allowedTransitions` is the single source of truth for
 * legality, every mutating method routes through `transition()`, and
 * every accepted transition is both appended to `auditLog` (never
 * updated, never removed -- see ApprovalAuditRecord's docstring for
 * why that's a real guarantee, not just a naming convention: nothing
 * in this file ever calls `_auditLog.update` with anything but
 * `list + newRecord`) and published on `transitions` for JarvisCore to
 * forward as CoreEvent.ApprovalStatusChanged.
 *
 * State machine:
 *
 *   PENDING --approve--> APPROVED
 *   PENDING --reject----> REJECTED (terminal)
 *   PENDING --cancel----> CANCELLED (terminal)
 *   PENDING --expire----> EXPIRED (terminal)
 *   APPROVED --revoke---> REVOKED
 *   REVOKED --approve---> APPROVED   ("Approve Again")
 *
 * REJECTED, CANCELLED and EXPIRED have no outgoing transitions --
 * once an approval is refused, withdrawn, or times out, a new approval
 * must be requested; only a REVOKED (previously-granted-then-pulled-back)
 * approval can be restored, which is what "Approve Again" means.
 *
 * Honesty note: the three approvals seeded in `seed()` below predate
 * relatedConnectionId existing as a concept and are NOT retroactively
 * linked to the seeded Connections in ConnectionRepository (doing so
 * would mean this repository reaching into another repository's data
 * at construction time, which is exactly the kind of cross-repository
 * coupling this codebase's layering avoids). Only approvals created
 * through `requestApproval`/JarvisCore.requestConnectionApproval going
 * forward carry a real link.
 */
interface ApprovalRepository {
    val items: StateFlow<List<ApprovalItem>>

    /** Every audit record ever created, oldest first, for every approval. Append-only -- see this interface's docstring. */
    val auditLog: StateFlow<List<ApprovalAuditRecord>>

    /** Fires once per newly-created approval. JarvisCore is the sole subscriber -- see its docstring. */
    val created: SharedFlow<ApprovalItem>

    /** Fires once per accepted state transition. JarvisCore is the sole subscriber -- see its docstring. */
    val transitions: SharedFlow<ApprovalTransition>

    fun requestApproval(
        kind: ApprovalKind,
        title: String,
        reason: String,
        riskLevel: RiskLevel,
        requestedBy: String = "system",
        relatedConnectionId: String? = null,
        /** Sprint 10: set for kind == TOOL_EXECUTION. See ApprovalItem.relatedToolId. */
        relatedToolId: String? = null,
        /** Sprint 11: set for kind == AGENT_TASK. See ApprovalItem.relatedAgentTaskId. */
        relatedAgentTaskId: String? = null,
    ): ApprovalItem

    fun approve(approvalId: String, actor: String, reason: String? = null)
    fun reject(approvalId: String, actor: String, reason: String? = null)
    fun cancel(approvalId: String, actor: String, reason: String? = null)
    fun expire(approvalId: String, actor: String = "system", reason: String? = null)
    fun revoke(approvalId: String, actor: String, reason: String? = null)
}

/** One accepted transition, as published on ApprovalRepository.transitions. */
data class ApprovalTransition(
    val approvalId: String,
    val title: String,
    val relatedConnectionId: String?,
    val previousState: ApprovalOutcome,
    val newState: ApprovalOutcome,
    val actor: String,
    val reason: String? = null,
)

class ApprovalOperationError(message: String) : Exception(message)

@Singleton
class MockApprovalRepository @Inject constructor() : ApprovalRepository {

    private val _items = MutableStateFlow(seed())
    override val items: StateFlow<List<ApprovalItem>> = _items.asStateFlow()

    private val _auditLog = MutableStateFlow<List<ApprovalAuditRecord>>(emptyList())
    override val auditLog: StateFlow<List<ApprovalAuditRecord>> = _auditLog.asStateFlow()

    private val _created = MutableSharedFlow<ApprovalItem>(extraBufferCapacity = 32)
    override val created: SharedFlow<ApprovalItem> = _created

    private val _transitions = MutableSharedFlow<ApprovalTransition>(extraBufferCapacity = 32)
    override val transitions: SharedFlow<ApprovalTransition> = _transitions

    override fun requestApproval(
        kind: ApprovalKind,
        title: String,
        reason: String,
        riskLevel: RiskLevel,
        requestedBy: String,
        relatedConnectionId: String?,
        relatedToolId: String?,
        relatedAgentTaskId: String?,
    ): ApprovalItem {
        val approval = ApprovalItem(
            approvalId = UUID.randomUUID().toString(),
            kind = kind,
            title = title,
            reason = reason,
            riskLevel = riskLevel,
            outcome = ApprovalOutcome.PENDING,
            createdAt = Instant.now(),
            resolvedAt = null,
            resolvedBy = null,
            relatedConnectionId = relatedConnectionId,
            relatedToolId = relatedToolId,
            relatedAgentTaskId = relatedAgentTaskId,
        )
        _items.update { it + approval }
        appendAudit(approval.approvalId, requestedBy, relatedConnectionId, previousState = null, newState = ApprovalOutcome.PENDING, reason = "Request created")
        _created.tryEmit(approval)
        return approval
    }

    override fun approve(approvalId: String, actor: String, reason: String?) {
        transition(approvalId, ApprovalOutcome.APPROVED, actor, reason) {
            it.copy(outcome = ApprovalOutcome.APPROVED, resolvedAt = Instant.now(), resolvedBy = actor)
        }
    }

    override fun reject(approvalId: String, actor: String, reason: String?) {
        transition(approvalId, ApprovalOutcome.REJECTED, actor, reason) {
            it.copy(outcome = ApprovalOutcome.REJECTED, resolvedAt = Instant.now(), resolvedBy = actor)
        }
    }

    override fun cancel(approvalId: String, actor: String, reason: String?) {
        transition(approvalId, ApprovalOutcome.CANCELLED, actor, reason) {
            it.copy(outcome = ApprovalOutcome.CANCELLED, resolvedAt = Instant.now(), resolvedBy = actor)
        }
    }

    override fun expire(approvalId: String, actor: String, reason: String?) {
        transition(approvalId, ApprovalOutcome.EXPIRED, actor, reason) {
            it.copy(outcome = ApprovalOutcome.EXPIRED, resolvedAt = Instant.now(), resolvedBy = actor)
        }
    }

    override fun revoke(approvalId: String, actor: String, reason: String?) {
        transition(approvalId, ApprovalOutcome.REVOKED, actor, reason) {
            it.copy(outcome = ApprovalOutcome.REVOKED, resolvedAt = Instant.now(), resolvedBy = actor)
        }
    }

    /**
     * The single validation gate every mutating method above (besides
     * requestApproval) routes through -- mirrors
     * ConnectionRepository.transition() exactly: look up current state,
     * check `newState` against `allowedTransitions[current]`, apply
     * `block` only if legal, append an audit record, and emit on
     * `transitions`. A state change that didn't go through this method
     * cannot exist in `_items`, and an audit record cannot exist
     * without a state change that actually happened -- the two can't
     * drift apart.
     */
    private fun transition(
        approvalId: String,
        newState: ApprovalOutcome,
        actor: String,
        reason: String?,
        block: (ApprovalItem) -> ApprovalItem,
    ) {
        val current = _items.value.firstOrNull { it.approvalId == approvalId }
            ?: throw ApprovalOperationError("No approval found with id '$approvalId'.")
        val allowed = allowedTransitions[current.outcome].orEmpty()
        if (newState !in allowed) {
            throw ApprovalOperationError(
                "Cannot move '${current.title}' from ${current.outcome} to $newState " +
                    "-- allowed next states are ${if (allowed.isEmpty()) "none (terminal)" else allowed}.",
            )
        }
        _items.update { list -> list.map { if (it.approvalId == approvalId) block(it) else it } }
        appendAudit(approvalId, actor, current.relatedConnectionId, current.outcome, newState, reason)
        _transitions.tryEmit(ApprovalTransition(approvalId, current.title, current.relatedConnectionId, current.outcome, newState, actor, reason))
    }

    private fun appendAudit(
        approvalId: String,
        actor: String,
        relatedConnectionId: String?,
        previousState: ApprovalOutcome?,
        newState: ApprovalOutcome,
        reason: String?,
    ) {
        // The append-only guarantee lives entirely in this one line:
        // `list + record`, never `list.map { ... }` or any form of
        // in-place replacement. There is no other write path to
        // _auditLog anywhere in this class.
        _auditLog.update { list ->
            list + ApprovalAuditRecord(UUID.randomUUID().toString(), approvalId, Instant.now(), actor, relatedConnectionId, previousState, newState, reason)
        }
    }

    private fun seed(): List<ApprovalItem> = listOf(
        ApprovalItem(
            UUID.randomUUID().toString(), ApprovalKind.CONNECTION_REQUEST, "Connect Claude",
            "New AI provider connection requested.", RiskLevel.MODERATE, ApprovalOutcome.PENDING,
            Instant.now(), null, null,
        ),
        ApprovalItem(
            UUID.randomUUID().toString(), ApprovalKind.PERMISSION_REQUEST, "Deploy NG Signal Pro update",
            "Tier 2 action requires owner approval.", RiskLevel.HIGH, ApprovalOutcome.PENDING,
            Instant.now(), null, null,
        ),
        ApprovalItem(
            UUID.randomUUID().toString(), ApprovalKind.CONNECTION_REQUEST, "Connect GitHub",
            "Repository read access requested.", RiskLevel.LOW, ApprovalOutcome.APPROVED,
            Instant.now(), Instant.now(), "owner",
        ),
    )

    companion object {
        /** The full Sprint 9 Final transition graph. See this file's class docstring for the diagram. */
        val allowedTransitions: Map<ApprovalOutcome, Set<ApprovalOutcome>> = mapOf(
            ApprovalOutcome.PENDING to setOf(ApprovalOutcome.APPROVED, ApprovalOutcome.REJECTED, ApprovalOutcome.CANCELLED, ApprovalOutcome.EXPIRED),
            ApprovalOutcome.APPROVED to setOf(ApprovalOutcome.REVOKED),
            ApprovalOutcome.REVOKED to setOf(ApprovalOutcome.APPROVED),
            ApprovalOutcome.REJECTED to emptySet(),
            ApprovalOutcome.CANCELLED to emptySet(),
            ApprovalOutcome.EXPIRED to emptySet(),
        )
    }
}

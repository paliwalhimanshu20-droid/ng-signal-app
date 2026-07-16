package com.jarvis.os.app.feature.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.os.app.core.JarvisCore
import com.jarvis.os.app.data.model.ConnectionHealth
import com.jarvis.os.app.data.repository.ConnectionOperationError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sprint 9 (PR1): every action below now calls a JarvisCore
 * coordination method instead of ConnectionRepository directly --
 * this is the "remove duplicated coordination logic from ViewModels"
 * requirement. The ViewModel still owns nothing about connection
 * business rules (it never decided what states were valid, before or
 * now); it's just no longer the thing directly holding a reference to
 * ConnectionRepository.
 */
@HiltViewModel
class ConnectionsViewModel @Inject constructor(
    private val core: JarvisCore,
) : ViewModel() {

    val connections = core.connections.connections.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Sprint-7.1 UX polish: a state-check failure here (e.g. a stale
    // button firing after the connection's status already changed)
    // must never crash the app or vanish silently — it's surfaced as a
    // one-shot event the screen turns into a Snackbar. This does not
    // relax or change any governance rule; ConnectionOperationError is
    // still thrown by the same checks it always was, now inside
    // ConnectionRepository's single `transition()` gate.
    private val _errors = MutableSharedFlow<String>()
    val errors: SharedFlow<String> = _errors

    private fun runGuarded(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: ConnectionOperationError) {
                _errors.emit(e.message ?: "That action is no longer valid for this connection's current state.")
            }
        }
    }

    // Owner Sovereignty (Sprint-6 Part 3), enforced at the UI action
    // layer too: every button below maps 1:1 to a JarvisCore
    // coordination method that mirrors ConnectionManager's real
    // governance rule — there is no "quick approve" shortcut that
    // skips a state check.
    fun approve(connectionId: String) = runGuarded { core.approveConnection(connectionId) }
    fun reject(connectionId: String) = runGuarded { core.rejectConnection(connectionId) }
    fun connect(connectionId: String) = runGuarded { core.connectConnection(connectionId) }
    fun suspend(connectionId: String) = runGuarded { core.suspendConnection(connectionId) }
    fun disconnect(connectionId: String) = runGuarded { core.disconnectConnection(connectionId) }
    fun reconnect(connectionId: String) = runGuarded { core.reconnectConnection(connectionId) }
    fun disableAll() = runGuarded { core.disableAllConnections() }

    fun testConnection(connectionId: String): ConnectionHealth = core.testConnection(connectionId)

    /**
     * Sprint 12 "Every Button Must Work": real data, not a placeholder
     * -- ApprovalRepository.auditLog (Sprint 9) already records every
     * approval/rejection/connect/suspend transition with a
     * relatedConnectionId, it just had no UI reading it filtered by
     * connection until now. Not a Flow: the audit log only changes when
     * an action above actually runs, so a synchronous snapshot read at
     * the moment "View Audit" is tapped is enough -- no separate
     * reactive subscription needed for a one-shot dialog.
     */
    fun auditFor(connectionId: String): List<com.jarvis.os.app.data.model.ApprovalAuditRecord> =
        core.approvals.auditLog.value.filter { it.relatedConnectionId == connectionId }
}

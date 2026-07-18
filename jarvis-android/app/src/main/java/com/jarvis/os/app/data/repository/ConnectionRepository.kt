package com.jarvis.os.app.data.repository

import com.jarvis.os.app.data.model.Connection
import com.jarvis.os.app.data.model.ConnectionHealth
import com.jarvis.os.app.data.model.ConnectionStatus
import com.jarvis.os.app.data.model.PermissionScope
import com.jarvis.os.app.data.model.TrustLevel
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
 * BACKEND STATUS — read before wiring a real client:
 *
 * No HTTP/gRPC/IPC bridge to the Python backend exists yet (Sprint-6
 * built jarvis.connections.ConnectionManager as an in-process Python
 * object with no network-exposed API — see that sprint's own delivery
 * notes). This interface's method names and parameter shapes were
 * chosen to mirror ConnectionManager's public methods 1:1
 * (request_connection -> requestConnection, approve(connection_id,
 * approved_by) -> approve(connectionId, approvedBy), etc.) SPECIFICALLY
 * so that the day a real API exists, a `RemoteConnectionRepository`
 * implementing this same interface is a thin HTTP client, not a
 * redesign of anything that calls it (JarvisCore is the only thing
 * that depends on `ConnectionRepository` as of Sprint 9 — see that
 * class's docstring — never a ViewModel directly).
 *
 * SPRINT 9 — validated state machine:
 *
 *   PENDING_APPROVAL --approve--> APPROVED --connect--> CONNECTING
 *   CONNECTING --markConnected--> CONNECTED
 *   CONNECTING --markError-------> ERROR
 *   CONNECTED  --markError-------> ERROR
 *   CONNECTED  --suspend---------> SUSPENDED
 *   SUSPENDED  --reconnect-------> CONNECTING
 *   ERROR      --reconnect-------> CONNECTING
 *   PENDING_APPROVAL --reject----> REJECTED (terminal)
 *   {APPROVED,CONNECTING,CONNECTED,SUSPENDED,ERROR} --disconnect--> DISCONNECTED (terminal)
 *
 * `allowedTransitions` below is the single source of truth for this
 * graph — every mutating method routes through `transition()`, which
 * consults it, so an impossible state combination (e.g. suspending a
 * connection that was never connected) throws ConnectionOperationError
 * instead of silently succeeding. Every successful transition is
 * emitted on `transitions`; JarvisCore is the sole subscriber, and
 * republishes each one as CoreEvent.ConnectionStatusChanged (see that
 * class) — this repository does not depend on JarvisCore or CoreEvent
 * itself, keeping the dependency direction one-way per Sprint 9
 * Section 7 ("JarvisCore coordinates only").
 *
 * `MockConnectionRepository` below is real, working, in-memory state
 * management — every method genuinely transitions state and genuinely
 * fails on an invalid transition, exactly like the Python
 * ConnectionManager's own rules — it is a faithful behavioral
 * stand-in, not a UI placeholder that always succeeds.
 */
interface ConnectionRepository {
    val connections: StateFlow<List<Connection>>

    /** Every successful state transition, in order. JarvisCore is the sole subscriber -- see this file's class docstring. */
    val transitions: SharedFlow<ConnectionTransition>

    fun requestConnection(
        providerId: String,
        providerName: String,
        requestedPermissions: Set<PermissionScope>,
        maximumPermission: PermissionScope,
        profileTags: Set<String> = emptySet(),
    ): Connection

    fun approve(connectionId: String, approvedBy: String)
    fun reject(connectionId: String, reason: String)

    /** APPROVED -> CONNECTING. The explicit "begin connecting" step Sprint 9 Flow A requires between an owner's approval and a live connection. */
    fun connect(connectionId: String)

    /** CONNECTING -> CONNECTED. */
    fun markConnected(connectionId: String)

    /** CONNECTING or CONNECTED -> ERROR. Sprint 9 Flow C: a failed connect attempt or a live connection dropping. */
    fun markError(connectionId: String, reason: String)

    fun disconnect(connectionId: String, reason: String? = null)
    fun suspend(connectionId: String, reason: String)

    /** SUSPENDED or ERROR -> CONNECTING (retry/resume). Callers that want the resulting CONNECTED state must follow up with markConnected once the (mock or real) connect attempt resolves, same as a fresh connect(). */
    fun reconnect(connectionId: String)

    fun disableAll(reason: String)
    fun testConnection(connectionId: String): ConnectionHealth
}

/** One accepted transition, as published on ConnectionRepository.transitions. */
data class ConnectionTransition(
    val connectionId: String,
    val providerName: String,
    val previousStatus: ConnectionStatus,
    val newStatus: ConnectionStatus,
    val reason: String? = null,
)

class ConnectionOperationError(message: String) : Exception(message)

@Singleton
class MockConnectionRepository @Inject constructor() : ConnectionRepository {

    private val _connections = MutableStateFlow(seedConnections())
    override val connections: StateFlow<List<Connection>> = _connections.asStateFlow()

    private val _transitions = MutableSharedFlow<ConnectionTransition>(extraBufferCapacity = 32)
    override val transitions: SharedFlow<ConnectionTransition> = _transitions

    override fun requestConnection(
        providerId: String,
        providerName: String,
        requestedPermissions: Set<PermissionScope>,
        maximumPermission: PermissionScope,
        profileTags: Set<String>,
    ): Connection {
        val connection = Connection(
            connectionId = UUID.randomUUID().toString(),
            providerId = providerId,
            providerName = providerName,
            status = ConnectionStatus.PENDING_APPROVAL,
            trustLevel = TrustLevel(requestedPermissions, emptySet(), maximumPermission),
            health = ConnectionHealth.UNKNOWN,
            lastSync = null,
            profileTags = profileTags,
        )
        _connections.update { it + connection }
        return connection
    }

    override fun approve(connectionId: String, approvedBy: String) {
        transition(connectionId, ConnectionStatus.APPROVED) { it.copy(status = ConnectionStatus.APPROVED) }
    }

    override fun reject(connectionId: String, reason: String) {
        transition(connectionId, ConnectionStatus.REJECTED, reason) {
            it.copy(status = ConnectionStatus.REJECTED, trustLevel = TrustLevel.none())
        }
    }

    override fun connect(connectionId: String) {
        transition(connectionId, ConnectionStatus.CONNECTING) { it.copy(status = ConnectionStatus.CONNECTING) }
    }

    override fun markConnected(connectionId: String) {
        transition(connectionId, ConnectionStatus.CONNECTED) {
            it.copy(status = ConnectionStatus.CONNECTED, health = ConnectionHealth.HEALTHY, lastSync = Instant.now())
        }
    }

    override fun markError(connectionId: String, reason: String) {
        transition(connectionId, ConnectionStatus.ERROR, reason) {
            it.copy(status = ConnectionStatus.ERROR, health = ConnectionHealth.UNHEALTHY)
        }
    }

    override fun disconnect(connectionId: String, reason: String?) {
        val current = _connections.value.firstOrNull { it.connectionId == connectionId }
            ?: throw ConnectionOperationError("No connection found with id '$connectionId'.")
        // Idempotent no-op on the two terminal states, same as Sprint-8 -- disconnecting an already-terminal connection isn't an error, it's a no-op.
        if (current.status == ConnectionStatus.REJECTED || current.status == ConnectionStatus.DISCONNECTED) return
        transition(connectionId, ConnectionStatus.DISCONNECTED, reason) {
            it.copy(status = ConnectionStatus.DISCONNECTED, trustLevel = TrustLevel.none(), health = ConnectionHealth.UNKNOWN)
        }
    }

    override fun suspend(connectionId: String, reason: String) {
        transition(connectionId, ConnectionStatus.SUSPENDED, reason) { it.copy(status = ConnectionStatus.SUSPENDED) }
    }

    override fun reconnect(connectionId: String) {
        transition(connectionId, ConnectionStatus.CONNECTING) { it.copy(status = ConnectionStatus.CONNECTING) }
    }

    override fun disableAll(reason: String) {
        _connections.value
            .filter { it.status != ConnectionStatus.REJECTED && it.status != ConnectionStatus.DISCONNECTED }
            .forEach { disconnect(it.connectionId, reason) }
    }

    override fun testConnection(connectionId: String): ConnectionHealth {
        val connection = _connections.value.firstOrNull { it.connectionId == connectionId }
            ?: throw ConnectionOperationError("No connection found with id '$connectionId'.")
        val health = if (connection.status == ConnectionStatus.CONNECTED) ConnectionHealth.HEALTHY else ConnectionHealth.UNKNOWN
        update(connectionId) { it.copy(health = health, lastSync = Instant.now()) }
        return health
    }

    /**
     * The single validation gate every mutating method above routes
     * through. Looks up the current status, checks `newStatus` against
     * `allowedTransitions[current]`, applies `block` only if legal, and
     * emits the resulting ConnectionTransition -- so it is structurally
     * impossible for a state change to happen without either a legal
     * transition or an exception, and impossible for `transitions` to
     * emit something that didn't actually happen to `connections`.
     */
    private fun transition(
        connectionId: String,
        newStatus: ConnectionStatus,
        reason: String? = null,
        block: (Connection) -> Connection,
    ) {
        val current = _connections.value.firstOrNull { it.connectionId == connectionId }
            ?: throw ConnectionOperationError("No connection found with id '$connectionId'.")
        val allowed = allowedTransitions[current.status].orEmpty()
        if (newStatus !in allowed) {
            throw ConnectionOperationError(
                "Cannot move '${current.providerName}' from ${current.status} to $newStatus " +
                    "-- allowed next states are ${if (allowed.isEmpty()) "none (terminal)" else allowed}.",
            )
        }
        update(connectionId, block)
        _transitions.tryEmit(
            ConnectionTransition(connectionId, current.providerName, current.status, newStatus, reason),
        )
    }

    private fun update(connectionId: String, block: (Connection) -> Connection) {
        _connections.update { list -> list.map { if (it.connectionId == connectionId) block(it) else it } }
    }

    /**
     * "AI Provider Stabilization & Truthfulness Audit": a real,
     * confirmed finding, not a hypothetical one -- 8 of these 9 entries
     * were seeded as CONNECTED, but only GitHub and NG Signal Pro have
     * any real backing code anywhere in this app (GitHubStatusProvider,
     * NgSignalProStatusProvider). Calendar, Gmail, Spotify, and Weather
     * have no integration of any kind, anywhere -- there is no code
     * path that could make "CONNECTED" true for them. The "ChatGPT"
     * entry duplicates the real AI Provider system (AIProviderScreen)
     * through a completely separate, unconnected pathway. "ProjectOS"
     * doesn't conceptually belong in a connection list at all -- it's
     * this app's own internal project tracking, not an external system
     * to connect to.
     *
     * Fixed to PENDING_APPROVAL (an existing, already-real status --
     * see Claude's own entry below, unchanged) for everything without
     * real backing, rather than inventing a new status or silently
     * leaving the false claim in place. This directly fixes Mission
     * Control's "connected systems" count from an overclaimed 8 of 9 to
     * an honest 2 of 9.
     *
     * NOT fixed in this pass, documented instead of hidden: even
     * GitHub and NG Signal Pro's CONNECTED status here is a fixed seed
     * value, not dynamically synced with whether the Owner has actually
     * entered a real GitHub token in Settings -- see this sprint's
     * integration report for why that deeper wiring wasn't attempted
     * alongside everything else in this pass.
     */
    private fun seedConnections(): List<Connection> = listOf(
        mockConnection("provider-github", "GitHub", ConnectionStatus.CONNECTED, setOf("work")),
        mockConnection("provider-openai", "ChatGPT", ConnectionStatus.PENDING_APPROVAL, setOf("work")),
        mockConnection("provider-anthropic", "Claude", ConnectionStatus.PENDING_APPROVAL, setOf("work")),
        mockConnection("provider-calendar", "Calendar", ConnectionStatus.PENDING_APPROVAL, setOf("work", "personal")),
        mockConnection("provider-gmail", "Gmail", ConnectionStatus.PENDING_APPROVAL, setOf("personal")),
        mockConnection("provider-spotify", "Spotify", ConnectionStatus.PENDING_APPROVAL, setOf("personal")),
        mockConnection("provider-weather", "Weather", ConnectionStatus.PENDING_APPROVAL, setOf("work", "personal")),
        mockConnection("provider-ngsignal", "NG Signal Pro", ConnectionStatus.CONNECTED, setOf("work")),
        mockConnection("provider-projectos", "ProjectOS", ConnectionStatus.PENDING_APPROVAL, setOf("work")),
    )

    private fun mockConnection(providerId: String, name: String, status: ConnectionStatus, tags: Set<String>) = Connection(
        connectionId = UUID.randomUUID().toString(),
        providerId = providerId,
        providerName = name,
        status = status,
        trustLevel = if (status == ConnectionStatus.CONNECTED) {
            TrustLevel(setOf(PermissionScope.READ, PermissionScope.WRITE), emptySet(), PermissionScope.WRITE)
        } else TrustLevel.none(),
        health = if (status == ConnectionStatus.CONNECTED) ConnectionHealth.HEALTHY else ConnectionHealth.UNKNOWN,
        lastSync = if (status == ConnectionStatus.CONNECTED) Instant.now() else null,
        profileTags = tags,
    )

    companion object {
        /**
         * The full Sprint 9 transition graph. A status absent as a key
         * (or mapped to an empty set) is terminal. This is the ONLY
         * place transition legality is decided -- see `transition()`.
         */
        val allowedTransitions: Map<ConnectionStatus, Set<ConnectionStatus>> = mapOf(
            // DISCONNECTED is included here (in addition to APPROVED/REJECTED) so
            // disableAll() can route every non-terminal connection, pending ones
            // included, through the same transition()/disconnect() path rather
            // than a bulk-write special case -- a pending request is a valid
            // thing for an owner override to withdraw.
            ConnectionStatus.PENDING_APPROVAL to setOf(ConnectionStatus.APPROVED, ConnectionStatus.REJECTED, ConnectionStatus.DISCONNECTED),
            ConnectionStatus.APPROVED to setOf(ConnectionStatus.CONNECTING, ConnectionStatus.DISCONNECTED),
            ConnectionStatus.CONNECTING to setOf(ConnectionStatus.CONNECTED, ConnectionStatus.ERROR, ConnectionStatus.DISCONNECTED),
            ConnectionStatus.CONNECTED to setOf(ConnectionStatus.SUSPENDED, ConnectionStatus.ERROR, ConnectionStatus.DISCONNECTED),
            ConnectionStatus.SUSPENDED to setOf(ConnectionStatus.CONNECTING, ConnectionStatus.DISCONNECTED),
            ConnectionStatus.ERROR to setOf(ConnectionStatus.CONNECTING, ConnectionStatus.DISCONNECTED),
            ConnectionStatus.REJECTED to emptySet(),
            ConnectionStatus.DISCONNECTED to emptySet(),
        )
    }
}

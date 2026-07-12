package com.jarvis.os.app.data.repository

import com.jarvis.os.app.data.model.Connection
import com.jarvis.os.app.data.model.ConnectionHealth
import com.jarvis.os.app.data.model.ConnectionStatus
import com.jarvis.os.app.data.model.PermissionScope
import com.jarvis.os.app.data.model.TrustLevel
import kotlinx.coroutines.flow.MutableStateFlow
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
 * redesign of anything that calls it (every ViewModel in this app
 * depends on `ConnectionRepository`, never on `MockConnectionRepository`
 * directly — see di/RepositoryModule.kt).
 *
 * `MockConnectionRepository` below is real, working, in-memory state
 * management — approve/reject/suspend/disconnect genuinely transition
 * state and genuinely fail on an invalid transition, exactly like the
 * Python ConnectionManager's own rules — it is a faithful behavioral
 * stand-in, not a UI placeholder that always succeeds.
 */
interface ConnectionRepository {
    val connections: StateFlow<List<Connection>>

    fun requestConnection(
        providerId: String,
        providerName: String,
        requestedPermissions: Set<PermissionScope>,
        maximumPermission: PermissionScope,
        profileTags: Set<String> = emptySet(),
    ): Connection

    fun approve(connectionId: String, approvedBy: String)
    fun reject(connectionId: String, reason: String)
    fun markConnected(connectionId: String)
    fun disconnect(connectionId: String, reason: String? = null)
    fun suspend(connectionId: String, reason: String)
    fun reconnect(connectionId: String)
    fun disableAll(reason: String)
    fun testConnection(connectionId: String): ConnectionHealth
}

class ConnectionOperationError(message: String) : Exception(message)

@Singleton
class MockConnectionRepository @Inject constructor() : ConnectionRepository {

    private val _connections = MutableStateFlow(seedConnections())
    override val connections: StateFlow<List<Connection>> = _connections.asStateFlow()

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
        transition(connectionId, expected = ConnectionStatus.PENDING_APPROVAL) {
            it.copy(status = ConnectionStatus.APPROVED)
        }
    }

    override fun reject(connectionId: String, reason: String) {
        transition(connectionId, expected = ConnectionStatus.PENDING_APPROVAL) {
            it.copy(status = ConnectionStatus.REJECTED, trustLevel = TrustLevel.none())
        }
    }

    override fun markConnected(connectionId: String) {
        transition(connectionId, expected = ConnectionStatus.APPROVED) {
            it.copy(status = ConnectionStatus.CONNECTED, health = ConnectionHealth.HEALTHY, lastSync = Instant.now())
        }
    }

    override fun disconnect(connectionId: String, reason: String?) {
        update(connectionId) {
            if (it.status == ConnectionStatus.REJECTED || it.status == ConnectionStatus.DISCONNECTED) it
            else it.copy(status = ConnectionStatus.DISCONNECTED, trustLevel = TrustLevel.none(), health = ConnectionHealth.UNKNOWN)
        }
    }

    override fun suspend(connectionId: String, reason: String) {
        transition(connectionId, expected = ConnectionStatus.CONNECTED) {
            it.copy(status = ConnectionStatus.SUSPENDED)
        }
    }

    override fun reconnect(connectionId: String) {
        transition(connectionId, expected = ConnectionStatus.SUSPENDED) {
            it.copy(status = ConnectionStatus.APPROVED)
        }
    }

    override fun disableAll(reason: String) {
        _connections.update { list ->
            list.map {
                if (it.status == ConnectionStatus.REJECTED || it.status == ConnectionStatus.DISCONNECTED) it
                else it.copy(status = ConnectionStatus.DISCONNECTED, trustLevel = TrustLevel.none(), health = ConnectionHealth.UNKNOWN)
            }
        }
    }

    override fun testConnection(connectionId: String): ConnectionHealth {
        val connection = _connections.value.firstOrNull { it.connectionId == connectionId }
            ?: throw ConnectionOperationError("No connection found with id '$connectionId'.")
        val health = if (connection.status == ConnectionStatus.CONNECTED) ConnectionHealth.HEALTHY else ConnectionHealth.UNKNOWN
        update(connectionId) { it.copy(health = health, lastSync = Instant.now()) }
        return health
    }

    private fun transition(connectionId: String, expected: ConnectionStatus, block: (Connection) -> Connection) {
        val current = _connections.value.firstOrNull { it.connectionId == connectionId }
            ?: throw ConnectionOperationError("No connection found with id '$connectionId'.")
        if (current.status != expected) {
            throw ConnectionOperationError(
                "Cannot perform this action: status is '${current.status}', expected '$expected'.",
            )
        }
        update(connectionId, block)
    }

    private fun update(connectionId: String, block: (Connection) -> Connection) {
        _connections.update { list -> list.map { if (it.connectionId == connectionId) block(it) else it } }
    }

    private fun seedConnections(): List<Connection> = listOf(
        mockConnection("provider-github", "GitHub", ConnectionStatus.CONNECTED, setOf("work")),
        mockConnection("provider-openai", "ChatGPT", ConnectionStatus.CONNECTED, setOf("work")),
        mockConnection("provider-anthropic", "Claude", ConnectionStatus.PENDING_APPROVAL, setOf("work")),
        mockConnection("provider-calendar", "Calendar", ConnectionStatus.CONNECTED, setOf("work", "personal")),
        mockConnection("provider-gmail", "Gmail", ConnectionStatus.CONNECTED, setOf("personal")),
        mockConnection("provider-spotify", "Spotify", ConnectionStatus.CONNECTED, setOf("personal")),
        mockConnection("provider-weather", "Weather", ConnectionStatus.CONNECTED, setOf("work", "personal")),
        mockConnection("provider-ngsignal", "NG Signal Pro", ConnectionStatus.CONNECTED, setOf("work")),
        mockConnection("provider-projectos", "ProjectOS", ConnectionStatus.CONNECTED, setOf("work")),
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
}

package com.jarvis.os.app.data.model

import java.time.Instant

/**
 * Mirrors jarvis.connections.models (Sprint-6, Python) field-for-field
 * and name-for-name wherever possible. This is deliberate: the day a
 * real backend bridge exists, the JSON a real ConnectionManager API
 * would serialize should deserialize into this class with no field
 * renaming — the mapping work happens once, here, not scattered through
 * every screen that touches a Connection.
 */
enum class PermissionScope { READ, WRITE, EXECUTE, HIGH_RISK_ACTIONS }

/**
 * Sprint 9: the 7-state validated machine the sprint calls for --
 * CONNECTING (an in-flight connect attempt between an approved
 * connection and a confirmed one) and ERROR (a connect attempt or a
 * live connection that failed) replace the old flat FAILED, which had
 * no place in the transition graph and was never actually reachable.
 * REJECTED is kept alongside DISCONNECTED as a second terminal state
 * because they mean different things to the owner (an explicit "no"
 * at approval time vs. a connection that was live and was taken down)
 * and Sprint 8's UI already surfaces that distinction -- collapsing
 * them would be a behavior change this sprint's "do not redesign"
 * rule argues against. See ConnectionRepository.allowedTransitions
 * for the full graph.
 */
enum class ConnectionStatus { PENDING_APPROVAL, APPROVED, CONNECTING, CONNECTED, SUSPENDED, ERROR, DISCONNECTED, REJECTED }

enum class ConnectionHealth { HEALTHY, DEGRADED, UNHEALTHY, UNKNOWN }

data class TrustLevel(
    val grantedPermissions: Set<PermissionScope>,
    val approvalRequiredFor: Set<PermissionScope>,
    val maximumPermission: PermissionScope,
) {
    companion object {
        fun none() = TrustLevel(emptySet(), emptySet(), PermissionScope.READ)
    }
}

data class Connection(
    val connectionId: String,
    val providerId: String,
    val providerName: String,
    val status: ConnectionStatus,
    val trustLevel: TrustLevel,
    val health: ConnectionHealth,
    val lastSync: Instant?,
    val profileTags: Set<String>,
)

/** Part 11's four Connection Profiles — mirrors jarvis.connections.profiles.ConnectionProfile exactly. */
enum class ConnectionProfile(val label: String) {
    WORK("Work"),
    PERSONAL("Personal"),
    OFFLINE("Offline"),
    PRIVACY("Privacy"),
}

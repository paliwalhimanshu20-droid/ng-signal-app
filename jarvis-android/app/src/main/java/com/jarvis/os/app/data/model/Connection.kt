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

enum class ConnectionStatus { PENDING_APPROVAL, APPROVED, CONNECTED, SUSPENDED, DISCONNECTED, REJECTED, FAILED }

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

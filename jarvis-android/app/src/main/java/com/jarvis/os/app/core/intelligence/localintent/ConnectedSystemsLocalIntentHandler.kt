package com.jarvis.os.app.core.intelligence.localintent

import com.jarvis.os.app.data.repository.ConnectionRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connected Systems: answers "list my connections", "is <provider> connected", "connection
 * status" directly from [ConnectionRepository] -- the same live state the Connections screen
 * reads, per that repository's own "JarvisCore is the sole cross-repository coordinator, every
 * other reader is a plain reader" contract (this handler reads only, never mutates).
 */
@Singleton
class ConnectedSystemsLocalIntentHandler @Inject constructor(
    private val connections: ConnectionRepository,
) : LocalIntentHandler {

    override val domain = LocalServiceDomain.CONNECTED_SYSTEMS

    override suspend fun tryHandle(text: String): String? {
        val lower = text.lowercase()
        if (KEYWORDS.none { it in lower }) return null

        val all = connections.connections.value
        if (all.isEmpty()) return "No connections have been requested yet."

        // "is X connected" -- scope to whichever provider name the owner actually named.
        val named = all.firstOrNull { c -> lower.contains(c.providerName.lowercase()) }
        if (named != null) {
            return "${named.providerName} is currently ${named.status} (health: ${named.health})."
        }

        return "${all.size} connection(s): " +
            all.joinToString("; ") { c -> "${c.providerName} (${c.status})" } + "."
    }

    companion object {
        private val KEYWORDS = setOf(
            "connection", "connections", "connected systems", "is connected", "am i connected",
        )
    }
}

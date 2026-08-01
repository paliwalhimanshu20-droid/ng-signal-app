package com.jarvis.os.app.core.intelligence.localintent

import com.jarvis.os.app.core.chat.AiRouter
import com.jarvis.os.app.data.model.ConnectionStatus
import com.jarvis.os.app.data.repository.ConnectionRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mission Control: answers "which AI provider is active", "system status", "what's my setup
 * look like" directly from [AiRouter] and [ConnectionRepository] -- the exact same live state
 * MissionControlScreen itself renders (see AiRouter.activeProviderId StateFlow combine there),
 * so this handler's answer can never drift from what the Mission Control screen shows.
 */
@Singleton
class MissionControlLocalIntentHandler @Inject constructor(
    private val aiRouter: AiRouter,
    private val connections: ConnectionRepository,
) : LocalIntentHandler {

    override val domain = LocalServiceDomain.MISSION_CONTROL

    override suspend fun tryHandle(text: String): LocalIntentAnswer? = answer(text)?.let { LocalIntentAnswer(it) }

    private suspend fun answer(text: String): String? {
        val lower = text.lowercase()
        if (KEYWORDS.none { it in lower }) return null

        val active = aiRouter.active
        val connected = connections.connections.value.count { it.status == ConnectionStatus.CONNECTED }
        val total = connections.connections.value.size

        return "Mission Control: active AI provider is ${active.displayName} (${active.id}). " +
            "$connected of $total connections are currently CONNECTED. " +
            "${aiRouter.available.size} AI provider(s) bound in total."
    }

    companion object {
        private val KEYWORDS = setOf(
            "mission control", "system status", "active ai provider", "which ai provider",
            "which provider is active", "current provider", "who's driving", "whos driving",
        )
    }
}

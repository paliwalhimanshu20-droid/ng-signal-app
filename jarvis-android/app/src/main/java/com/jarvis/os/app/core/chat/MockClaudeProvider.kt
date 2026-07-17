package com.jarvis.os.app.core.chat

import com.jarvis.os.app.data.model.AiCapability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PR4 / Sprint 11 "Coordinate Claude": a second ChatProvider so
 * AiRouter.routeFor and MultiAiCoordinator have more than one real
 * candidate to choose between -- the honest gap this closes is stated
 * in AiRouter's own docstring ("nothing here to switch between yet").
 *
 * Still no real network call to Anthropic's API specifically --
 * OpenAiCompatibleChatProvider (Sprint 12) is this codebase's one real
 * provider today, and it can already point at any OpenAI-compatible
 * endpoint the Owner configures. This class stays bound as a distinct,
 * honestly-labeled option in the provider picker rather than being
 * folded into that one.
 *
 * "JARVIS Experience Transformation" (Phase 0): this class's reply
 * text used to name its own implementation details (ConnectionRepository,
 * "capability-tagged placeholder") directly to the Owner -- fixed to
 * speak naturally instead, same as MockChatProvider's own reply.
 * Internal architecture stays internal; only what a real conversational
 * partner would actually say is user-facing now.
 */
@Singleton
class MockClaudeProvider @Inject constructor() : ChatProvider {
    override val id: String = "claude"
    override val displayName: String = "Claude (not yet connected)"

    override val capabilities: Set<AiCapability> = setOf(
        AiCapability.GENERAL_CHAT,
        AiCapability.REASONING,
        AiCapability.LONG_CONTEXT,
        AiCapability.CODE_GENERATION,
    )

    override fun sendMessage(sessionId: String, text: String): Flow<ChatChunk> = flow {
        val reply = "I'm not connected to Claude yet -- add an API key under Settings, AI Provider, " +
            "and we can have a real conversation together. For now, here's what I heard: \"$text\"."
        emit(ChatChunk.Complete(reply))
    }
}

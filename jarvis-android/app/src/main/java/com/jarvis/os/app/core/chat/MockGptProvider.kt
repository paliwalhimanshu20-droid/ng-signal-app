package com.jarvis.os.app.core.chat

import com.jarvis.os.app.data.model.AiCapability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PR4 / Sprint 11 "Coordinate ChatGPT" -- same honesty and swap-point
 * shape as MockClaudeProvider (see its docstring); the only difference
 * that matters to routing is this provider's capability set, which
 * deliberately overlaps GENERAL_CHAT with the other two but claims
 * TOOL_USE and VISION where MockClaudeProvider claims REASONING and
 * LONG_CONTEXT -- so AiRouter.routeFor and MultiAiCoordinator's
 * expertise-based routing (Sprint 11) have a real, non-degenerate
 * choice to make between three candidates, not three interchangeable
 * ones.
 */
@Singleton
class MockGptProvider @Inject constructor() : ChatProvider {
    override val id: String = "gpt"
    override val displayName: String = "ChatGPT (not yet connected)"

    override val capabilities: Set<AiCapability> = setOf(
        AiCapability.GENERAL_CHAT,
        AiCapability.TOOL_USE,
        AiCapability.VISION,
    )

    override fun sendMessage(sessionId: String, text: String): Flow<ChatChunk> = flow {
        val reply = "Routed to the ChatGPT provider slot for: \"$text\". No live OpenAI API " +
            "call happens yet -- placeholder tagged for tool-use and vision routing " +
            "until a real connection is approved and an API key is supplied."
        emit(ChatChunk.Complete(reply))
    }
}

package com.jarvis.os.app.core.chat

import com.jarvis.os.app.data.model.AiCapability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PR4 / Sprint 11 "Coordinate ChatGPT" -- same shape as
 * MockClaudeProvider (see its docstring); the only difference that
 * matters to routing is this provider's capability set, which
 * deliberately overlaps GENERAL_CHAT with the other two but claims
 * TOOL_USE and VISION where MockClaudeProvider claims REASONING and
 * LONG_CONTEXT -- so AiRouter.routeFor and MultiAiCoordinator's
 * expertise-based routing (Sprint 11) have a real, non-degenerate
 * choice to make between three candidates, not three interchangeable
 * ones.
 *
 * "JARVIS Experience Transformation" (Phase 0): reply text fixed to
 * speak naturally, same as MockClaudeProvider's own fix -- see that
 * file's docstring.
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

    override fun sendMessage(sessionId: String, prompt: ChatPrompt): Flow<ChatChunk> = flow {
        // "Conversation Replay Bug Fix": echoes prompt.userMessage only -- see MockChatProvider's
        // own docstring on this same change for the exact bug this prevents.
        val reply = "I'm not connected to ChatGPT yet -- add an API key under Settings, AI Provider, " +
            "and we can have a real conversation together. For now, here's what I heard: \"${prompt.userMessage}\"."
        emit(ChatChunk.Complete(reply))
    }
}

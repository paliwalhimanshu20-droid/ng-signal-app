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
 * Still no real network call to Anthropic's API -- that requires a
 * securely stored API key and goes through ConnectionRepository's
 * owner-approval flow (Sprint-6/Sprint 9's whole point), which no
 * screen collects yet. This mock is deliberately honest about that in
 * its own reply text rather than silently pretending. Swapping in a
 * real Anthropic-backed implementation later is: implement
 * ChatProvider with a real HTTP client, delete this class or leave it
 * bound alongside it, done -- same swap point MockChatProvider already
 * established.
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
        val reply = "Routed to the Claude provider slot for: \"$text\". No live Anthropic API " +
            "call happens yet -- this provider is a capability-tagged placeholder " +
            "(reasoning, long-context, code) until a real connection is approved " +
            "via ConnectionRepository and an API key is supplied."
        emit(ChatChunk.Complete(reply))
    }
}

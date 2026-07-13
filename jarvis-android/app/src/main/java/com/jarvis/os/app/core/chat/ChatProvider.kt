package com.jarvis.os.app.core.chat

import kotlinx.coroutines.flow.Flow

/**
 * Sprint-8: the seam every future AI provider — ChatGPT, Claude,
 * Gemini, a local on-device model, or JARVIS OS's own AI Coordination
 * Layer once it is network-exposed — implements. ChatViewModel and
 * ChatScreen depend ONLY on this interface, never on a concrete
 * provider. Adding a real provider later is: (1) implement this
 * interface, (2) add one @Binds @IntoSet line in ChatProviderModule.
 * Nothing in the UI layer changes — the same "swap point" pattern
 * already used for every repository in this codebase (see
 * RepositoryModule.kt's docstring).
 */
interface ChatProvider {
    /** Stable identifier, e.g. "mock", "claude", "gpt". Used for selection once more than one provider exists — see ChatProviderRegistry. */
    val id: String

    /** Human-readable name for a future provider-picker UI. */
    val displayName: String

    /**
     * Streaming-ready: returns a Flow of chunks rather than a single
     * suspend-and-return String, so a real streaming provider can emit
     * tokens as they arrive rather than the whole reply at once. A
     * non-streaming provider is still a valid, if degenerate, user of
     * this same interface — see MockChatProvider — not a separate code
     * path that would need to be thrown away when a real streaming
     * provider is added.
     */
    fun sendMessage(sessionId: String, text: String): Flow<ChatChunk>
}

sealed interface ChatChunk {
    /** One piece of a streaming reply. A provider emits zero or more of these before Complete. */
    data class Token(val text: String) : ChatChunk

    /** The final, complete reply text — always emitted exactly once, whether or not any Token was emitted first. */
    data class Complete(val fullText: String) : ChatChunk

    data class Error(val message: String) : ChatChunk
}

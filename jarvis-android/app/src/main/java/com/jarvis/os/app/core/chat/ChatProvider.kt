package com.jarvis.os.app.core.chat

import com.jarvis.os.app.data.model.AiCapability
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
    /** Stable identifier, e.g. "mock", "claude", "gpt". Used for selection once more than one provider exists — see AiRouter. */
    val id: String

    /** Human-readable name for a future provider-picker UI. */
    val displayName: String

    /**
     * PR4: what this provider is good for, used by AiRouter.routeFor to
     * pick a provider by required capability rather than always using
     * whatever switchProvider last set. A provider with no particular
     * strength still declares GENERAL_CHAT so it remains a valid
     * fallback -- see MockChatProvider.
     */
    val capabilities: Set<AiCapability>

    /**
     * Streaming-ready: returns a Flow of chunks rather than a single
     * suspend-and-return String, so a real streaming provider can emit
     * tokens as they arrive rather than the whole reply at once. A
     * non-streaming provider is still a valid, if degenerate, user of
     * this same interface — see MockChatProvider — not a separate code
     * path that would need to be thrown away when a real streaming
     * provider is added.
     *
     * "Conversation Replay Bug Fix": [prompt] replaces what used to be a single flat `text: String`
     * — see [ChatPrompt]'s own docstring for exactly why that was the root cause of JARVIS
     * answering with recalled history instead of the Owner's actual question. Every implementation
     * of this method MUST send [ChatPrompt.userMessage] as the real, current question — verbatim,
     * never prefixed or blended with [ChatPrompt.memory] / [ChatPrompt.recentChat] — and, for any
     * provider that calls a real model, MUST send memory/recent chat (when present) as its own
     * separate background turn via [PromptBuilder.backgroundContextBlock], not folded into the
     * user turn.
     */
    fun sendMessage(sessionId: String, prompt: ChatPrompt): Flow<ChatChunk>

    /**
     * "Offline Completion" milestone: true if this provider is actually ready to be called right
     * now -- for a real provider (Anthropic/Gemini/Groq/OpenAiCompatible) that means a saved API
     * key exists (see each one's own override, checking its `ApiKeyStore.currentConfig() != null`
     * -- the exact same condition that currently makes [sendMessage] emit a
     * `ChatChunk.Error("No <provider> API key is configured...")`). Lets
     * `ChatRepository.isAiProviderReady()` / JarvisCore check readiness WITHOUT calling
     * [sendMessage] and parsing/guessing from a [ChatChunk.Error] -- matching this codebase's "no
     * fake success, no guessing from text" discipline elsewhere (see ChatMessage.sourceToolIds's
     * own docstring for the same reasoning applied to tool provenance). Defaults to true, which is
     * correct for every mock provider (MockChatProvider/MockClaudeProvider/MockGptProvider) --
     * none of them need a key to produce a reply, so none of them override this.
     */
    fun isConfigured(): Boolean = true
}

sealed interface ChatChunk {
    /** One piece of a streaming reply. A provider emits zero or more of these before Complete. */
    data class Token(val text: String) : ChatChunk

    /** The final, complete reply text — always emitted exactly once, whether or not any Token was emitted first. */
    data class Complete(val fullText: String) : ChatChunk

    data class Error(val message: String) : ChatChunk
}

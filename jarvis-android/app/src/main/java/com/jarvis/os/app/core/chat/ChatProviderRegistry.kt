package com.jarvis.os.app.core.chat

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint-8: the extensibility point Requirement 7 asks for — "the Chat
 * layer must expose interfaces so ChatGPT, Claude, Gemini, local models
 * and future providers can be added without changing UI code." Today
 * exactly one provider (MockChatProvider) is bound via Hilt's
 * multibinding (see ChatProviderModule), so [active] always resolves
 * to it, unambiguously.
 *
 * Adding a second, real provider later means: (1) implement
 * ChatProvider, (2) add one @Binds @IntoSet line in
 * ChatProviderModule. What [active] should mean once more than one
 * provider is bound — a Settings preference is the obvious answer — is
 * a real product decision deliberately left for that sprint, not
 * guessed at here with no UI to back it. Nothing above this class —
 * ChatViewModel, ChatScreen — changes when that decision is made.
 */
@Singleton
class ChatProviderRegistry @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards ChatProvider>,
) {
    val available: List<ChatProvider> get() = providers.toList()

    /** With exactly one provider bound this sprint, this is unambiguous. Selecting among multiple providers is future scope — see class docstring. */
    val active: ChatProvider
        get() = providers.firstOrNull() ?: error("No ChatProvider bound — check ChatProviderModule")
}

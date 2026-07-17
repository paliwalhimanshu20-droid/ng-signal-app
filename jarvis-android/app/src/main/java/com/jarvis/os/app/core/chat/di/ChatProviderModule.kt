package com.jarvis.os.app.core.chat.di

import com.jarvis.os.app.core.chat.AnthropicChatProvider
import com.jarvis.os.app.core.chat.ChatProvider
import com.jarvis.os.app.core.chat.GeminiChatProvider
import com.jarvis.os.app.core.chat.MockChatProvider
import com.jarvis.os.app.core.chat.MockClaudeProvider
import com.jarvis.os.app.core.chat.MockGptProvider
import com.jarvis.os.app.core.chat.OpenAiCompatibleChatProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Sprint-8: the actual "add a provider without touching the UI" swap
 * point. PR4 exercises exactly the pattern this file's original
 * docstring promised: two more @Binds @IntoSet lines, nothing else in
 * this file or the Chat feature package changed, and AiRouter now has
 * three real candidates to route between instead of one.
 *
 * Sprint 12 "Real AI Conversation": one more entry --
 * OpenAiCompatibleChatProvider is the first genuinely real (not Mock)
 * provider in this codebase, same one-line swap-point pattern. It
 * remains bound alongside the three Mocks rather than replacing them --
 * see that class's own docstring for why it degrades to an honest
 * error message (not a crash, not a silent fallback) when the Owner
 * hasn't configured a real API key yet.
 *
 * "Universal Connection Ecosystem -- Phase 1": GeminiChatProvider is
 * the second real, network-calling provider, bound the same one-line
 * way -- AiRouter now has two genuinely live candidates plus three
 * honest offline fallbacks to route between.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ChatProviderModule {
    @Binds
    @IntoSet
    abstract fun bindMockChatProvider(impl: MockChatProvider): ChatProvider

    @Binds
    @IntoSet
    abstract fun bindMockClaudeProvider(impl: MockClaudeProvider): ChatProvider

    @Binds
    @IntoSet
    abstract fun bindMockGptProvider(impl: MockGptProvider): ChatProvider

    @Binds
    @IntoSet
    abstract fun bindOpenAiCompatibleChatProvider(impl: OpenAiCompatibleChatProvider): ChatProvider

    @Binds
    @IntoSet
    abstract fun bindGeminiChatProvider(impl: GeminiChatProvider): ChatProvider

    @Binds
    @IntoSet
    abstract fun bindAnthropicChatProvider(impl: AnthropicChatProvider): ChatProvider
}

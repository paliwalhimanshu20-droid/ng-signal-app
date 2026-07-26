package com.jarvis.os.app.di

import com.jarvis.os.app.core.chat.AnthropicChatProvider
import com.jarvis.os.app.core.chat.ChatProvider
import com.jarvis.os.app.core.chat.GeminiChatProvider
import com.jarvis.os.app.core.chat.GroqChatProvider
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
 * The swap point [ChatProvider]'s own docstring promises: "Adding a real provider later is
 * (1) implement this interface, (2) add one @Binds @IntoSet line here." AiRouter injects
 * `Set<ChatProvider>` (see AiRouter.kt) — without this module that set has no contributors and
 * every class downstream of AiRouter (WatchTower agents, MultiAiCoordinator, JarvisCore, every
 * ViewModel that reaches JarvisCore) fails to build. This module was documented everywhere
 * (ChatProvider.kt, BuiltInTools.kt, etc.) but never actually created in this codebase.
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
    abstract fun bindAnthropicChatProvider(impl: AnthropicChatProvider): ChatProvider

    @Binds
    @IntoSet
    abstract fun bindGeminiChatProvider(impl: GeminiChatProvider): ChatProvider

    @Binds
    @IntoSet
    abstract fun bindGroqChatProvider(impl: GroqChatProvider): ChatProvider

    @Binds
    @IntoSet
    abstract fun bindOpenAiCompatibleChatProvider(impl: OpenAiCompatibleChatProvider): ChatProvider
}

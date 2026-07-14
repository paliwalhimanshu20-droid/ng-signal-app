package com.jarvis.os.app.core.chat.di

import com.jarvis.os.app.core.chat.ChatProvider
import com.jarvis.os.app.core.chat.MockChatProvider
import com.jarvis.os.app.core.chat.MockClaudeProvider
import com.jarvis.os.app.core.chat.MockGptProvider
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
}

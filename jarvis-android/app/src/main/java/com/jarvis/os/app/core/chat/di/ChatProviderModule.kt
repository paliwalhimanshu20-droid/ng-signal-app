package com.jarvis.os.app.core.chat.di

import com.jarvis.os.app.core.chat.ChatProvider
import com.jarvis.os.app.core.chat.MockChatProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Sprint-8: the actual "add a provider without touching the UI" swap
 * point. A future PR adding a real provider adds one @Binds @IntoSet
 * function here — nothing else in this file, and nothing in the Chat
 * feature package, needs to change.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ChatProviderModule {
    @Binds
    @IntoSet
    abstract fun bindMockChatProvider(impl: MockChatProvider): ChatProvider
}

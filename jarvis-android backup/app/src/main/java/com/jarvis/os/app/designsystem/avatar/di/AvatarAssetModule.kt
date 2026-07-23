package com.jarvis.os.app.designsystem.avatar.di

import com.jarvis.os.app.designsystem.avatar.AvatarAssetProvider
import com.jarvis.os.app.designsystem.avatar.DrawableAvatarAssetProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** "JARVIS Living Avatar" sprint: swapping in a real asset source later (or a remote-fetched one) is a new class implementing AvatarAssetProvider plus a change to this one line -- same pattern as VoiceModule/ChatProviderModule. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AvatarAssetModule {
    @Binds
    @Singleton
    abstract fun bindAvatarAssetProvider(impl: DrawableAvatarAssetProvider): AvatarAssetProvider
}

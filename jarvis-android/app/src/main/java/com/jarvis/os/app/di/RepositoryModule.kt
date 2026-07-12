package com.jarvis.os.app.di

import com.jarvis.os.app.data.repository.ApprovalRepository
import com.jarvis.os.app.data.repository.ChatRepository
import com.jarvis.os.app.data.repository.ConnectionRepository
import com.jarvis.os.app.data.repository.HomeAutomationRepository
import com.jarvis.os.app.data.repository.MemoryRepository
import com.jarvis.os.app.data.repository.MockApprovalRepository
import com.jarvis.os.app.data.repository.MockChatRepository
import com.jarvis.os.app.data.repository.MockConnectionRepository
import com.jarvis.os.app.data.repository.MockHomeAutomationRepository
import com.jarvis.os.app.data.repository.MockMemoryRepository
import com.jarvis.os.app.data.repository.MockProjectRepository
import com.jarvis.os.app.data.repository.ProjectRepository
import com.jarvis.os.app.data.settings.DataStoreSettingsRepository
import com.jarvis.os.app.data.settings.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * THE single swap point for a future real backend bridge (see
 * ConnectionRepository.kt's module docstring): every `@Binds` below
 * pairs an interface with today's Mock* implementation. Replacing
 * `MockConnectionRepository::class` with `RemoteConnectionRepository::class`
 * on the day a real API exists is the entire migration for that
 * repository — no ViewModel, no screen, no navigation code changes,
 * because everything above this module depends only on the interface.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindConnectionRepository(impl: MockConnectionRepository): ConnectionRepository

    @Binds
    @Singleton
    abstract fun bindApprovalRepository(impl: MockApprovalRepository): ApprovalRepository

    @Binds
    @Singleton
    abstract fun bindProjectRepository(impl: MockProjectRepository): ProjectRepository

    @Binds
    @Singleton
    abstract fun bindMemoryRepository(impl: MockMemoryRepository): MemoryRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: MockChatRepository): ChatRepository

    @Binds
    @Singleton
    abstract fun bindHomeAutomationRepository(impl: MockHomeAutomationRepository): HomeAutomationRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
}

package com.jarvis.os.app.di

import com.jarvis.os.app.core.memory.AgentMemory
import com.jarvis.os.app.core.memory.AgentMemoryImpl
import com.jarvis.os.app.core.memory.ConversationMemory
import com.jarvis.os.app.core.memory.ConversationMemoryImpl
import com.jarvis.os.app.core.memory.PersonalMemory
import com.jarvis.os.app.core.memory.PersonalMemoryImpl
import com.jarvis.os.app.core.memory.ProjectMemory
import com.jarvis.os.app.core.memory.ProjectMemoryImpl
import com.jarvis.os.app.data.repository.ApprovalRepository
import com.jarvis.os.app.data.repository.AuditRepository
import com.jarvis.os.app.data.repository.ChatRepository
import com.jarvis.os.app.data.repository.ConnectionRepository
import com.jarvis.os.app.data.repository.HomeAutomationRepository
import com.jarvis.os.app.data.repository.MemoryRepository
import com.jarvis.os.app.data.repository.MockApprovalRepository
import com.jarvis.os.app.data.repository.MockAuditRepository
import com.jarvis.os.app.data.repository.MockChatRepository
import com.jarvis.os.app.data.repository.MockConnectionRepository
import com.jarvis.os.app.data.repository.MockHomeAutomationRepository
import com.jarvis.os.app.data.repository.MockMemoryRepository
import com.jarvis.os.app.data.repository.MockNotificationRepository
import com.jarvis.os.app.data.settings.ApiKeyStore
import com.jarvis.os.app.data.settings.EncryptedApiKeyStore
import com.jarvis.os.app.data.repository.RealNgSignalProStatusProvider
import com.jarvis.os.app.data.repository.MockProjectRepository
import com.jarvis.os.app.data.repository.MockToolRepository
import com.jarvis.os.app.data.repository.NgSignalProStatusProvider
import com.jarvis.os.app.data.repository.NotificationRepository
import com.jarvis.os.app.data.repository.ProjectRepository
import com.jarvis.os.app.data.repository.ToolRepository
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
    abstract fun bindNotificationRepository(impl: MockNotificationRepository): NotificationRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    // --- Sprint 10: Memory views (see core/memory/MemoryInterfaces.kt for why these are four separate classes, not one) ---

    @Binds
    @Singleton
    abstract fun bindConversationMemory(impl: ConversationMemoryImpl): ConversationMemory

    @Binds
    @Singleton
    abstract fun bindProjectMemory(impl: ProjectMemoryImpl): ProjectMemory

    @Binds
    @Singleton
    abstract fun bindPersonalMemory(impl: PersonalMemoryImpl): PersonalMemory

    @Binds
    @Singleton
    abstract fun bindAgentMemory(impl: AgentMemoryImpl): AgentMemory

    @Binds
    @Singleton
    abstract fun bindToolRepository(impl: MockToolRepository): ToolRepository

    @Binds
    @Singleton
    abstract fun bindAuditRepository(impl: MockAuditRepository): AuditRepository

    @Binds
    @Singleton
    abstract fun bindNgSignalProStatusProvider(impl: RealNgSignalProStatusProvider): NgSignalProStatusProvider

    @Binds
    @Singleton
    abstract fun bindApiKeyStore(impl: EncryptedApiKeyStore): ApiKeyStore

    @Binds
    @Singleton
    abstract fun bindGitHubTokenStore(impl: com.jarvis.os.app.data.settings.EncryptedGitHubTokenStore): com.jarvis.os.app.data.settings.GitHubTokenStore

    @Binds
    @Singleton
    abstract fun bindGitHubStatusProvider(impl: com.jarvis.os.app.data.repository.RealGitHubStatusProvider): com.jarvis.os.app.data.repository.GitHubStatusProvider

    @Binds
    @Singleton
    abstract fun bindStreamlitStatusProvider(impl: com.jarvis.os.app.data.repository.RealStreamlitStatusProvider): com.jarvis.os.app.data.repository.StreamlitStatusProvider

    @Binds
    @Singleton
    abstract fun bindGeminiKeyStore(impl: com.jarvis.os.app.data.settings.EncryptedGeminiKeyStore): com.jarvis.os.app.data.settings.GeminiKeyStore

    @Binds
    @Singleton
    abstract fun bindAnthropicKeyStore(impl: com.jarvis.os.app.data.settings.EncryptedAnthropicKeyStore): com.jarvis.os.app.data.settings.AnthropicKeyStore

    @Binds
    @Singleton
    abstract fun bindGroqKeyStore(impl: com.jarvis.os.app.data.settings.EncryptedGroqKeyStore): com.jarvis.os.app.data.settings.GroqKeyStore

    @Binds
    @Singleton
    abstract fun bindPreferredProviderStore(impl: com.jarvis.os.app.data.settings.SharedPrefsPreferredProviderStore): com.jarvis.os.app.data.settings.PreferredProviderStore
}

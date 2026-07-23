package com.jarvis.os.app.feature.settings;

import com.jarvis.os.app.core.chat.AiRouter;
import com.jarvis.os.app.core.chat.AnthropicChatProvider;
import com.jarvis.os.app.core.chat.GeminiChatProvider;
import com.jarvis.os.app.core.chat.GroqChatProvider;
import com.jarvis.os.app.core.chat.OpenAiCompatibleChatProvider;
import com.jarvis.os.app.core.security.GoogleAuthManager;
import com.jarvis.os.app.data.repository.GitHubStatusProvider;
import com.jarvis.os.app.data.repository.GoogleWorkspaceStatusProvider;
import com.jarvis.os.app.data.repository.NgSignalProStatusProvider;
import com.jarvis.os.app.data.repository.StreamlitStatusProvider;
import com.jarvis.os.app.data.settings.AnthropicKeyStore;
import com.jarvis.os.app.data.settings.ApiKeyStore;
import com.jarvis.os.app.data.settings.GeminiKeyStore;
import com.jarvis.os.app.data.settings.GitHubTokenStore;
import com.jarvis.os.app.data.settings.GoogleWorkspaceTokenStore;
import com.jarvis.os.app.data.settings.GroqKeyStore;
import com.jarvis.os.app.data.settings.SettingsRepository;
import com.jarvis.os.app.data.settings.StreamlitDeploymentStore;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class SettingsViewModel_Factory implements Factory<SettingsViewModel> {
  private final Provider<SettingsRepository> repositoryProvider;

  private final Provider<ApiKeyStore> apiKeyStoreProvider;

  private final Provider<AiRouter> aiRouterProvider;

  private final Provider<GeminiKeyStore> geminiKeyStoreProvider;

  private final Provider<AnthropicKeyStore> anthropicKeyStoreProvider;

  private final Provider<GroqKeyStore> groqKeyStoreProvider;

  private final Provider<GitHubTokenStore> gitHubTokenStoreProvider;

  private final Provider<GitHubStatusProvider> gitHubStatusProvider;

  private final Provider<NgSignalProStatusProvider> ngSignalProStatusProvider;

  private final Provider<StreamlitDeploymentStore> streamlitDeploymentStoreProvider;

  private final Provider<StreamlitStatusProvider> streamlitStatusProvider;

  private final Provider<GoogleWorkspaceTokenStore> googleWorkspaceTokenStoreProvider;

  private final Provider<GoogleWorkspaceStatusProvider> googleWorkspaceStatusProvider;

  private final Provider<GoogleAuthManager> googleAuthManagerProvider;

  private final Provider<GeminiChatProvider> geminiChatProvider;

  private final Provider<OpenAiCompatibleChatProvider> openAiChatProvider;

  private final Provider<AnthropicChatProvider> anthropicChatProvider;

  private final Provider<GroqChatProvider> groqChatProvider;

  public SettingsViewModel_Factory(Provider<SettingsRepository> repositoryProvider,
      Provider<ApiKeyStore> apiKeyStoreProvider, Provider<AiRouter> aiRouterProvider,
      Provider<GeminiKeyStore> geminiKeyStoreProvider,
      Provider<AnthropicKeyStore> anthropicKeyStoreProvider,
      Provider<GroqKeyStore> groqKeyStoreProvider,
      Provider<GitHubTokenStore> gitHubTokenStoreProvider,
      Provider<GitHubStatusProvider> gitHubStatusProvider,
      Provider<NgSignalProStatusProvider> ngSignalProStatusProvider,
      Provider<StreamlitDeploymentStore> streamlitDeploymentStoreProvider,
      Provider<StreamlitStatusProvider> streamlitStatusProvider,
      Provider<GoogleWorkspaceTokenStore> googleWorkspaceTokenStoreProvider,
      Provider<GoogleWorkspaceStatusProvider> googleWorkspaceStatusProvider,
      Provider<GoogleAuthManager> googleAuthManagerProvider,
      Provider<GeminiChatProvider> geminiChatProvider,
      Provider<OpenAiCompatibleChatProvider> openAiChatProvider,
      Provider<AnthropicChatProvider> anthropicChatProvider,
      Provider<GroqChatProvider> groqChatProvider) {
    this.repositoryProvider = repositoryProvider;
    this.apiKeyStoreProvider = apiKeyStoreProvider;
    this.aiRouterProvider = aiRouterProvider;
    this.geminiKeyStoreProvider = geminiKeyStoreProvider;
    this.anthropicKeyStoreProvider = anthropicKeyStoreProvider;
    this.groqKeyStoreProvider = groqKeyStoreProvider;
    this.gitHubTokenStoreProvider = gitHubTokenStoreProvider;
    this.gitHubStatusProvider = gitHubStatusProvider;
    this.ngSignalProStatusProvider = ngSignalProStatusProvider;
    this.streamlitDeploymentStoreProvider = streamlitDeploymentStoreProvider;
    this.streamlitStatusProvider = streamlitStatusProvider;
    this.googleWorkspaceTokenStoreProvider = googleWorkspaceTokenStoreProvider;
    this.googleWorkspaceStatusProvider = googleWorkspaceStatusProvider;
    this.googleAuthManagerProvider = googleAuthManagerProvider;
    this.geminiChatProvider = geminiChatProvider;
    this.openAiChatProvider = openAiChatProvider;
    this.anthropicChatProvider = anthropicChatProvider;
    this.groqChatProvider = groqChatProvider;
  }

  @Override
  public SettingsViewModel get() {
    return newInstance(repositoryProvider.get(), apiKeyStoreProvider.get(), aiRouterProvider.get(), geminiKeyStoreProvider.get(), anthropicKeyStoreProvider.get(), groqKeyStoreProvider.get(), gitHubTokenStoreProvider.get(), gitHubStatusProvider.get(), ngSignalProStatusProvider.get(), streamlitDeploymentStoreProvider.get(), streamlitStatusProvider.get(), googleWorkspaceTokenStoreProvider.get(), googleWorkspaceStatusProvider.get(), googleAuthManagerProvider.get(), geminiChatProvider.get(), openAiChatProvider.get(), anthropicChatProvider.get(), groqChatProvider.get());
  }

  public static SettingsViewModel_Factory create(Provider<SettingsRepository> repositoryProvider,
      Provider<ApiKeyStore> apiKeyStoreProvider, Provider<AiRouter> aiRouterProvider,
      Provider<GeminiKeyStore> geminiKeyStoreProvider,
      Provider<AnthropicKeyStore> anthropicKeyStoreProvider,
      Provider<GroqKeyStore> groqKeyStoreProvider,
      Provider<GitHubTokenStore> gitHubTokenStoreProvider,
      Provider<GitHubStatusProvider> gitHubStatusProvider,
      Provider<NgSignalProStatusProvider> ngSignalProStatusProvider,
      Provider<StreamlitDeploymentStore> streamlitDeploymentStoreProvider,
      Provider<StreamlitStatusProvider> streamlitStatusProvider,
      Provider<GoogleWorkspaceTokenStore> googleWorkspaceTokenStoreProvider,
      Provider<GoogleWorkspaceStatusProvider> googleWorkspaceStatusProvider,
      Provider<GoogleAuthManager> googleAuthManagerProvider,
      Provider<GeminiChatProvider> geminiChatProvider,
      Provider<OpenAiCompatibleChatProvider> openAiChatProvider,
      Provider<AnthropicChatProvider> anthropicChatProvider,
      Provider<GroqChatProvider> groqChatProvider) {
    return new SettingsViewModel_Factory(repositoryProvider, apiKeyStoreProvider, aiRouterProvider, geminiKeyStoreProvider, anthropicKeyStoreProvider, groqKeyStoreProvider, gitHubTokenStoreProvider, gitHubStatusProvider, ngSignalProStatusProvider, streamlitDeploymentStoreProvider, streamlitStatusProvider, googleWorkspaceTokenStoreProvider, googleWorkspaceStatusProvider, googleAuthManagerProvider, geminiChatProvider, openAiChatProvider, anthropicChatProvider, groqChatProvider);
  }

  public static SettingsViewModel newInstance(SettingsRepository repository,
      ApiKeyStore apiKeyStore, AiRouter aiRouter, GeminiKeyStore geminiKeyStore,
      AnthropicKeyStore anthropicKeyStore, GroqKeyStore groqKeyStore,
      GitHubTokenStore gitHubTokenStore, GitHubStatusProvider gitHubStatusProvider,
      NgSignalProStatusProvider ngSignalProStatusProvider,
      StreamlitDeploymentStore streamlitDeploymentStore,
      StreamlitStatusProvider streamlitStatusProvider,
      GoogleWorkspaceTokenStore googleWorkspaceTokenStore,
      GoogleWorkspaceStatusProvider googleWorkspaceStatusProvider,
      GoogleAuthManager googleAuthManager, GeminiChatProvider geminiChatProvider,
      OpenAiCompatibleChatProvider openAiChatProvider, AnthropicChatProvider anthropicChatProvider,
      GroqChatProvider groqChatProvider) {
    return new SettingsViewModel(repository, apiKeyStore, aiRouter, geminiKeyStore, anthropicKeyStore, groqKeyStore, gitHubTokenStore, gitHubStatusProvider, ngSignalProStatusProvider, streamlitDeploymentStore, streamlitStatusProvider, googleWorkspaceTokenStore, googleWorkspaceStatusProvider, googleAuthManager, geminiChatProvider, openAiChatProvider, anthropicChatProvider, groqChatProvider);
  }
}

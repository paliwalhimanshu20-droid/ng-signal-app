package com.jarvis.os.app.feature.missioncontrol;

import com.jarvis.os.app.core.JarvisCore;
import com.jarvis.os.app.core.agents.AgentRegistry;
import com.jarvis.os.app.core.chat.AiRouter;
import com.jarvis.os.app.core.chat.AnthropicChatProvider;
import com.jarvis.os.app.core.chat.GeminiChatProvider;
import com.jarvis.os.app.core.chat.GroqChatProvider;
import com.jarvis.os.app.core.chat.OpenAiCompatibleChatProvider;
import com.jarvis.os.app.core.workflow.WorkflowEngine;
import com.jarvis.os.app.data.repository.GitHubStatusProvider;
import com.jarvis.os.app.data.repository.NgSignalProStatusProvider;
import com.jarvis.os.app.data.settings.AnthropicKeyStore;
import com.jarvis.os.app.data.settings.ApiKeyStore;
import com.jarvis.os.app.data.settings.GeminiKeyStore;
import com.jarvis.os.app.data.settings.GroqKeyStore;
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
public final class MissionControlViewModel_Factory implements Factory<MissionControlViewModel> {
  private final Provider<JarvisCore> coreProvider;

  private final Provider<WorkflowEngine> workflowEngineProvider;

  private final Provider<AgentRegistry> agentRegistryProvider;

  private final Provider<AiRouter> aiRouterProvider;

  private final Provider<NgSignalProStatusProvider> ngSignalProProvider;

  private final Provider<GitHubStatusProvider> gitHubProvider;

  private final Provider<ApiKeyStore> apiKeyStoreProvider;

  private final Provider<GeminiKeyStore> geminiKeyStoreProvider;

  private final Provider<AnthropicKeyStore> anthropicKeyStoreProvider;

  private final Provider<GeminiChatProvider> geminiChatProvider;

  private final Provider<OpenAiCompatibleChatProvider> openAiChatProvider;

  private final Provider<AnthropicChatProvider> anthropicChatProvider;

  private final Provider<GroqKeyStore> groqKeyStoreProvider;

  private final Provider<GroqChatProvider> groqChatProvider;

  public MissionControlViewModel_Factory(Provider<JarvisCore> coreProvider,
      Provider<WorkflowEngine> workflowEngineProvider,
      Provider<AgentRegistry> agentRegistryProvider, Provider<AiRouter> aiRouterProvider,
      Provider<NgSignalProStatusProvider> ngSignalProProvider,
      Provider<GitHubStatusProvider> gitHubProvider, Provider<ApiKeyStore> apiKeyStoreProvider,
      Provider<GeminiKeyStore> geminiKeyStoreProvider,
      Provider<AnthropicKeyStore> anthropicKeyStoreProvider,
      Provider<GeminiChatProvider> geminiChatProvider,
      Provider<OpenAiCompatibleChatProvider> openAiChatProvider,
      Provider<AnthropicChatProvider> anthropicChatProvider,
      Provider<GroqKeyStore> groqKeyStoreProvider, Provider<GroqChatProvider> groqChatProvider) {
    this.coreProvider = coreProvider;
    this.workflowEngineProvider = workflowEngineProvider;
    this.agentRegistryProvider = agentRegistryProvider;
    this.aiRouterProvider = aiRouterProvider;
    this.ngSignalProProvider = ngSignalProProvider;
    this.gitHubProvider = gitHubProvider;
    this.apiKeyStoreProvider = apiKeyStoreProvider;
    this.geminiKeyStoreProvider = geminiKeyStoreProvider;
    this.anthropicKeyStoreProvider = anthropicKeyStoreProvider;
    this.geminiChatProvider = geminiChatProvider;
    this.openAiChatProvider = openAiChatProvider;
    this.anthropicChatProvider = anthropicChatProvider;
    this.groqKeyStoreProvider = groqKeyStoreProvider;
    this.groqChatProvider = groqChatProvider;
  }

  @Override
  public MissionControlViewModel get() {
    return newInstance(coreProvider.get(), workflowEngineProvider.get(), agentRegistryProvider.get(), aiRouterProvider.get(), ngSignalProProvider.get(), gitHubProvider.get(), apiKeyStoreProvider.get(), geminiKeyStoreProvider.get(), anthropicKeyStoreProvider.get(), geminiChatProvider.get(), openAiChatProvider.get(), anthropicChatProvider.get(), groqKeyStoreProvider.get(), groqChatProvider.get());
  }

  public static MissionControlViewModel_Factory create(Provider<JarvisCore> coreProvider,
      Provider<WorkflowEngine> workflowEngineProvider,
      Provider<AgentRegistry> agentRegistryProvider, Provider<AiRouter> aiRouterProvider,
      Provider<NgSignalProStatusProvider> ngSignalProProvider,
      Provider<GitHubStatusProvider> gitHubProvider, Provider<ApiKeyStore> apiKeyStoreProvider,
      Provider<GeminiKeyStore> geminiKeyStoreProvider,
      Provider<AnthropicKeyStore> anthropicKeyStoreProvider,
      Provider<GeminiChatProvider> geminiChatProvider,
      Provider<OpenAiCompatibleChatProvider> openAiChatProvider,
      Provider<AnthropicChatProvider> anthropicChatProvider,
      Provider<GroqKeyStore> groqKeyStoreProvider, Provider<GroqChatProvider> groqChatProvider) {
    return new MissionControlViewModel_Factory(coreProvider, workflowEngineProvider, agentRegistryProvider, aiRouterProvider, ngSignalProProvider, gitHubProvider, apiKeyStoreProvider, geminiKeyStoreProvider, anthropicKeyStoreProvider, geminiChatProvider, openAiChatProvider, anthropicChatProvider, groqKeyStoreProvider, groqChatProvider);
  }

  public static MissionControlViewModel newInstance(JarvisCore core, WorkflowEngine workflowEngine,
      AgentRegistry agentRegistry, AiRouter aiRouter, NgSignalProStatusProvider ngSignalPro,
      GitHubStatusProvider gitHub, ApiKeyStore apiKeyStore, GeminiKeyStore geminiKeyStore,
      AnthropicKeyStore anthropicKeyStore, GeminiChatProvider geminiChatProvider,
      OpenAiCompatibleChatProvider openAiChatProvider, AnthropicChatProvider anthropicChatProvider,
      GroqKeyStore groqKeyStore, GroqChatProvider groqChatProvider) {
    return new MissionControlViewModel(core, workflowEngine, agentRegistry, aiRouter, ngSignalPro, gitHub, apiKeyStore, geminiKeyStore, anthropicKeyStore, geminiChatProvider, openAiChatProvider, anthropicChatProvider, groqKeyStore, groqChatProvider);
  }
}

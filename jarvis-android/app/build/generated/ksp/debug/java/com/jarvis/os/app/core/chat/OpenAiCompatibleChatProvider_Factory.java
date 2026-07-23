package com.jarvis.os.app.core.chat;

import com.jarvis.os.app.data.settings.ApiKeyStore;
import com.jarvis.os.app.data.settings.SettingsRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class OpenAiCompatibleChatProvider_Factory implements Factory<OpenAiCompatibleChatProvider> {
  private final Provider<ApiKeyStore> apiKeyStoreProvider;

  private final Provider<SettingsRepository> settingsRepositoryProvider;

  public OpenAiCompatibleChatProvider_Factory(Provider<ApiKeyStore> apiKeyStoreProvider,
      Provider<SettingsRepository> settingsRepositoryProvider) {
    this.apiKeyStoreProvider = apiKeyStoreProvider;
    this.settingsRepositoryProvider = settingsRepositoryProvider;
  }

  @Override
  public OpenAiCompatibleChatProvider get() {
    return newInstance(apiKeyStoreProvider.get(), settingsRepositoryProvider.get());
  }

  public static OpenAiCompatibleChatProvider_Factory create(
      Provider<ApiKeyStore> apiKeyStoreProvider,
      Provider<SettingsRepository> settingsRepositoryProvider) {
    return new OpenAiCompatibleChatProvider_Factory(apiKeyStoreProvider, settingsRepositoryProvider);
  }

  public static OpenAiCompatibleChatProvider newInstance(ApiKeyStore apiKeyStore,
      SettingsRepository settingsRepository) {
    return new OpenAiCompatibleChatProvider(apiKeyStore, settingsRepository);
  }
}

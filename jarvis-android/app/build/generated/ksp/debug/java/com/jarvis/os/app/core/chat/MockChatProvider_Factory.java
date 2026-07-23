package com.jarvis.os.app.core.chat;

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
public final class MockChatProvider_Factory implements Factory<MockChatProvider> {
  private final Provider<SettingsRepository> settingsRepositoryProvider;

  public MockChatProvider_Factory(Provider<SettingsRepository> settingsRepositoryProvider) {
    this.settingsRepositoryProvider = settingsRepositoryProvider;
  }

  @Override
  public MockChatProvider get() {
    return newInstance(settingsRepositoryProvider.get());
  }

  public static MockChatProvider_Factory create(
      Provider<SettingsRepository> settingsRepositoryProvider) {
    return new MockChatProvider_Factory(settingsRepositoryProvider);
  }

  public static MockChatProvider newInstance(SettingsRepository settingsRepository) {
    return new MockChatProvider(settingsRepository);
  }
}

package com.jarvis.os.app;

import com.jarvis.os.app.data.settings.SettingsRepository;
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
public final class MainActivityViewModel_Factory implements Factory<MainActivityViewModel> {
  private final Provider<SettingsRepository> settingsRepositoryProvider;

  public MainActivityViewModel_Factory(Provider<SettingsRepository> settingsRepositoryProvider) {
    this.settingsRepositoryProvider = settingsRepositoryProvider;
  }

  @Override
  public MainActivityViewModel get() {
    return newInstance(settingsRepositoryProvider.get());
  }

  public static MainActivityViewModel_Factory create(
      Provider<SettingsRepository> settingsRepositoryProvider) {
    return new MainActivityViewModel_Factory(settingsRepositoryProvider);
  }

  public static MainActivityViewModel newInstance(SettingsRepository settingsRepository) {
    return new MainActivityViewModel(settingsRepository);
  }
}

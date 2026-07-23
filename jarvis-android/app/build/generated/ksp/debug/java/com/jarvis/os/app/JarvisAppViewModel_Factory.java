package com.jarvis.os.app;

import com.jarvis.os.app.core.JarvisCore;
import com.jarvis.os.app.core.JarvisPresence;
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
public final class JarvisAppViewModel_Factory implements Factory<JarvisAppViewModel> {
  private final Provider<JarvisCore> coreProvider;

  private final Provider<SettingsRepository> settingsRepositoryProvider;

  private final Provider<JarvisPresence> presenceProvider;

  public JarvisAppViewModel_Factory(Provider<JarvisCore> coreProvider,
      Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<JarvisPresence> presenceProvider) {
    this.coreProvider = coreProvider;
    this.settingsRepositoryProvider = settingsRepositoryProvider;
    this.presenceProvider = presenceProvider;
  }

  @Override
  public JarvisAppViewModel get() {
    return newInstance(coreProvider.get(), settingsRepositoryProvider.get(), presenceProvider.get());
  }

  public static JarvisAppViewModel_Factory create(Provider<JarvisCore> coreProvider,
      Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<JarvisPresence> presenceProvider) {
    return new JarvisAppViewModel_Factory(coreProvider, settingsRepositoryProvider, presenceProvider);
  }

  public static JarvisAppViewModel newInstance(JarvisCore core,
      SettingsRepository settingsRepository, JarvisPresence presence) {
    return new JarvisAppViewModel(core, settingsRepository, presence);
  }
}

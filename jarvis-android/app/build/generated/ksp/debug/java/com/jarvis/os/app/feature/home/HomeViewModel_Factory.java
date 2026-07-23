package com.jarvis.os.app.feature.home;

import com.jarvis.os.app.core.JarvisCore;
import com.jarvis.os.app.core.JarvisPresence;
import com.jarvis.os.app.core.agents.AgentRegistry;
import com.jarvis.os.app.core.voice.SpeechSynthesizer;
import com.jarvis.os.app.core.voice.SpeechToTextController;
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
public final class HomeViewModel_Factory implements Factory<HomeViewModel> {
  private final Provider<JarvisCore> coreProvider;

  private final Provider<SpeechToTextController> speechToTextProvider;

  private final Provider<SpeechSynthesizer> speechSynthesizerProvider;

  private final Provider<SettingsRepository> settingsRepositoryProvider;

  private final Provider<JarvisPresence> presenceProvider;

  private final Provider<AgentRegistry> agentRegistryProvider;

  public HomeViewModel_Factory(Provider<JarvisCore> coreProvider,
      Provider<SpeechToTextController> speechToTextProvider,
      Provider<SpeechSynthesizer> speechSynthesizerProvider,
      Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<JarvisPresence> presenceProvider, Provider<AgentRegistry> agentRegistryProvider) {
    this.coreProvider = coreProvider;
    this.speechToTextProvider = speechToTextProvider;
    this.speechSynthesizerProvider = speechSynthesizerProvider;
    this.settingsRepositoryProvider = settingsRepositoryProvider;
    this.presenceProvider = presenceProvider;
    this.agentRegistryProvider = agentRegistryProvider;
  }

  @Override
  public HomeViewModel get() {
    return newInstance(coreProvider.get(), speechToTextProvider.get(), speechSynthesizerProvider.get(), settingsRepositoryProvider.get(), presenceProvider.get(), agentRegistryProvider.get());
  }

  public static HomeViewModel_Factory create(Provider<JarvisCore> coreProvider,
      Provider<SpeechToTextController> speechToTextProvider,
      Provider<SpeechSynthesizer> speechSynthesizerProvider,
      Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<JarvisPresence> presenceProvider, Provider<AgentRegistry> agentRegistryProvider) {
    return new HomeViewModel_Factory(coreProvider, speechToTextProvider, speechSynthesizerProvider, settingsRepositoryProvider, presenceProvider, agentRegistryProvider);
  }

  public static HomeViewModel newInstance(JarvisCore core, SpeechToTextController speechToText,
      SpeechSynthesizer speechSynthesizer, SettingsRepository settingsRepository,
      JarvisPresence presence, AgentRegistry agentRegistry) {
    return new HomeViewModel(core, speechToText, speechSynthesizer, settingsRepository, presence, agentRegistry);
  }
}

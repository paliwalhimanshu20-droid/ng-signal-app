package com.jarvis.os.app.feature.chat;

import com.jarvis.os.app.core.JarvisCore;
import com.jarvis.os.app.core.JarvisPresence;
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
public final class ChatViewModel_Factory implements Factory<ChatViewModel> {
  private final Provider<JarvisCore> coreProvider;

  private final Provider<SpeechToTextController> speechToTextProvider;

  private final Provider<SpeechSynthesizer> speechSynthesizerProvider;

  private final Provider<SettingsRepository> settingsRepositoryProvider;

  private final Provider<JarvisPresence> presenceProvider;

  public ChatViewModel_Factory(Provider<JarvisCore> coreProvider,
      Provider<SpeechToTextController> speechToTextProvider,
      Provider<SpeechSynthesizer> speechSynthesizerProvider,
      Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<JarvisPresence> presenceProvider) {
    this.coreProvider = coreProvider;
    this.speechToTextProvider = speechToTextProvider;
    this.speechSynthesizerProvider = speechSynthesizerProvider;
    this.settingsRepositoryProvider = settingsRepositoryProvider;
    this.presenceProvider = presenceProvider;
  }

  @Override
  public ChatViewModel get() {
    return newInstance(coreProvider.get(), speechToTextProvider.get(), speechSynthesizerProvider.get(), settingsRepositoryProvider.get(), presenceProvider.get());
  }

  public static ChatViewModel_Factory create(Provider<JarvisCore> coreProvider,
      Provider<SpeechToTextController> speechToTextProvider,
      Provider<SpeechSynthesizer> speechSynthesizerProvider,
      Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<JarvisPresence> presenceProvider) {
    return new ChatViewModel_Factory(coreProvider, speechToTextProvider, speechSynthesizerProvider, settingsRepositoryProvider, presenceProvider);
  }

  public static ChatViewModel newInstance(JarvisCore core, SpeechToTextController speechToText,
      SpeechSynthesizer speechSynthesizer, SettingsRepository settingsRepository,
      JarvisPresence presence) {
    return new ChatViewModel(core, speechToText, speechSynthesizer, settingsRepository, presence);
  }
}

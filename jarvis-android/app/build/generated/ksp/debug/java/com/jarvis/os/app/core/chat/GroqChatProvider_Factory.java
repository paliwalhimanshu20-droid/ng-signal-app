package com.jarvis.os.app.core.chat;

import com.jarvis.os.app.data.settings.GroqKeyStore;
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
public final class GroqChatProvider_Factory implements Factory<GroqChatProvider> {
  private final Provider<GroqKeyStore> keyStoreProvider;

  public GroqChatProvider_Factory(Provider<GroqKeyStore> keyStoreProvider) {
    this.keyStoreProvider = keyStoreProvider;
  }

  @Override
  public GroqChatProvider get() {
    return newInstance(keyStoreProvider.get());
  }

  public static GroqChatProvider_Factory create(Provider<GroqKeyStore> keyStoreProvider) {
    return new GroqChatProvider_Factory(keyStoreProvider);
  }

  public static GroqChatProvider newInstance(GroqKeyStore keyStore) {
    return new GroqChatProvider(keyStore);
  }
}

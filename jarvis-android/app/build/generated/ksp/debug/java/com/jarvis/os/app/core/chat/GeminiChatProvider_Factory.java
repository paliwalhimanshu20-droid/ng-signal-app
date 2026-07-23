package com.jarvis.os.app.core.chat;

import com.jarvis.os.app.data.settings.GeminiKeyStore;
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
public final class GeminiChatProvider_Factory implements Factory<GeminiChatProvider> {
  private final Provider<GeminiKeyStore> keyStoreProvider;

  public GeminiChatProvider_Factory(Provider<GeminiKeyStore> keyStoreProvider) {
    this.keyStoreProvider = keyStoreProvider;
  }

  @Override
  public GeminiChatProvider get() {
    return newInstance(keyStoreProvider.get());
  }

  public static GeminiChatProvider_Factory create(Provider<GeminiKeyStore> keyStoreProvider) {
    return new GeminiChatProvider_Factory(keyStoreProvider);
  }

  public static GeminiChatProvider newInstance(GeminiKeyStore keyStore) {
    return new GeminiChatProvider(keyStore);
  }
}

package com.jarvis.os.app.core.chat;

import com.jarvis.os.app.data.settings.AnthropicKeyStore;
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
public final class AnthropicChatProvider_Factory implements Factory<AnthropicChatProvider> {
  private final Provider<AnthropicKeyStore> keyStoreProvider;

  public AnthropicChatProvider_Factory(Provider<AnthropicKeyStore> keyStoreProvider) {
    this.keyStoreProvider = keyStoreProvider;
  }

  @Override
  public AnthropicChatProvider get() {
    return newInstance(keyStoreProvider.get());
  }

  public static AnthropicChatProvider_Factory create(Provider<AnthropicKeyStore> keyStoreProvider) {
    return new AnthropicChatProvider_Factory(keyStoreProvider);
  }

  public static AnthropicChatProvider newInstance(AnthropicKeyStore keyStore) {
    return new AnthropicChatProvider(keyStore);
  }
}

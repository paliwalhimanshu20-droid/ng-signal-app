package com.jarvis.os.app.core.chat;

import com.jarvis.os.app.data.settings.PreferredProviderStore;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import java.util.Set;
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
public final class AiRouter_Factory implements Factory<AiRouter> {
  private final Provider<Set<ChatProvider>> providersProvider;

  private final Provider<PreferredProviderStore> preferredProviderStoreProvider;

  public AiRouter_Factory(Provider<Set<ChatProvider>> providersProvider,
      Provider<PreferredProviderStore> preferredProviderStoreProvider) {
    this.providersProvider = providersProvider;
    this.preferredProviderStoreProvider = preferredProviderStoreProvider;
  }

  @Override
  public AiRouter get() {
    return newInstance(providersProvider.get(), preferredProviderStoreProvider.get());
  }

  public static AiRouter_Factory create(Provider<Set<ChatProvider>> providersProvider,
      Provider<PreferredProviderStore> preferredProviderStoreProvider) {
    return new AiRouter_Factory(providersProvider, preferredProviderStoreProvider);
  }

  public static AiRouter newInstance(Set<ChatProvider> providers,
      PreferredProviderStore preferredProviderStore) {
    return new AiRouter(providers, preferredProviderStore);
  }
}

package com.jarvis.os.app.data.repository;

import com.jarvis.os.app.data.settings.GitHubTokenStore;
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
public final class RealNgSignalProStatusProvider_Factory implements Factory<RealNgSignalProStatusProvider> {
  private final Provider<GitHubTokenStore> tokenStoreProvider;

  public RealNgSignalProStatusProvider_Factory(Provider<GitHubTokenStore> tokenStoreProvider) {
    this.tokenStoreProvider = tokenStoreProvider;
  }

  @Override
  public RealNgSignalProStatusProvider get() {
    return newInstance(tokenStoreProvider.get());
  }

  public static RealNgSignalProStatusProvider_Factory create(
      Provider<GitHubTokenStore> tokenStoreProvider) {
    return new RealNgSignalProStatusProvider_Factory(tokenStoreProvider);
  }

  public static RealNgSignalProStatusProvider newInstance(GitHubTokenStore tokenStore) {
    return new RealNgSignalProStatusProvider(tokenStore);
  }
}

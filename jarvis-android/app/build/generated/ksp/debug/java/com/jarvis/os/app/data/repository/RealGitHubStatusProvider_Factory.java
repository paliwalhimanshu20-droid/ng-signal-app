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
public final class RealGitHubStatusProvider_Factory implements Factory<RealGitHubStatusProvider> {
  private final Provider<GitHubTokenStore> tokenStoreProvider;

  public RealGitHubStatusProvider_Factory(Provider<GitHubTokenStore> tokenStoreProvider) {
    this.tokenStoreProvider = tokenStoreProvider;
  }

  @Override
  public RealGitHubStatusProvider get() {
    return newInstance(tokenStoreProvider.get());
  }

  public static RealGitHubStatusProvider_Factory create(
      Provider<GitHubTokenStore> tokenStoreProvider) {
    return new RealGitHubStatusProvider_Factory(tokenStoreProvider);
  }

  public static RealGitHubStatusProvider newInstance(GitHubTokenStore tokenStore) {
    return new RealGitHubStatusProvider(tokenStore);
  }
}

package com.jarvis.os.app.core.tools;

import com.jarvis.os.app.data.repository.GitHubStatusProvider;
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
public final class GitHubStatusTool_Factory implements Factory<GitHubStatusTool> {
  private final Provider<GitHubStatusProvider> providerProvider;

  public GitHubStatusTool_Factory(Provider<GitHubStatusProvider> providerProvider) {
    this.providerProvider = providerProvider;
  }

  @Override
  public GitHubStatusTool get() {
    return newInstance(providerProvider.get());
  }

  public static GitHubStatusTool_Factory create(Provider<GitHubStatusProvider> providerProvider) {
    return new GitHubStatusTool_Factory(providerProvider);
  }

  public static GitHubStatusTool newInstance(GitHubStatusProvider provider) {
    return new GitHubStatusTool(provider);
  }
}

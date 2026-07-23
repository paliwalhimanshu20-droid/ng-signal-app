package com.jarvis.os.app.data.repository;

import com.jarvis.os.app.core.security.AuthenticationProvider;
import com.jarvis.os.app.data.settings.GoogleWorkspaceTokenStore;
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
public final class RealGoogleWorkspaceStatusProvider_Factory implements Factory<RealGoogleWorkspaceStatusProvider> {
  private final Provider<AuthenticationProvider> authManagerProvider;

  private final Provider<GoogleWorkspaceTokenStore> tokenStoreProvider;

  public RealGoogleWorkspaceStatusProvider_Factory(
      Provider<AuthenticationProvider> authManagerProvider,
      Provider<GoogleWorkspaceTokenStore> tokenStoreProvider) {
    this.authManagerProvider = authManagerProvider;
    this.tokenStoreProvider = tokenStoreProvider;
  }

  @Override
  public RealGoogleWorkspaceStatusProvider get() {
    return newInstance(authManagerProvider.get(), tokenStoreProvider.get());
  }

  public static RealGoogleWorkspaceStatusProvider_Factory create(
      Provider<AuthenticationProvider> authManagerProvider,
      Provider<GoogleWorkspaceTokenStore> tokenStoreProvider) {
    return new RealGoogleWorkspaceStatusProvider_Factory(authManagerProvider, tokenStoreProvider);
  }

  public static RealGoogleWorkspaceStatusProvider newInstance(AuthenticationProvider authManager,
      GoogleWorkspaceTokenStore tokenStore) {
    return new RealGoogleWorkspaceStatusProvider(authManager, tokenStore);
  }
}

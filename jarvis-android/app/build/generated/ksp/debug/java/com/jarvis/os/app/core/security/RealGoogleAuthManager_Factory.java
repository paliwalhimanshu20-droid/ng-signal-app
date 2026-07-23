package com.jarvis.os.app.core.security;

import android.content.Context;
import com.jarvis.os.app.data.settings.GoogleWorkspaceTokenStore;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class RealGoogleAuthManager_Factory implements Factory<RealGoogleAuthManager> {
  private final Provider<Context> contextProvider;

  private final Provider<GoogleWorkspaceTokenStore> tokenStoreProvider;

  public RealGoogleAuthManager_Factory(Provider<Context> contextProvider,
      Provider<GoogleWorkspaceTokenStore> tokenStoreProvider) {
    this.contextProvider = contextProvider;
    this.tokenStoreProvider = tokenStoreProvider;
  }

  @Override
  public RealGoogleAuthManager get() {
    return newInstance(contextProvider.get(), tokenStoreProvider.get());
  }

  public static RealGoogleAuthManager_Factory create(Provider<Context> contextProvider,
      Provider<GoogleWorkspaceTokenStore> tokenStoreProvider) {
    return new RealGoogleAuthManager_Factory(contextProvider, tokenStoreProvider);
  }

  public static RealGoogleAuthManager newInstance(Context context,
      GoogleWorkspaceTokenStore tokenStore) {
    return new RealGoogleAuthManager(context, tokenStore);
  }
}

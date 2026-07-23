package com.jarvis.os.app.core.tools;

import com.jarvis.os.app.data.repository.GoogleWorkspaceStatusProvider;
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
public final class GoogleGmailTool_Factory implements Factory<GoogleGmailTool> {
  private final Provider<GoogleWorkspaceStatusProvider> providerProvider;

  public GoogleGmailTool_Factory(Provider<GoogleWorkspaceStatusProvider> providerProvider) {
    this.providerProvider = providerProvider;
  }

  @Override
  public GoogleGmailTool get() {
    return newInstance(providerProvider.get());
  }

  public static GoogleGmailTool_Factory create(
      Provider<GoogleWorkspaceStatusProvider> providerProvider) {
    return new GoogleGmailTool_Factory(providerProvider);
  }

  public static GoogleGmailTool newInstance(GoogleWorkspaceStatusProvider provider) {
    return new GoogleGmailTool(provider);
  }
}

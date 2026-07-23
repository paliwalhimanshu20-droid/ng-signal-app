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
public final class GoogleCalendarTool_Factory implements Factory<GoogleCalendarTool> {
  private final Provider<GoogleWorkspaceStatusProvider> providerProvider;

  public GoogleCalendarTool_Factory(Provider<GoogleWorkspaceStatusProvider> providerProvider) {
    this.providerProvider = providerProvider;
  }

  @Override
  public GoogleCalendarTool get() {
    return newInstance(providerProvider.get());
  }

  public static GoogleCalendarTool_Factory create(
      Provider<GoogleWorkspaceStatusProvider> providerProvider) {
    return new GoogleCalendarTool_Factory(providerProvider);
  }

  public static GoogleCalendarTool newInstance(GoogleWorkspaceStatusProvider provider) {
    return new GoogleCalendarTool(provider);
  }
}

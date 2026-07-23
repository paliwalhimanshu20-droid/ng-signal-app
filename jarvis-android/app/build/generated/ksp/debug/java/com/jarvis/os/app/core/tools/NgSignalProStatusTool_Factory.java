package com.jarvis.os.app.core.tools;

import com.jarvis.os.app.data.repository.NgSignalProStatusProvider;
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
public final class NgSignalProStatusTool_Factory implements Factory<NgSignalProStatusTool> {
  private final Provider<NgSignalProStatusProvider> providerProvider;

  public NgSignalProStatusTool_Factory(Provider<NgSignalProStatusProvider> providerProvider) {
    this.providerProvider = providerProvider;
  }

  @Override
  public NgSignalProStatusTool get() {
    return newInstance(providerProvider.get());
  }

  public static NgSignalProStatusTool_Factory create(
      Provider<NgSignalProStatusProvider> providerProvider) {
    return new NgSignalProStatusTool_Factory(providerProvider);
  }

  public static NgSignalProStatusTool newInstance(NgSignalProStatusProvider provider) {
    return new NgSignalProStatusTool(provider);
  }
}

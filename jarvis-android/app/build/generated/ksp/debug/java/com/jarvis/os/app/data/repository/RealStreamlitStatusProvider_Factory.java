package com.jarvis.os.app.data.repository;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class RealStreamlitStatusProvider_Factory implements Factory<RealStreamlitStatusProvider> {
  @Override
  public RealStreamlitStatusProvider get() {
    return newInstance();
  }

  public static RealStreamlitStatusProvider_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static RealStreamlitStatusProvider newInstance() {
    return new RealStreamlitStatusProvider();
  }

  private static final class InstanceHolder {
    private static final RealStreamlitStatusProvider_Factory INSTANCE = new RealStreamlitStatusProvider_Factory();
  }
}

package com.jarvis.os.app.core.deployment;

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
public final class PackageExtractor_Factory implements Factory<PackageExtractor> {
  @Override
  public PackageExtractor get() {
    return newInstance();
  }

  public static PackageExtractor_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static PackageExtractor newInstance() {
    return new PackageExtractor();
  }

  private static final class InstanceHolder {
    private static final PackageExtractor_Factory INSTANCE = new PackageExtractor_Factory();
  }
}

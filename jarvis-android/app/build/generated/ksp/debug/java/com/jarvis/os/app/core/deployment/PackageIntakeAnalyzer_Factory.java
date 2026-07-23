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
public final class PackageIntakeAnalyzer_Factory implements Factory<PackageIntakeAnalyzer> {
  @Override
  public PackageIntakeAnalyzer get() {
    return newInstance();
  }

  public static PackageIntakeAnalyzer_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static PackageIntakeAnalyzer newInstance() {
    return new PackageIntakeAnalyzer();
  }

  private static final class InstanceHolder {
    private static final PackageIntakeAnalyzer_Factory INSTANCE = new PackageIntakeAnalyzer_Factory();
  }
}

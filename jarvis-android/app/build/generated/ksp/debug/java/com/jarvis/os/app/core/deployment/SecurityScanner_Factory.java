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
public final class SecurityScanner_Factory implements Factory<SecurityScanner> {
  @Override
  public SecurityScanner get() {
    return newInstance();
  }

  public static SecurityScanner_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static SecurityScanner newInstance() {
    return new SecurityScanner();
  }

  private static final class InstanceHolder {
    private static final SecurityScanner_Factory INSTANCE = new SecurityScanner_Factory();
  }
}

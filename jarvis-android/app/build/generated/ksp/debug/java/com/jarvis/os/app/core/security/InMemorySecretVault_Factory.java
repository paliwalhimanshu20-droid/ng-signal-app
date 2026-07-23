package com.jarvis.os.app.core.security;

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
public final class InMemorySecretVault_Factory implements Factory<InMemorySecretVault> {
  @Override
  public InMemorySecretVault get() {
    return newInstance();
  }

  public static InMemorySecretVault_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static InMemorySecretVault newInstance() {
    return new InMemorySecretVault();
  }

  private static final class InstanceHolder {
    private static final InMemorySecretVault_Factory INSTANCE = new InMemorySecretVault_Factory();
  }
}

package com.jarvis.os.app.core.chat;

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
public final class MockGptProvider_Factory implements Factory<MockGptProvider> {
  @Override
  public MockGptProvider get() {
    return newInstance();
  }

  public static MockGptProvider_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static MockGptProvider newInstance() {
    return new MockGptProvider();
  }

  private static final class InstanceHolder {
    private static final MockGptProvider_Factory INSTANCE = new MockGptProvider_Factory();
  }
}

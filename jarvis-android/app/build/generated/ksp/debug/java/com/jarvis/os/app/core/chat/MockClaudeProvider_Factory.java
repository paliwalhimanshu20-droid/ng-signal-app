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
public final class MockClaudeProvider_Factory implements Factory<MockClaudeProvider> {
  @Override
  public MockClaudeProvider get() {
    return newInstance();
  }

  public static MockClaudeProvider_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static MockClaudeProvider newInstance() {
    return new MockClaudeProvider();
  }

  private static final class InstanceHolder {
    private static final MockClaudeProvider_Factory INSTANCE = new MockClaudeProvider_Factory();
  }
}

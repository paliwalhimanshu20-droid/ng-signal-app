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
public final class MockConnectionRepository_Factory implements Factory<MockConnectionRepository> {
  @Override
  public MockConnectionRepository get() {
    return newInstance();
  }

  public static MockConnectionRepository_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static MockConnectionRepository newInstance() {
    return new MockConnectionRepository();
  }

  private static final class InstanceHolder {
    private static final MockConnectionRepository_Factory INSTANCE = new MockConnectionRepository_Factory();
  }
}

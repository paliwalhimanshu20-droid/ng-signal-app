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
public final class MockMemoryRepository_Factory implements Factory<MockMemoryRepository> {
  @Override
  public MockMemoryRepository get() {
    return newInstance();
  }

  public static MockMemoryRepository_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static MockMemoryRepository newInstance() {
    return new MockMemoryRepository();
  }

  private static final class InstanceHolder {
    private static final MockMemoryRepository_Factory INSTANCE = new MockMemoryRepository_Factory();
  }
}

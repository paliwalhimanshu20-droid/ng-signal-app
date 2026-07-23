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
public final class MockProjectRepository_Factory implements Factory<MockProjectRepository> {
  @Override
  public MockProjectRepository get() {
    return newInstance();
  }

  public static MockProjectRepository_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static MockProjectRepository newInstance() {
    return new MockProjectRepository();
  }

  private static final class InstanceHolder {
    private static final MockProjectRepository_Factory INSTANCE = new MockProjectRepository_Factory();
  }
}

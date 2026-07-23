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
public final class MockHomeAutomationRepository_Factory implements Factory<MockHomeAutomationRepository> {
  @Override
  public MockHomeAutomationRepository get() {
    return newInstance();
  }

  public static MockHomeAutomationRepository_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static MockHomeAutomationRepository newInstance() {
    return new MockHomeAutomationRepository();
  }

  private static final class InstanceHolder {
    private static final MockHomeAutomationRepository_Factory INSTANCE = new MockHomeAutomationRepository_Factory();
  }
}

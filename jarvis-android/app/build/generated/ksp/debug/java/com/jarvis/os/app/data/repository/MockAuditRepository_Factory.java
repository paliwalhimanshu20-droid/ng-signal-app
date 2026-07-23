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
public final class MockAuditRepository_Factory implements Factory<MockAuditRepository> {
  @Override
  public MockAuditRepository get() {
    return newInstance();
  }

  public static MockAuditRepository_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static MockAuditRepository newInstance() {
    return new MockAuditRepository();
  }

  private static final class InstanceHolder {
    private static final MockAuditRepository_Factory INSTANCE = new MockAuditRepository_Factory();
  }
}

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
public final class MockApprovalRepository_Factory implements Factory<MockApprovalRepository> {
  @Override
  public MockApprovalRepository get() {
    return newInstance();
  }

  public static MockApprovalRepository_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static MockApprovalRepository newInstance() {
    return new MockApprovalRepository();
  }

  private static final class InstanceHolder {
    private static final MockApprovalRepository_Factory INSTANCE = new MockApprovalRepository_Factory();
  }
}

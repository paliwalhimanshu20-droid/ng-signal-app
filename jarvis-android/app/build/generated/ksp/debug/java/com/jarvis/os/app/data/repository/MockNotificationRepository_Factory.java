package com.jarvis.os.app.data.repository;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import kotlinx.coroutines.CoroutineScope;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("com.jarvis.os.app.di.ApplicationScope")
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
public final class MockNotificationRepository_Factory implements Factory<MockNotificationRepository> {
  private final Provider<CoroutineScope> scopeProvider;

  public MockNotificationRepository_Factory(Provider<CoroutineScope> scopeProvider) {
    this.scopeProvider = scopeProvider;
  }

  @Override
  public MockNotificationRepository get() {
    return newInstance(scopeProvider.get());
  }

  public static MockNotificationRepository_Factory create(Provider<CoroutineScope> scopeProvider) {
    return new MockNotificationRepository_Factory(scopeProvider);
  }

  public static MockNotificationRepository newInstance(CoroutineScope scope) {
    return new MockNotificationRepository(scope);
  }
}

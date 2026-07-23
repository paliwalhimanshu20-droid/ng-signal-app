package com.jarvis.os.app.feature.notifications;

import com.jarvis.os.app.core.JarvisCore;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class NotificationsViewModel_Factory implements Factory<NotificationsViewModel> {
  private final Provider<JarvisCore> coreProvider;

  public NotificationsViewModel_Factory(Provider<JarvisCore> coreProvider) {
    this.coreProvider = coreProvider;
  }

  @Override
  public NotificationsViewModel get() {
    return newInstance(coreProvider.get());
  }

  public static NotificationsViewModel_Factory create(Provider<JarvisCore> coreProvider) {
    return new NotificationsViewModel_Factory(coreProvider);
  }

  public static NotificationsViewModel newInstance(JarvisCore core) {
    return new NotificationsViewModel(core);
  }
}

package com.jarvis.os.app.feature.connections;

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
public final class ConnectionsViewModel_Factory implements Factory<ConnectionsViewModel> {
  private final Provider<JarvisCore> coreProvider;

  public ConnectionsViewModel_Factory(Provider<JarvisCore> coreProvider) {
    this.coreProvider = coreProvider;
  }

  @Override
  public ConnectionsViewModel get() {
    return newInstance(coreProvider.get());
  }

  public static ConnectionsViewModel_Factory create(Provider<JarvisCore> coreProvider) {
    return new ConnectionsViewModel_Factory(coreProvider);
  }

  public static ConnectionsViewModel newInstance(JarvisCore core) {
    return new ConnectionsViewModel(core);
  }
}

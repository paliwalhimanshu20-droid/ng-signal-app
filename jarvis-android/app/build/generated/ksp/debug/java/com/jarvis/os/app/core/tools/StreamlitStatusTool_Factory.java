package com.jarvis.os.app.core.tools;

import com.jarvis.os.app.data.repository.StreamlitStatusProvider;
import com.jarvis.os.app.data.settings.StreamlitDeploymentStore;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class StreamlitStatusTool_Factory implements Factory<StreamlitStatusTool> {
  private final Provider<StreamlitStatusProvider> providerProvider;

  private final Provider<StreamlitDeploymentStore> deploymentStoreProvider;

  public StreamlitStatusTool_Factory(Provider<StreamlitStatusProvider> providerProvider,
      Provider<StreamlitDeploymentStore> deploymentStoreProvider) {
    this.providerProvider = providerProvider;
    this.deploymentStoreProvider = deploymentStoreProvider;
  }

  @Override
  public StreamlitStatusTool get() {
    return newInstance(providerProvider.get(), deploymentStoreProvider.get());
  }

  public static StreamlitStatusTool_Factory create(
      Provider<StreamlitStatusProvider> providerProvider,
      Provider<StreamlitDeploymentStore> deploymentStoreProvider) {
    return new StreamlitStatusTool_Factory(providerProvider, deploymentStoreProvider);
  }

  public static StreamlitStatusTool newInstance(StreamlitStatusProvider provider,
      StreamlitDeploymentStore deploymentStore) {
    return new StreamlitStatusTool(provider, deploymentStore);
  }
}

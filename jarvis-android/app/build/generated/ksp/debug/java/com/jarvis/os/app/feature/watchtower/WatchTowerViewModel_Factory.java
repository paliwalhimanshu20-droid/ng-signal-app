package com.jarvis.os.app.feature.watchtower;

import com.jarvis.os.app.core.agents.AgentRegistry;
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
public final class WatchTowerViewModel_Factory implements Factory<WatchTowerViewModel> {
  private final Provider<AgentRegistry> agentRegistryProvider;

  public WatchTowerViewModel_Factory(Provider<AgentRegistry> agentRegistryProvider) {
    this.agentRegistryProvider = agentRegistryProvider;
  }

  @Override
  public WatchTowerViewModel get() {
    return newInstance(agentRegistryProvider.get());
  }

  public static WatchTowerViewModel_Factory create(Provider<AgentRegistry> agentRegistryProvider) {
    return new WatchTowerViewModel_Factory(agentRegistryProvider);
  }

  public static WatchTowerViewModel newInstance(AgentRegistry agentRegistry) {
    return new WatchTowerViewModel(agentRegistry);
  }
}

package com.jarvis.os.app.core.agents;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import java.util.Set;
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
public final class MockAgentRegistry_Factory implements Factory<MockAgentRegistry> {
  private final Provider<Set<Agent>> boundAgentsProvider;

  public MockAgentRegistry_Factory(Provider<Set<Agent>> boundAgentsProvider) {
    this.boundAgentsProvider = boundAgentsProvider;
  }

  @Override
  public MockAgentRegistry get() {
    return newInstance(boundAgentsProvider.get());
  }

  public static MockAgentRegistry_Factory create(Provider<Set<Agent>> boundAgentsProvider) {
    return new MockAgentRegistry_Factory(boundAgentsProvider);
  }

  public static MockAgentRegistry newInstance(Set<Agent> boundAgents) {
    return new MockAgentRegistry(boundAgents);
  }
}

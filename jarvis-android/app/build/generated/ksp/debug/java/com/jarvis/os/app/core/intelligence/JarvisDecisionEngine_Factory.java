package com.jarvis.os.app.core.intelligence;

import com.jarvis.os.app.core.agents.AgentRegistry;
import com.jarvis.os.app.data.repository.ToolRepository;
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
public final class JarvisDecisionEngine_Factory implements Factory<JarvisDecisionEngine> {
  private final Provider<ToolRepository> toolsProvider;

  private final Provider<AgentRegistry> agentsProvider;

  public JarvisDecisionEngine_Factory(Provider<ToolRepository> toolsProvider,
      Provider<AgentRegistry> agentsProvider) {
    this.toolsProvider = toolsProvider;
    this.agentsProvider = agentsProvider;
  }

  @Override
  public JarvisDecisionEngine get() {
    return newInstance(toolsProvider.get(), agentsProvider.get());
  }

  public static JarvisDecisionEngine_Factory create(Provider<ToolRepository> toolsProvider,
      Provider<AgentRegistry> agentsProvider) {
    return new JarvisDecisionEngine_Factory(toolsProvider, agentsProvider);
  }

  public static JarvisDecisionEngine newInstance(ToolRepository tools, AgentRegistry agents) {
    return new JarvisDecisionEngine(tools, agents);
  }
}

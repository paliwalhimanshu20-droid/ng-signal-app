package com.jarvis.os.app.core.monitoring;

import com.jarvis.os.app.core.workflow.WorkflowEngine;
import com.jarvis.os.app.data.repository.ConnectionRepository;
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
public final class SystemHealthMonitor_Factory implements Factory<SystemHealthMonitor> {
  private final Provider<ConnectionRepository> connectionsProvider;

  private final Provider<ToolRepository> toolsProvider;

  private final Provider<WorkflowEngine> workflowsProvider;

  public SystemHealthMonitor_Factory(Provider<ConnectionRepository> connectionsProvider,
      Provider<ToolRepository> toolsProvider, Provider<WorkflowEngine> workflowsProvider) {
    this.connectionsProvider = connectionsProvider;
    this.toolsProvider = toolsProvider;
    this.workflowsProvider = workflowsProvider;
  }

  @Override
  public SystemHealthMonitor get() {
    return newInstance(connectionsProvider.get(), toolsProvider.get(), workflowsProvider.get());
  }

  public static SystemHealthMonitor_Factory create(
      Provider<ConnectionRepository> connectionsProvider, Provider<ToolRepository> toolsProvider,
      Provider<WorkflowEngine> workflowsProvider) {
    return new SystemHealthMonitor_Factory(connectionsProvider, toolsProvider, workflowsProvider);
  }

  public static SystemHealthMonitor newInstance(ConnectionRepository connections,
      ToolRepository tools, WorkflowEngine workflows) {
    return new SystemHealthMonitor(connections, tools, workflows);
  }
}

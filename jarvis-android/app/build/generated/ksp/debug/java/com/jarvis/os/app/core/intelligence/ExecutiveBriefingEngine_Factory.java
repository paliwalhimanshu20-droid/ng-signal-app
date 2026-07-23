package com.jarvis.os.app.core.intelligence;

import com.jarvis.os.app.core.agents.AgentRegistry;
import com.jarvis.os.app.data.repository.ApprovalRepository;
import com.jarvis.os.app.data.repository.ConnectionRepository;
import com.jarvis.os.app.data.repository.GitHubStatusProvider;
import com.jarvis.os.app.data.repository.MemoryRepository;
import com.jarvis.os.app.data.repository.NgSignalProStatusProvider;
import com.jarvis.os.app.data.repository.NotificationRepository;
import com.jarvis.os.app.data.repository.ProjectRepository;
import com.jarvis.os.app.data.settings.SettingsRepository;
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
public final class ExecutiveBriefingEngine_Factory implements Factory<ExecutiveBriefingEngine> {
  private final Provider<ProjectRepository> projectsProvider;

  private final Provider<ApprovalRepository> approvalsProvider;

  private final Provider<NotificationRepository> notificationsProvider;

  private final Provider<ConnectionRepository> connectionsProvider;

  private final Provider<MemoryRepository> memoryProvider;

  private final Provider<AgentRegistry> agentsProvider;

  private final Provider<NgSignalProStatusProvider> ngSignalProProvider;

  private final Provider<SettingsRepository> settingsRepositoryProvider;

  private final Provider<GitHubStatusProvider> gitHubProvider;

  public ExecutiveBriefingEngine_Factory(Provider<ProjectRepository> projectsProvider,
      Provider<ApprovalRepository> approvalsProvider,
      Provider<NotificationRepository> notificationsProvider,
      Provider<ConnectionRepository> connectionsProvider, Provider<MemoryRepository> memoryProvider,
      Provider<AgentRegistry> agentsProvider,
      Provider<NgSignalProStatusProvider> ngSignalProProvider,
      Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<GitHubStatusProvider> gitHubProvider) {
    this.projectsProvider = projectsProvider;
    this.approvalsProvider = approvalsProvider;
    this.notificationsProvider = notificationsProvider;
    this.connectionsProvider = connectionsProvider;
    this.memoryProvider = memoryProvider;
    this.agentsProvider = agentsProvider;
    this.ngSignalProProvider = ngSignalProProvider;
    this.settingsRepositoryProvider = settingsRepositoryProvider;
    this.gitHubProvider = gitHubProvider;
  }

  @Override
  public ExecutiveBriefingEngine get() {
    return newInstance(projectsProvider.get(), approvalsProvider.get(), notificationsProvider.get(), connectionsProvider.get(), memoryProvider.get(), agentsProvider.get(), ngSignalProProvider.get(), settingsRepositoryProvider.get(), gitHubProvider.get());
  }

  public static ExecutiveBriefingEngine_Factory create(Provider<ProjectRepository> projectsProvider,
      Provider<ApprovalRepository> approvalsProvider,
      Provider<NotificationRepository> notificationsProvider,
      Provider<ConnectionRepository> connectionsProvider, Provider<MemoryRepository> memoryProvider,
      Provider<AgentRegistry> agentsProvider,
      Provider<NgSignalProStatusProvider> ngSignalProProvider,
      Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<GitHubStatusProvider> gitHubProvider) {
    return new ExecutiveBriefingEngine_Factory(projectsProvider, approvalsProvider, notificationsProvider, connectionsProvider, memoryProvider, agentsProvider, ngSignalProProvider, settingsRepositoryProvider, gitHubProvider);
  }

  public static ExecutiveBriefingEngine newInstance(ProjectRepository projects,
      ApprovalRepository approvals, NotificationRepository notifications,
      ConnectionRepository connections, MemoryRepository memory, AgentRegistry agents,
      NgSignalProStatusProvider ngSignalPro, SettingsRepository settingsRepository,
      GitHubStatusProvider gitHub) {
    return new ExecutiveBriefingEngine(projects, approvals, notifications, connections, memory, agents, ngSignalPro, settingsRepository, gitHub);
  }
}

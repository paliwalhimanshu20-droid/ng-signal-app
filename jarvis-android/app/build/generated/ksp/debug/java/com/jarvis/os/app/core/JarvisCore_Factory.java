package com.jarvis.os.app.core;

import com.jarvis.os.app.core.agents.WatchTowerOrchestrator;
import com.jarvis.os.app.core.intelligence.ContextManager;
import com.jarvis.os.app.core.intelligence.ExecutiveBriefingEngine;
import com.jarvis.os.app.core.intelligence.IntentRouter;
import com.jarvis.os.app.core.intelligence.JarvisDecisionEngine;
import com.jarvis.os.app.data.repository.ApprovalRepository;
import com.jarvis.os.app.data.repository.AuditRepository;
import com.jarvis.os.app.data.repository.ChatRepository;
import com.jarvis.os.app.data.repository.ConnectionRepository;
import com.jarvis.os.app.data.repository.MemoryRepository;
import com.jarvis.os.app.data.repository.NotificationRepository;
import com.jarvis.os.app.data.repository.ProjectRepository;
import com.jarvis.os.app.data.repository.ToolRepository;
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
public final class JarvisCore_Factory implements Factory<JarvisCore> {
  private final Provider<ConnectionRepository> connectionsProvider;

  private final Provider<ApprovalRepository> approvalsProvider;

  private final Provider<MemoryRepository> memoryProvider;

  private final Provider<ProjectRepository> projectsProvider;

  private final Provider<ChatRepository> chatProvider;

  private final Provider<NotificationRepository> notificationsProvider;

  private final Provider<ToolRepository> toolsProvider;

  private final Provider<AuditRepository> auditProvider;

  private final Provider<ContextManager> contextManagerProvider;

  private final Provider<JarvisDecisionEngine> decisionEngineProvider;

  private final Provider<IntentRouter> intentRouterProvider;

  private final Provider<WatchTowerOrchestrator> watchTowerProvider;

  private final Provider<ExecutiveBriefingEngine> briefingEngineProvider;

  private final Provider<CoroutineScope> appScopeProvider;

  public JarvisCore_Factory(Provider<ConnectionRepository> connectionsProvider,
      Provider<ApprovalRepository> approvalsProvider, Provider<MemoryRepository> memoryProvider,
      Provider<ProjectRepository> projectsProvider, Provider<ChatRepository> chatProvider,
      Provider<NotificationRepository> notificationsProvider,
      Provider<ToolRepository> toolsProvider, Provider<AuditRepository> auditProvider,
      Provider<ContextManager> contextManagerProvider,
      Provider<JarvisDecisionEngine> decisionEngineProvider,
      Provider<IntentRouter> intentRouterProvider,
      Provider<WatchTowerOrchestrator> watchTowerProvider,
      Provider<ExecutiveBriefingEngine> briefingEngineProvider,
      Provider<CoroutineScope> appScopeProvider) {
    this.connectionsProvider = connectionsProvider;
    this.approvalsProvider = approvalsProvider;
    this.memoryProvider = memoryProvider;
    this.projectsProvider = projectsProvider;
    this.chatProvider = chatProvider;
    this.notificationsProvider = notificationsProvider;
    this.toolsProvider = toolsProvider;
    this.auditProvider = auditProvider;
    this.contextManagerProvider = contextManagerProvider;
    this.decisionEngineProvider = decisionEngineProvider;
    this.intentRouterProvider = intentRouterProvider;
    this.watchTowerProvider = watchTowerProvider;
    this.briefingEngineProvider = briefingEngineProvider;
    this.appScopeProvider = appScopeProvider;
  }

  @Override
  public JarvisCore get() {
    return newInstance(connectionsProvider.get(), approvalsProvider.get(), memoryProvider.get(), projectsProvider.get(), chatProvider.get(), notificationsProvider.get(), toolsProvider.get(), auditProvider.get(), contextManagerProvider.get(), decisionEngineProvider.get(), intentRouterProvider.get(), watchTowerProvider.get(), briefingEngineProvider.get(), appScopeProvider.get());
  }

  public static JarvisCore_Factory create(Provider<ConnectionRepository> connectionsProvider,
      Provider<ApprovalRepository> approvalsProvider, Provider<MemoryRepository> memoryProvider,
      Provider<ProjectRepository> projectsProvider, Provider<ChatRepository> chatProvider,
      Provider<NotificationRepository> notificationsProvider,
      Provider<ToolRepository> toolsProvider, Provider<AuditRepository> auditProvider,
      Provider<ContextManager> contextManagerProvider,
      Provider<JarvisDecisionEngine> decisionEngineProvider,
      Provider<IntentRouter> intentRouterProvider,
      Provider<WatchTowerOrchestrator> watchTowerProvider,
      Provider<ExecutiveBriefingEngine> briefingEngineProvider,
      Provider<CoroutineScope> appScopeProvider) {
    return new JarvisCore_Factory(connectionsProvider, approvalsProvider, memoryProvider, projectsProvider, chatProvider, notificationsProvider, toolsProvider, auditProvider, contextManagerProvider, decisionEngineProvider, intentRouterProvider, watchTowerProvider, briefingEngineProvider, appScopeProvider);
  }

  public static JarvisCore newInstance(ConnectionRepository connections,
      ApprovalRepository approvals, MemoryRepository memory, ProjectRepository projects,
      ChatRepository chat, NotificationRepository notifications, ToolRepository tools,
      AuditRepository audit, ContextManager contextManager, JarvisDecisionEngine decisionEngine,
      IntentRouter intentRouter, WatchTowerOrchestrator watchTower,
      ExecutiveBriefingEngine briefingEngine, CoroutineScope appScope) {
    return new JarvisCore(connections, approvals, memory, projects, chat, notifications, tools, audit, contextManager, decisionEngine, intentRouter, watchTower, briefingEngine, appScope);
  }
}

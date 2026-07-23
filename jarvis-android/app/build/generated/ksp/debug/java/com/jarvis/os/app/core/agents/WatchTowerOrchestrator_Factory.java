package com.jarvis.os.app.core.agents;

import com.jarvis.os.app.data.repository.ApprovalRepository;
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
public final class WatchTowerOrchestrator_Factory implements Factory<WatchTowerOrchestrator> {
  private final Provider<MultiAiCoordinator> coordinatorProvider;

  private final Provider<ApprovalRepository> approvalsProvider;

  public WatchTowerOrchestrator_Factory(Provider<MultiAiCoordinator> coordinatorProvider,
      Provider<ApprovalRepository> approvalsProvider) {
    this.coordinatorProvider = coordinatorProvider;
    this.approvalsProvider = approvalsProvider;
  }

  @Override
  public WatchTowerOrchestrator get() {
    return newInstance(coordinatorProvider.get(), approvalsProvider.get());
  }

  public static WatchTowerOrchestrator_Factory create(
      Provider<MultiAiCoordinator> coordinatorProvider,
      Provider<ApprovalRepository> approvalsProvider) {
    return new WatchTowerOrchestrator_Factory(coordinatorProvider, approvalsProvider);
  }

  public static WatchTowerOrchestrator newInstance(MultiAiCoordinator coordinator,
      ApprovalRepository approvals) {
    return new WatchTowerOrchestrator(coordinator, approvals);
  }
}

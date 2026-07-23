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
public final class DefaultMultiAiCoordinator_Factory implements Factory<DefaultMultiAiCoordinator> {
  private final Provider<AgentRegistry> registryProvider;

  private final Provider<ApprovalRepository> approvalsProvider;

  public DefaultMultiAiCoordinator_Factory(Provider<AgentRegistry> registryProvider,
      Provider<ApprovalRepository> approvalsProvider) {
    this.registryProvider = registryProvider;
    this.approvalsProvider = approvalsProvider;
  }

  @Override
  public DefaultMultiAiCoordinator get() {
    return newInstance(registryProvider.get(), approvalsProvider.get());
  }

  public static DefaultMultiAiCoordinator_Factory create(Provider<AgentRegistry> registryProvider,
      Provider<ApprovalRepository> approvalsProvider) {
    return new DefaultMultiAiCoordinator_Factory(registryProvider, approvalsProvider);
  }

  public static DefaultMultiAiCoordinator newInstance(AgentRegistry registry,
      ApprovalRepository approvals) {
    return new DefaultMultiAiCoordinator(registry, approvals);
  }
}

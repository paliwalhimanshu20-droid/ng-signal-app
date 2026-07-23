package com.jarvis.os.app.data.repository;

import com.jarvis.os.app.core.tools.Tool;
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
public final class MockToolRepository_Factory implements Factory<MockToolRepository> {
  private final Provider<Set<Tool>> toolsProvider;

  private final Provider<ApprovalRepository> approvalsProvider;

  public MockToolRepository_Factory(Provider<Set<Tool>> toolsProvider,
      Provider<ApprovalRepository> approvalsProvider) {
    this.toolsProvider = toolsProvider;
    this.approvalsProvider = approvalsProvider;
  }

  @Override
  public MockToolRepository get() {
    return newInstance(toolsProvider.get(), approvalsProvider.get());
  }

  public static MockToolRepository_Factory create(Provider<Set<Tool>> toolsProvider,
      Provider<ApprovalRepository> approvalsProvider) {
    return new MockToolRepository_Factory(toolsProvider, approvalsProvider);
  }

  public static MockToolRepository newInstance(Set<Tool> tools, ApprovalRepository approvals) {
    return new MockToolRepository(tools, approvals);
  }
}

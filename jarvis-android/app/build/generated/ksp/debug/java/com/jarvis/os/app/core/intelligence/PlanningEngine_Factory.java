package com.jarvis.os.app.core.intelligence;

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
public final class PlanningEngine_Factory implements Factory<PlanningEngine> {
  private final Provider<ToolRepository> toolsProvider;

  public PlanningEngine_Factory(Provider<ToolRepository> toolsProvider) {
    this.toolsProvider = toolsProvider;
  }

  @Override
  public PlanningEngine get() {
    return newInstance(toolsProvider.get());
  }

  public static PlanningEngine_Factory create(Provider<ToolRepository> toolsProvider) {
    return new PlanningEngine_Factory(toolsProvider);
  }

  public static PlanningEngine newInstance(ToolRepository tools) {
    return new PlanningEngine(tools);
  }
}

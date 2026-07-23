package com.jarvis.os.app.core.workflow;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class DefaultWorkflowEngine_Factory implements Factory<DefaultWorkflowEngine> {
  @Override
  public DefaultWorkflowEngine get() {
    return newInstance();
  }

  public static DefaultWorkflowEngine_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static DefaultWorkflowEngine newInstance() {
    return new DefaultWorkflowEngine();
  }

  private static final class InstanceHolder {
    private static final DefaultWorkflowEngine_Factory INSTANCE = new DefaultWorkflowEngine_Factory();
  }
}

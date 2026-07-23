package com.jarvis.os.app.core.tools;

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
public final class ProjectNoteTool_Factory implements Factory<ProjectNoteTool> {
  @Override
  public ProjectNoteTool get() {
    return newInstance();
  }

  public static ProjectNoteTool_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ProjectNoteTool newInstance() {
    return new ProjectNoteTool();
  }

  private static final class InstanceHolder {
    private static final ProjectNoteTool_Factory INSTANCE = new ProjectNoteTool_Factory();
  }
}

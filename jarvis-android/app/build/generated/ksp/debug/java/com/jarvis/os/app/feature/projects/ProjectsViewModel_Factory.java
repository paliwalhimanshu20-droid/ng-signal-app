package com.jarvis.os.app.feature.projects;

import com.jarvis.os.app.data.repository.ProjectRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class ProjectsViewModel_Factory implements Factory<ProjectsViewModel> {
  private final Provider<ProjectRepository> repositoryProvider;

  public ProjectsViewModel_Factory(Provider<ProjectRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public ProjectsViewModel get() {
    return newInstance(repositoryProvider.get());
  }

  public static ProjectsViewModel_Factory create(Provider<ProjectRepository> repositoryProvider) {
    return new ProjectsViewModel_Factory(repositoryProvider);
  }

  public static ProjectsViewModel newInstance(ProjectRepository repository) {
    return new ProjectsViewModel(repository);
  }
}

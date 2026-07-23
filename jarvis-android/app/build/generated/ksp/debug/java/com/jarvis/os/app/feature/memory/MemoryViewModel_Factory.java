package com.jarvis.os.app.feature.memory;

import com.jarvis.os.app.data.repository.MemoryRepository;
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
public final class MemoryViewModel_Factory implements Factory<MemoryViewModel> {
  private final Provider<MemoryRepository> repositoryProvider;

  public MemoryViewModel_Factory(Provider<MemoryRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public MemoryViewModel get() {
    return newInstance(repositoryProvider.get());
  }

  public static MemoryViewModel_Factory create(Provider<MemoryRepository> repositoryProvider) {
    return new MemoryViewModel_Factory(repositoryProvider);
  }

  public static MemoryViewModel newInstance(MemoryRepository repository) {
    return new MemoryViewModel(repository);
  }
}

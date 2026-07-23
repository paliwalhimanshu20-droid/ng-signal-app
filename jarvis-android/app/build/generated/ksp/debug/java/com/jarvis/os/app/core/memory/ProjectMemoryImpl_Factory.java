package com.jarvis.os.app.core.memory;

import com.jarvis.os.app.data.repository.MemoryRepository;
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
public final class ProjectMemoryImpl_Factory implements Factory<ProjectMemoryImpl> {
  private final Provider<MemoryRepository> memoryProvider;

  public ProjectMemoryImpl_Factory(Provider<MemoryRepository> memoryProvider) {
    this.memoryProvider = memoryProvider;
  }

  @Override
  public ProjectMemoryImpl get() {
    return newInstance(memoryProvider.get());
  }

  public static ProjectMemoryImpl_Factory create(Provider<MemoryRepository> memoryProvider) {
    return new ProjectMemoryImpl_Factory(memoryProvider);
  }

  public static ProjectMemoryImpl newInstance(MemoryRepository memory) {
    return new ProjectMemoryImpl(memory);
  }
}

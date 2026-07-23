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
public final class AgentMemoryImpl_Factory implements Factory<AgentMemoryImpl> {
  private final Provider<MemoryRepository> memoryProvider;

  public AgentMemoryImpl_Factory(Provider<MemoryRepository> memoryProvider) {
    this.memoryProvider = memoryProvider;
  }

  @Override
  public AgentMemoryImpl get() {
    return newInstance(memoryProvider.get());
  }

  public static AgentMemoryImpl_Factory create(Provider<MemoryRepository> memoryProvider) {
    return new AgentMemoryImpl_Factory(memoryProvider);
  }

  public static AgentMemoryImpl newInstance(MemoryRepository memory) {
    return new AgentMemoryImpl(memory);
  }
}

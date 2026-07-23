package com.jarvis.os.app.core.intelligence;

import com.jarvis.os.app.core.memory.ConversationMemory;
import com.jarvis.os.app.core.memory.PersonalMemory;
import com.jarvis.os.app.data.repository.ChatRepository;
import com.jarvis.os.app.data.repository.ProjectRepository;
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
public final class ContextManager_Factory implements Factory<ContextManager> {
  private final Provider<ConversationMemory> conversationMemoryProvider;

  private final Provider<PersonalMemory> personalMemoryProvider;

  private final Provider<ChatRepository> chatProvider;

  private final Provider<ProjectRepository> projectsProvider;

  public ContextManager_Factory(Provider<ConversationMemory> conversationMemoryProvider,
      Provider<PersonalMemory> personalMemoryProvider, Provider<ChatRepository> chatProvider,
      Provider<ProjectRepository> projectsProvider) {
    this.conversationMemoryProvider = conversationMemoryProvider;
    this.personalMemoryProvider = personalMemoryProvider;
    this.chatProvider = chatProvider;
    this.projectsProvider = projectsProvider;
  }

  @Override
  public ContextManager get() {
    return newInstance(conversationMemoryProvider.get(), personalMemoryProvider.get(), chatProvider.get(), projectsProvider.get());
  }

  public static ContextManager_Factory create(
      Provider<ConversationMemory> conversationMemoryProvider,
      Provider<PersonalMemory> personalMemoryProvider, Provider<ChatRepository> chatProvider,
      Provider<ProjectRepository> projectsProvider) {
    return new ContextManager_Factory(conversationMemoryProvider, personalMemoryProvider, chatProvider, projectsProvider);
  }

  public static ContextManager newInstance(ConversationMemory conversationMemory,
      PersonalMemory personalMemory, ChatRepository chat, ProjectRepository projects) {
    return new ContextManager(conversationMemory, personalMemory, chat, projects);
  }
}

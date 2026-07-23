package com.jarvis.os.app.data.repository;

import com.jarvis.os.app.core.chat.AiRouter;
import com.jarvis.os.app.core.chat.ChatSessionManager;
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
public final class MockChatRepository_Factory implements Factory<MockChatRepository> {
  private final Provider<AiRouter> routerProvider;

  private final Provider<ChatSessionManager> sessionManagerProvider;

  public MockChatRepository_Factory(Provider<AiRouter> routerProvider,
      Provider<ChatSessionManager> sessionManagerProvider) {
    this.routerProvider = routerProvider;
    this.sessionManagerProvider = sessionManagerProvider;
  }

  @Override
  public MockChatRepository get() {
    return newInstance(routerProvider.get(), sessionManagerProvider.get());
  }

  public static MockChatRepository_Factory create(Provider<AiRouter> routerProvider,
      Provider<ChatSessionManager> sessionManagerProvider) {
    return new MockChatRepository_Factory(routerProvider, sessionManagerProvider);
  }

  public static MockChatRepository newInstance(AiRouter router, ChatSessionManager sessionManager) {
    return new MockChatRepository(router, sessionManager);
  }
}

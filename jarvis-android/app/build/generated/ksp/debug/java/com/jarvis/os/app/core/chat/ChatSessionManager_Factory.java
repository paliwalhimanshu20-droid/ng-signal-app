package com.jarvis.os.app.core.chat;

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
public final class ChatSessionManager_Factory implements Factory<ChatSessionManager> {
  @Override
  public ChatSessionManager get() {
    return newInstance();
  }

  public static ChatSessionManager_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ChatSessionManager newInstance() {
    return new ChatSessionManager();
  }

  private static final class InstanceHolder {
    private static final ChatSessionManager_Factory INSTANCE = new ChatSessionManager_Factory();
  }
}

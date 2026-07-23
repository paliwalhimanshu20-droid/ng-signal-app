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
public final class KeywordIntentRouter_Factory implements Factory<KeywordIntentRouter> {
  private final Provider<ToolRepository> toolsProvider;

  public KeywordIntentRouter_Factory(Provider<ToolRepository> toolsProvider) {
    this.toolsProvider = toolsProvider;
  }

  @Override
  public KeywordIntentRouter get() {
    return newInstance(toolsProvider.get());
  }

  public static KeywordIntentRouter_Factory create(Provider<ToolRepository> toolsProvider) {
    return new KeywordIntentRouter_Factory(toolsProvider);
  }

  public static KeywordIntentRouter newInstance(ToolRepository tools) {
    return new KeywordIntentRouter(tools);
  }
}

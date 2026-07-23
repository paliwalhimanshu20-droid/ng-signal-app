package com.jarvis.os.app.core.agents;

import com.jarvis.os.app.core.chat.AiRouter;
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
public final class FlashAgent_Factory implements Factory<FlashAgent> {
  private final Provider<AiRouter> routerProvider;

  public FlashAgent_Factory(Provider<AiRouter> routerProvider) {
    this.routerProvider = routerProvider;
  }

  @Override
  public FlashAgent get() {
    return newInstance(routerProvider.get());
  }

  public static FlashAgent_Factory create(Provider<AiRouter> routerProvider) {
    return new FlashAgent_Factory(routerProvider);
  }

  public static FlashAgent newInstance(AiRouter router) {
    return new FlashAgent(router);
  }
}

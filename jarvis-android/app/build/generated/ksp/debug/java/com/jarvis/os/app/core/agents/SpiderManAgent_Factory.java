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
public final class SpiderManAgent_Factory implements Factory<SpiderManAgent> {
  private final Provider<AiRouter> routerProvider;

  public SpiderManAgent_Factory(Provider<AiRouter> routerProvider) {
    this.routerProvider = routerProvider;
  }

  @Override
  public SpiderManAgent get() {
    return newInstance(routerProvider.get());
  }

  public static SpiderManAgent_Factory create(Provider<AiRouter> routerProvider) {
    return new SpiderManAgent_Factory(routerProvider);
  }

  public static SpiderManAgent newInstance(AiRouter router) {
    return new SpiderManAgent(router);
  }
}

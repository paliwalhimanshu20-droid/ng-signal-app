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
public final class BatmanAgent_Factory implements Factory<BatmanAgent> {
  private final Provider<AiRouter> routerProvider;

  public BatmanAgent_Factory(Provider<AiRouter> routerProvider) {
    this.routerProvider = routerProvider;
  }

  @Override
  public BatmanAgent get() {
    return newInstance(routerProvider.get());
  }

  public static BatmanAgent_Factory create(Provider<AiRouter> routerProvider) {
    return new BatmanAgent_Factory(routerProvider);
  }

  public static BatmanAgent newInstance(AiRouter router) {
    return new BatmanAgent(router);
  }
}

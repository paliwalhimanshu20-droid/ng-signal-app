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
public final class NickFuryAgent_Factory implements Factory<NickFuryAgent> {
  private final Provider<AiRouter> routerProvider;

  public NickFuryAgent_Factory(Provider<AiRouter> routerProvider) {
    this.routerProvider = routerProvider;
  }

  @Override
  public NickFuryAgent get() {
    return newInstance(routerProvider.get());
  }

  public static NickFuryAgent_Factory create(Provider<AiRouter> routerProvider) {
    return new NickFuryAgent_Factory(routerProvider);
  }

  public static NickFuryAgent newInstance(AiRouter router) {
    return new NickFuryAgent(router);
  }
}

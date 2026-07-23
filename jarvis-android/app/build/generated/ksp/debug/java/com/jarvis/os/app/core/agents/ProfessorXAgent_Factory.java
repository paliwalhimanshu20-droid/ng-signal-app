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
public final class ProfessorXAgent_Factory implements Factory<ProfessorXAgent> {
  private final Provider<AiRouter> routerProvider;

  public ProfessorXAgent_Factory(Provider<AiRouter> routerProvider) {
    this.routerProvider = routerProvider;
  }

  @Override
  public ProfessorXAgent get() {
    return newInstance(routerProvider.get());
  }

  public static ProfessorXAgent_Factory create(Provider<AiRouter> routerProvider) {
    return new ProfessorXAgent_Factory(routerProvider);
  }

  public static ProfessorXAgent newInstance(AiRouter router) {
    return new ProfessorXAgent(router);
  }
}

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
public final class DoctorStrangeAgent_Factory implements Factory<DoctorStrangeAgent> {
  private final Provider<AiRouter> routerProvider;

  public DoctorStrangeAgent_Factory(Provider<AiRouter> routerProvider) {
    this.routerProvider = routerProvider;
  }

  @Override
  public DoctorStrangeAgent get() {
    return newInstance(routerProvider.get());
  }

  public static DoctorStrangeAgent_Factory create(Provider<AiRouter> routerProvider) {
    return new DoctorStrangeAgent_Factory(routerProvider);
  }

  public static DoctorStrangeAgent newInstance(AiRouter router) {
    return new DoctorStrangeAgent(router);
  }
}

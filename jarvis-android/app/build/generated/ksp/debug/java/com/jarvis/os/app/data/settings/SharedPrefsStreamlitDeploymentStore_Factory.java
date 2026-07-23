package com.jarvis.os.app.data.settings;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class SharedPrefsStreamlitDeploymentStore_Factory implements Factory<SharedPrefsStreamlitDeploymentStore> {
  private final Provider<Context> contextProvider;

  public SharedPrefsStreamlitDeploymentStore_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public SharedPrefsStreamlitDeploymentStore get() {
    return newInstance(contextProvider.get());
  }

  public static SharedPrefsStreamlitDeploymentStore_Factory create(
      Provider<Context> contextProvider) {
    return new SharedPrefsStreamlitDeploymentStore_Factory(contextProvider);
  }

  public static SharedPrefsStreamlitDeploymentStore newInstance(Context context) {
    return new SharedPrefsStreamlitDeploymentStore(context);
  }
}

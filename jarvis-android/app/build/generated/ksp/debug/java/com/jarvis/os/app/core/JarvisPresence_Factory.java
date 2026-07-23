package com.jarvis.os.app.core;

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
public final class JarvisPresence_Factory implements Factory<JarvisPresence> {
  @Override
  public JarvisPresence get() {
    return newInstance();
  }

  public static JarvisPresence_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static JarvisPresence newInstance() {
    return new JarvisPresence();
  }

  private static final class InstanceHolder {
    private static final JarvisPresence_Factory INSTANCE = new JarvisPresence_Factory();
  }
}

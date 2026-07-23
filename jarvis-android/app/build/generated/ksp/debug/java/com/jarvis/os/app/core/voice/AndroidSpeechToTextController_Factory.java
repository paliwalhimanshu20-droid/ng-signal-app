package com.jarvis.os.app.core.voice;

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
public final class AndroidSpeechToTextController_Factory implements Factory<AndroidSpeechToTextController> {
  private final Provider<Context> contextProvider;

  public AndroidSpeechToTextController_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public AndroidSpeechToTextController get() {
    return newInstance(contextProvider.get());
  }

  public static AndroidSpeechToTextController_Factory create(Provider<Context> contextProvider) {
    return new AndroidSpeechToTextController_Factory(contextProvider);
  }

  public static AndroidSpeechToTextController newInstance(Context context) {
    return new AndroidSpeechToTextController(context);
  }
}

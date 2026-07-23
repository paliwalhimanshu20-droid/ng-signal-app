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
public final class EncryptedGeminiKeyStore_Factory implements Factory<EncryptedGeminiKeyStore> {
  private final Provider<Context> contextProvider;

  public EncryptedGeminiKeyStore_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public EncryptedGeminiKeyStore get() {
    return newInstance(contextProvider.get());
  }

  public static EncryptedGeminiKeyStore_Factory create(Provider<Context> contextProvider) {
    return new EncryptedGeminiKeyStore_Factory(contextProvider);
  }

  public static EncryptedGeminiKeyStore newInstance(Context context) {
    return new EncryptedGeminiKeyStore(context);
  }
}

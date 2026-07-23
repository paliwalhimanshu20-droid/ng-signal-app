package com.jarvis.os.app.designsystem.avatar;

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
public final class DrawableAvatarAssetProvider_Factory implements Factory<DrawableAvatarAssetProvider> {
  @Override
  public DrawableAvatarAssetProvider get() {
    return newInstance();
  }

  public static DrawableAvatarAssetProvider_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static DrawableAvatarAssetProvider newInstance() {
    return new DrawableAvatarAssetProvider();
  }

  private static final class InstanceHolder {
    private static final DrawableAvatarAssetProvider_Factory INSTANCE = new DrawableAvatarAssetProvider_Factory();
  }
}

package com.jarvis.os.app.feature.approvals;

import com.jarvis.os.app.core.JarvisCore;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class ApprovalCenterViewModel_Factory implements Factory<ApprovalCenterViewModel> {
  private final Provider<JarvisCore> coreProvider;

  public ApprovalCenterViewModel_Factory(Provider<JarvisCore> coreProvider) {
    this.coreProvider = coreProvider;
  }

  @Override
  public ApprovalCenterViewModel get() {
    return newInstance(coreProvider.get());
  }

  public static ApprovalCenterViewModel_Factory create(Provider<JarvisCore> coreProvider) {
    return new ApprovalCenterViewModel_Factory(coreProvider);
  }

  public static ApprovalCenterViewModel newInstance(JarvisCore core) {
    return new ApprovalCenterViewModel(core);
  }
}

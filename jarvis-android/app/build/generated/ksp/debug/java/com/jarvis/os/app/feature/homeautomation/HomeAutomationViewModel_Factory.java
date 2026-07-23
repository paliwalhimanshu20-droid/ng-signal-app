package com.jarvis.os.app.feature.homeautomation;

import com.jarvis.os.app.data.repository.HomeAutomationRepository;
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
public final class HomeAutomationViewModel_Factory implements Factory<HomeAutomationViewModel> {
  private final Provider<HomeAutomationRepository> repositoryProvider;

  public HomeAutomationViewModel_Factory(Provider<HomeAutomationRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public HomeAutomationViewModel get() {
    return newInstance(repositoryProvider.get());
  }

  public static HomeAutomationViewModel_Factory create(
      Provider<HomeAutomationRepository> repositoryProvider) {
    return new HomeAutomationViewModel_Factory(repositoryProvider);
  }

  public static HomeAutomationViewModel newInstance(HomeAutomationRepository repository) {
    return new HomeAutomationViewModel(repository);
  }
}

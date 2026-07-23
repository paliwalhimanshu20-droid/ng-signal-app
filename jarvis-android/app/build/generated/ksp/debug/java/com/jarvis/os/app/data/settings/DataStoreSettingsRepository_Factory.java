package com.jarvis.os.app.data.settings;

import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
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
public final class DataStoreSettingsRepository_Factory implements Factory<DataStoreSettingsRepository> {
  private final Provider<DataStore<Preferences>> dataStoreProvider;

  public DataStoreSettingsRepository_Factory(Provider<DataStore<Preferences>> dataStoreProvider) {
    this.dataStoreProvider = dataStoreProvider;
  }

  @Override
  public DataStoreSettingsRepository get() {
    return newInstance(dataStoreProvider.get());
  }

  public static DataStoreSettingsRepository_Factory create(
      Provider<DataStore<Preferences>> dataStoreProvider) {
    return new DataStoreSettingsRepository_Factory(dataStoreProvider);
  }

  public static DataStoreSettingsRepository newInstance(DataStore<Preferences> dataStore) {
    return new DataStoreSettingsRepository(dataStore);
  }
}

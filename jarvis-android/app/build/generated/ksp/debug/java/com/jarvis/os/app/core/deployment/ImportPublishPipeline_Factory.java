package com.jarvis.os.app.core.deployment;

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
public final class ImportPublishPipeline_Factory implements Factory<ImportPublishPipeline> {
  private final Provider<PackageExtractor> extractorProvider;

  private final Provider<ChangeAnalyzer> changeAnalyzerProvider;

  private final Provider<SecurityScanner> securityScannerProvider;

  public ImportPublishPipeline_Factory(Provider<PackageExtractor> extractorProvider,
      Provider<ChangeAnalyzer> changeAnalyzerProvider,
      Provider<SecurityScanner> securityScannerProvider) {
    this.extractorProvider = extractorProvider;
    this.changeAnalyzerProvider = changeAnalyzerProvider;
    this.securityScannerProvider = securityScannerProvider;
  }

  @Override
  public ImportPublishPipeline get() {
    return newInstance(extractorProvider.get(), changeAnalyzerProvider.get(), securityScannerProvider.get());
  }

  public static ImportPublishPipeline_Factory create(Provider<PackageExtractor> extractorProvider,
      Provider<ChangeAnalyzer> changeAnalyzerProvider,
      Provider<SecurityScanner> securityScannerProvider) {
    return new ImportPublishPipeline_Factory(extractorProvider, changeAnalyzerProvider, securityScannerProvider);
  }

  public static ImportPublishPipeline newInstance(PackageExtractor extractor,
      ChangeAnalyzer changeAnalyzer, SecurityScanner securityScanner) {
    return new ImportPublishPipeline(extractor, changeAnalyzer, securityScanner);
  }
}

package com.jarvis.os.app.core.tools;

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
public final class CalculatorTool_Factory implements Factory<CalculatorTool> {
  @Override
  public CalculatorTool get() {
    return newInstance();
  }

  public static CalculatorTool_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static CalculatorTool newInstance() {
    return new CalculatorTool();
  }

  private static final class InstanceHolder {
    private static final CalculatorTool_Factory INSTANCE = new CalculatorTool_Factory();
  }
}

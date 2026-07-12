// Root build file. Per-module plugin versions are declared once here
// (Gradle's "plugins {}" block resolution) and applied without a
// version in app/build.gradle.kts — this is what keeps a future
// multi-module split (design-system, data, feature modules) from ever
// having two modules silently drift onto different plugin versions.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}

// APPLIED — this snippet has been merged into app/build.gradle.kts's real
// `dependencies { }` block (see the Room entries there and in
// gradle/libs.versions.toml). This file was originally left as a
// commented-out reference and was never actually wired in, which is why
// every androidx.room.* reference across com.jarvis.tidb was unresolved
// in CI. Kept here only as a historical note; safe to delete.
//
// Dependency snippet for Module 1 — Core Market Foundation.
// Merge into the app/module-level build.gradle.kts `dependencies { }` block.
// Versions are illustrative; align with whatever BOM/version catalog JARVIS already uses.

/*
dependencies {
    val roomVersion = "2.6.1"
    val coroutinesVersion = "1.8.1"

    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")

    androidTestImplementation("androidx.room:room-testing:$roomVersion")
}
*/

// Requires the KSP plugin in the module's plugins block:
// plugins {
//     id("com.google.devtools.ksp")
// }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.jarvis.os.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jarvis.os.app"
        minSdk = 26 // Android 8.0 — required for adaptive icons and the notification channel APIs Part 10 needs
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-sprint7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false // left off until Sprint-8+ adds obfuscation review; correctness over size at this stage
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)

    // "JARVIS Living Avatar" sprint: Lottie is the primary asset format
    // the Avatar Engine renders -- see AvatarAssetProvider's own
    // docstring for why Lottie over Rive.
    // Sprint 12 "Real AI Conversation": OkHttp for the real streaming
    // ChatProvider (SSE), security-crypto for encrypted API key storage
    // -- see core/chat/OpenAiCompatibleChatProvider.kt and
    // data/settings/ApiKeyStore.kt.
    implementation(libs.okhttp)
    implementation(libs.security.crypto)

    // "JARVIS Living Avatar" sprint: Lottie is the primary asset format
    // the Avatar Engine renders -- see AvatarAssetProvider's own
    // docstring for why Lottie over Rive.
    implementation(libs.lottie.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

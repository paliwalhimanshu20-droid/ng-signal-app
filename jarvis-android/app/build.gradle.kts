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

        // Sprint 13 "Production Google Workspace Authentication": AppAuth's
        // RedirectUriReceiverActivity is merged in from its own manifest
        // (see the appauth dependency below) -- it reads this placeholder
        // to build its intent-filter, so no manual AndroidManifest.xml
        // edit is needed for the OAuth redirect itself. Must match
        // GoogleOAuthConfig.REDIRECT_URI's scheme exactly.
        manifestPlaceholders["appAuthRedirectScheme"] = "com.jarvis.os.app"
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

    // Sprint 13 "Production Google Workspace Authentication": AppAuth is
    // the standard, actively-maintained OAuth2/OIDC client for Android
    // (Chrome Custom Tabs + PKCE) -- the official Google Identity/OAuth
    // library for this exact "native app, no client secret, real
    // refresh-token storage" flow. See core/security/GoogleAuthManager.kt.
    implementation("net.openid:appauth:0.11.1")

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

    // JARVIS Trading Intelligence Database (TIDB) — was documented in
    // build.gradle.dependencies.kts but never actually merged in; that
    // left every androidx.room.* reference across com.jarvis.tidb
    // unresolved. Wiring it here for real.
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    androidTestImplementation(libs.room.testing)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

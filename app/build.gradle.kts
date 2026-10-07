plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.puneeee.voicecatcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.puneeee.voicecatcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    val backendUrl = providers.gradleProperty("VOICE_CATCHER_BACKEND_URL").orNull.orEmpty().replace("\"", "\\\"")
    val pilotToken = providers.gradleProperty("VOICE_CATCHER_PILOT_TOKEN").orNull.orEmpty().replace("\"", "\\\"")
    defaultConfig {
        buildConfigField("String", "BACKEND_BASE_URL", "\"$backendUrl\"")
        buildConfigField("String", "PILOT_TOKEN", "\"$pilotToken\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)
}

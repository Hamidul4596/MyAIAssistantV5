plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.myaiaassistant.v4"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.myaiaassistant.v4"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "4.0"
        buildConfigField("String", "BACKEND_BASE_URL", "\"https://YOUR-BACKEND.example.com\"")
    }
    buildFeatures { compose = true; buildConfig = true }
}
dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

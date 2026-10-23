plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.eqalarm"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.eqalarm"
        minSdk = 27
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Camera + ComponentActivity (нужен LifecycleOwner для CameraX)
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    // Определение позы тела; модель встроена в приложение, интернет не нужен
    implementation("com.google.mlkit:pose-detection:18.0.0-beta4")
}

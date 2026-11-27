plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.palya.rainbowkeyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.palya.rainbowkeyboard"
        minSdk = 23
        targetSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
        versionCode = 1
        versionName = "1.0"
    }
}

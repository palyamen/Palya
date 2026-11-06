plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

import java.util.Base64

val stableKeystoreBase64 = System.getenv("NETSPEED_KEYSTORE_BASE64")
val stableStorePassword = System.getenv("NETSPEED_STORE_PASSWORD")
val stableKeyAlias = System.getenv("NETSPEED_KEY_ALIAS")
val stableKeyPassword = System.getenv("NETSPEED_KEY_PASSWORD")
val hasStableSigning = listOf(stableKeystoreBase64, stableStorePassword, stableKeyAlias, stableKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "com.astra.netspeed"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.astra.netspeed"
        minSdk = 23
        targetSdk = 35
        versionCode = 3
        versionName = "3.0"
    }

    signingConfigs {
        if (hasStableSigning) {
            create("stable") {
                val keyFile = layout.buildDirectory.file("stable-signing/net-speed-upload-key.jks").get().asFile
                keyFile.parentFile.mkdirs()
                keyFile.writeBytes(Base64.getDecoder().decode(stableKeystoreBase64))
                storeFile = keyFile
                storePassword = stableStorePassword
                keyAlias = stableKeyAlias
                keyPassword = stableKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (hasStableSigning) {
                signingConfig = signingConfigs.getByName("stable")
            }
        }
        release {
            isMinifyEnabled = false
            if (hasStableSigning) {
                signingConfig = signingConfigs.getByName("stable")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

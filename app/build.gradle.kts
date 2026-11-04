plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.astraya.glyph"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.astraya.glyph"
        minSdk = 34
        targetSdk = 35
        versionCode = (System.getenv("BUILD_NUMBER") ?: "1").toInt()
        versionName = "1.0.${System.getenv("BUILD_NUMBER") ?: "0"}"
    }

    // One fixed key, so each new build installs over the last one on the phone.
    // CI decodes it from a repository secret; without it the build falls back
    // to the debug key.
    val keystorePath = System.getenv("ASTRAYA_KEYSTORE")
    signingConfigs {
        if (keystorePath != null) {
            create("astraya") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ASTRAYA_KEYSTORE_PASSWORD")
                keyAlias = "astraya"
                keyPassword = System.getenv("ASTRAYA_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("astraya") ?: signingConfigs.getByName("debug")
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

dependencies {
    implementation(files("libs/glyph-matrix-sdk-2.0.aar"))
}

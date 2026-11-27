// The Android application. AGP 9 compiles Kotlin itself, so no kotlin-android plugin.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    compileSdk = catalogVersion("android-compileSdk").toInt()

    defaultConfig {
        minSdk = catalogVersion("android-minSdk").toInt()
        targetSdk = catalogVersion("android-targetSdk").toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(catalogVersion("jvmTarget"))
        targetCompatibility = JavaVersion.toVersion(catalogVersion("jvmTarget"))
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

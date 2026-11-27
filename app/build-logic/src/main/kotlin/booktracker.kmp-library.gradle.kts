import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Base for every shared module: Android and iOS targets, no Compose. shared/core uses only this,
// so a Compose or Ktor import there does not compile.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    android {
        namespace = booktrackerNamespace
        compileSdk = catalogVersion("android-compileSdk").toInt()
        minSdk = catalogVersion("android-minSdk").toInt()
        compilerOptions { jvmTarget.set(JvmTarget.fromTarget(catalogVersion("jvmTarget"))) }
        // Unit tests of commonTest run on the JVM as Android host tests (testAndroidHostTest)
        withHostTest {}
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonTest.dependencies {
            implementation(catalogLibrary("kotlin-test"))
        }
    }
}

// The Android app: only the Activity, the Application and app resources. Screens are in shared/.
plugins {
    id("booktracker.android-app")
}

android {
    namespace = "com.sauerwein.booktracker.android"

    defaultConfig {
        applicationId = "com.sauerwein.booktracker"
        versionCode = 1
        versionName = "0.1.0"
    }

    // Four variants: devDebug, devRelease, prodDebug, prodRelease (D19). The dev app has its own
    // app id, so it installs next to the prod app (D21). BuildConfig.FLAVOR tells shared/app which one runs.
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        create("prod") {
            dimension = "environment"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the debug key until the release step sets up real signing
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(projects.shared.app)
    implementation(libs.androidx.activity.compose)
}

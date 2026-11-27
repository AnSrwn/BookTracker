// UI architecture shared by all features: ScreenViewModel, ScreenHost, ScreenDestination. No look:
// colors, type and components live in shared/ui/designsystem.
plugins {
    id("booktracker.kmp-compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:ui:navigation"))
            api(libs.lifecycle.viewmodel)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.lifecycle.runtime.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

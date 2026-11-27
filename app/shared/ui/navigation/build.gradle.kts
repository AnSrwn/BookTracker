// Navigation keys of every screen and the tab definitions. No UI: features reference each other only
// through these keys, never directly.
plugins {
    id("booktracker.kmp-library")
    id("booktracker.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Runtime only (NavKey, saved state): this module has no UI
            api(libs.navigation3.runtime)
            api(libs.kotlinx.serialization.core)
        }
    }
}

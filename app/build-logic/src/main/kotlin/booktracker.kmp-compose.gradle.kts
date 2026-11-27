// A shared module with Compose UI and Compose resources (strings, icons).
plugins {
    id("booktracker.kmp-library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    android {
        // Compose resources need Android resources in the KMP library plugin, or the app crashes at runtime
        androidResources { enable = true }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(catalogLibrary("compose-runtime"))
            implementation(catalogLibrary("compose-foundation"))
            implementation(catalogLibrary("compose-ui"))
            implementation(catalogLibrary("compose-material3"))
            implementation(catalogLibrary("compose-components-resources"))
        }
    }
}

compose.resources {
    packageOfResClass = "$booktrackerNamespace.resources"
}

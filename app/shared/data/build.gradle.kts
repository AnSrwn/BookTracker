// Data layer: backend API, auth and tokens, the local database, repository implementations.
// In the skeleton it holds only the environment configuration.
plugins {
    id("booktracker.kmp-library")
    id("booktracker.koin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
        }
    }
}

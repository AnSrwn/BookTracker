// A feature module: screens and ViewModels of one tab. Features never depend on each other;
// they navigate through the keys in shared/ui/navigation.
plugins {
    id("booktracker.kmp-compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            implementation(project(":shared:ui:navigation"))
            implementation(project(":shared:ui:common"))
            implementation(project(":shared:ui:designsystem"))
            implementation(catalogLibrary("navigation3-ui"))
            implementation(catalogLibrary("lifecycle-viewmodel"))
            implementation(catalogLibrary("koin-core"))
            implementation(catalogLibrary("koin-compose-viewmodel"))
        }
        commonTest.dependencies {
            implementation(catalogLibrary("kotlinx-coroutines-test"))
        }
    }
}

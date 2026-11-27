import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

// Assembles the app: dependency injection, the navigation graph and one back stack per tab.
// Builds the one Kotlin framework the iOS app links ("Shared"); every shared module reaches iOS through it.
plugins {
    id("booktracker.kmp-compose")
}

kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.core)
            implementation(projects.shared.data)
            implementation(projects.shared.ui.navigation)
            implementation(projects.shared.ui.common)
            implementation(projects.shared.ui.designsystem)
            implementation(projects.shared.ui.feature.bookshelf)
            implementation(projects.shared.ui.feature.addbook)
            implementation(projects.shared.ui.feature.account)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel.navigation3)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        // Architecture tests (Konsist, D42) run on the JVM with the other host tests
        getByName("androidHostTest").dependencies {
            implementation(libs.konsist)
        }
    }
}

// Konsist reads the sources of every module, also those Gradle does not compile for Android (e.g.
// iosMain). Declaring them as inputs makes Gradle rerun the architecture tests whenever any source
// changes, instead of treating the task as up to date (review F20).
tasks.withType<Test>().configureEach {
    inputs
        .files(
            rootProject.fileTree("shared") {
                include("**/src/**/*.kt")
                exclude("**/build/**")
            },
        ).withPropertyName("konsistSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

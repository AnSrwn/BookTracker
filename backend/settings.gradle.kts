@file:Suppress("UnstableApiUsage")

pluginManagement { includeBuild("build-logic") }

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "BookTracker"

include("core")
include("adapter:web")
include("adapter:persistence")
include("bootstrap")

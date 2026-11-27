@file:Suppress("UnstableApiUsage")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BookTrackerApp"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":android")
include(":shared:core")
include(":shared:data")
include(":shared:ui:navigation")
include(":shared:ui:common")
include(":shared:ui:designsystem")
include(":shared:ui:feature:bookshelf")
include(":shared:ui:feature:addbook")
include(":shared:ui:feature:account")
include(":shared:app")

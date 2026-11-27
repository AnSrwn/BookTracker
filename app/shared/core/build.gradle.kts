// Domain layer: models, use cases and repository interfaces. Pure Kotlin, like `core` in the backend.
// No Compose, no Ktor, no Room: shared/data implements the interfaces, the ViewModels call the use cases.
plugins {
    id("booktracker.kmp-library")
}

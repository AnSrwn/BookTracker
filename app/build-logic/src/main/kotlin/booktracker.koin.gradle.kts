// Dependency injection with Koin Annotations (D44). The Koin Compiler Plugin compiles the
// annotations and checks the whole graph at build time; never KSP. Root modules live in a `.di`
// package and are found automatically (D45, D46).
plugins {
    id("booktracker.kmp-library")
    id("io.insert-koin.compiler.plugin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project.dependencies.platform(catalogLibrary("koin-bom")))
            implementation(catalogLibrary("koin-core"))
            implementation(catalogLibrary("koin-annotations"))
        }
    }
}

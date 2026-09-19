import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("booktracker.kotlin-library")
    id("org.jetbrains.kotlin.plugin.spring")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    "implementation"(platform(SpringBootPlugin.BOM_COORDINATES))
    "implementation"(libs.findLibrary("kotlin-reflect").get())
}

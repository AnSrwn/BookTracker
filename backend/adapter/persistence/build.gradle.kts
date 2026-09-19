plugins {
    id("booktracker.spring-library")
}

dependencies {
    implementation(project(":core"))

    runtimeOnly(libs.postgresql)
}

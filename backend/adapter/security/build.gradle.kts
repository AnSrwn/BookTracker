plugins {
    id("booktracker.spring-library")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.spring.boot.starter.security.oauth2.resource.server)
}

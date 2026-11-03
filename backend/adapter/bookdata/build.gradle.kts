plugins {
    id("booktracker.spring-library")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.spring.context)
    implementation(libs.spring.web)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)
    implementation(libs.resilience4j.ratelimiter)
    implementation(libs.resilience4j.circuitbreaker)
    implementation(libs.resilience4j.micrometer)
}

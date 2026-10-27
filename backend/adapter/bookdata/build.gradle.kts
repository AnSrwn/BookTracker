plugins {
    id("booktracker.spring-library")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.spring.context)
    implementation(libs.spring.web)
    implementation(libs.jackson.module.kotlin)

    testImplementation(libs.spring.test)
}

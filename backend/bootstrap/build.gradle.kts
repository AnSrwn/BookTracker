plugins {
    id("booktracker.spring-boot-app")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":adapter:web"))
    implementation(project(":adapter:persistence"))

    implementation(libs.spring.boot.starter)

    testImplementation(libs.spring.boot.starter.test)
}

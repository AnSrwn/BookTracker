plugins {
    id("booktracker.spring-boot-app")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":adapter:web"))
    implementation(project(":adapter:persistence"))
    implementation(project(":adapter:security"))
    implementation(project(":adapter:bookdata"))

    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.actuator)

    developmentOnly(libs.spring.boot.docker.compose)
}

tasks.bootRun {
    // bootRun runs with bootstrap/ as working directory, but compose.yaml lives in the root
    systemProperty("spring.docker.compose.file", rootProject.file("compose.yaml").path)
}

tasks.bootBuildImage {
    imageName = "booktracker-backend:latest"
}

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

springBoot {
    // Served by /actuator/info. The deploy workflow waits until the new commit answers there,
    // so a container still running the old image cannot pass for a successful deploy.
    buildInfo {
        properties {
            additional = mapOf("commit" to providers.environmentVariable("GITHUB_SHA").getOrElse("local"))
        }
    }
}

tasks.bootBuildImage {
    // The deploy workflow overrides this with --imageName=ghcr.io/…
    imageName = "booktracker-backend:latest"
}

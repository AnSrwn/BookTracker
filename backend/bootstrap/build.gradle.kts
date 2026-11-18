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
    // Pinned instead of the implicit :latest, so the same commit always builds on the same base.
    // The builder also fixes the run image (base OS layer) and the Java runtime's version line.
    builder = "paketobuildpacks/builder-noble-java-tiny:0.0.217"
    // The deploy workflow overrides this with --imageName=ghcr.io/…
    imageName = "booktracker-backend:local"
}

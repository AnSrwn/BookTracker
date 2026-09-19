plugins {
    id("booktracker.spring-library")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.spring.boot.starter.data.jdbc)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)

    runtimeOnly(libs.postgresql)
}

import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("booktracker.spring-library")
    id("org.springframework.boot")
}

dependencies {
    for (configuration in listOf("developmentOnly", "testAndDevelopmentOnly")) {
        add(configuration, platform(SpringBootPlugin.BOM_COORDINATES))
    }
}

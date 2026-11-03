plugins { id("org.jetbrains.kotlin.jvm") }

val libs = the<VersionCatalogsExtension>().named("libs")

kotlin {
    jvmToolchain(jdkVersion = libs.findVersion("java").get().requiredVersion.toInt())
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xannotation-default-target=param-property",
            // Without it `List<@NotBlank String>` compiles, but Bean Validation never sees the element constraint
            "-Xemit-jvm-type-annotations",
        )
    }
}

dependencies {
    "implementation"(platform(libs.findLibrary("kotlin-bom").get()))
    "testImplementation"(libs.findLibrary("kotlin-test-junit5").get())
    "testRuntimeOnly"(libs.findLibrary("junit-platform-launcher").get())
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }
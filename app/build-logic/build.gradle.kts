plugins { `kotlin-dsl` }

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.serialization.gradle.plugin)
    implementation(libs.compose.compiler.gradle.plugin)
    implementation(libs.compose.gradle.plugin)
    implementation(libs.android.gradle.plugin)
    // implementation, not compileOnly: convention plugins apply it by id
    implementation(libs.koin.compiler.gradle.plugin)
}

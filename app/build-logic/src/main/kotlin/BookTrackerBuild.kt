import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.catalogVersion(name: String): String = libs.findVersion(name).get().requiredVersion

internal fun Project.catalogLibrary(name: String) = libs.findLibrary(name).get()

/**
 * Package and Android namespace of a shared module, derived from its Gradle path:
 * `:shared:ui:feature:bookshelf` becomes `com.sauerwein.booktracker.ui.feature.bookshelf`.
 */
internal val Project.booktrackerNamespace: String
    get() = "com.sauerwein.booktracker." + path.removePrefix(":shared:").replace(':', '.')

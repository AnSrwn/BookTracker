package com.sauerwein.booktracker.app

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test

/**
 * The module rules of ADR-002 / D39 that Gradle cannot see (D42), as an allow-list per layer:
 * core imports no other layer, data only core, ui only core and ui, and a feature no other feature.
 * Only shared/app may import everything. Plus the Koin layout of ADR-007: modules in `.di`
 * packages (D45) and fully annotated for discovery (D46), no DSL/KSP/service locator, and the
 * screen layout of ADR-003: one package per screen with contract, screen, ViewModel and destination,
 * found by Koin (D48, D49, D51, D52). Konsist reads the production sources of every module in
 * app/, so these tests cover all modules from here.
 */
class ArchitectureTest {
    private val files = Konsist.scopeFromProduction().files

    @Test
    fun coreImportsNoOtherLayer() {
        assertLayerImportsOnly(layer = CORE, allowed = listOf(CORE))
    }

    @Test
    fun theDataLayerImportsOnlyCore() {
        assertLayerImportsOnly(layer = DATA, allowed = listOf(CORE, DATA))
    }

    @Test
    fun theUiLayerImportsOnlyCoreAndUi() {
        assertLayerImportsOnly(layer = UI, allowed = listOf(CORE, UI))
    }

    @Test
    fun aFeatureNeverImportsAnotherFeature() {
        files
            .filter { it.packageName().startsWith("$FEATURE.") }
            .assertTrue { file ->
                val own = "$FEATURE." + file.packageName().removePrefix("$FEATURE.").substringBefore('.')
                file.imports.none { it.name.isIn(FEATURE) && !it.name.isIn(own) }
            }
    }

    @Test
    fun koinModulesAndTheKoinApplicationLiveInDiPackages() {
        Konsist
            .scopeFromProduction()
            .classes()
            .filter { it.hasAnnotation("Module") || it.hasAnnotation("KoinApplication") }
            .assertTrue {
                it.packagee
                    ?.name
                    .orEmpty()
                    .endsWith(".di")
            }
    }

    /**
     * Every module is a root module (D45, D46): without `@Configuration` or `@ComponentScan`,
     * `@KoinApplication` silently misses it at runtime. The scan covers exactly its own Gradle
     * module's package, so a copied module cannot scan another feature (review F23).
     */
    @Test
    fun everyModuleIsARootModuleThatScansItsOwnPackage() {
        Konsist
            .scopeFromProduction()
            .classes()
            .filter { it.hasAnnotation("Module") }
            .assertTrue { module ->
                val scanned = module.componentScanPackage()
                module.hasAnnotation("Configuration") &&
                    scanned != null &&
                    scanned == module.packagee?.name?.removeSuffix(".di")
            }
    }

    /** DSL modules, KSP, service locators and the classic `startKoin` bypass the compile-time graph check (review F24). */
    @Test
    fun noKoinDslKspOrServiceLocator() {
        files.assertTrue { file ->
            file.imports.none { import ->
                FORBIDDEN_KOIN_IMPORTS.any { import.name == it || import.name.startsWith("$it.") }
            }
        }
    }

    /** A destination without `@Singleton` is never collected and crashes on navigation (D48, D49). */
    @Test
    fun everyDestinationIsASingletonInAScreenPackage() {
        Konsist
            .scopeFromProduction()
            .classes()
            .filter { it.extends("ScreenDestination") }
            .assertTrue { it.hasAnnotation("Singleton") && it.isInScreenPackage() }
    }

    /** ViewModels are found by Koin through `@KoinViewModel` and sit next to their screen (D49). */
    @Test
    fun everyViewModelIsAKoinViewModelInAScreenPackage() {
        Konsist
            .scopeFromProduction()
            .classes()
            .filter { it.extends("ScreenViewModel") }
            .assertTrue { it.hasAnnotation("KoinViewModel") && it.isInScreenPackage() }
    }

    /**
     * Screens are stateless: they get state, events and callbacks, never a ViewModel or Koin.
     * Only the destination gets the ViewModel with `koinViewModel()` (D49).
     */
    @Test
    fun screensKnowNoViewModelAndOnlyDestinationsGetOne() {
        files
            .filter { it.name.endsWith("Screen") && it.packageName().startsWith("$FEATURE.") }
            .assertTrue { file ->
                file.imports.none { it.name.startsWith("org.koin") } &&
                    !Regex("""\b[A-Z]\w*ViewModel\b""").containsMatchIn(file.text)
            }
        files
            .filter { file -> file.imports.any { it.name == "org.koin.compose.viewmodel.koinViewModel" } }
            .assertTrue { it.name.endsWith("Destination") }
    }

    /** The destination orchestrates; a screen gets only `state` and `onEvent`, so it works in a preview (D49). */
    @Test
    fun screensTakeOnlyStateAndOnEvent() {
        files
            .filter { it.name.endsWith("Screen") && it.packageName().startsWith("$FEATURE.") }
            .flatMap { it.functions() }
            .filter { it.name.endsWith("Screen") }
            .assertTrue { screen -> screen.parameters.all { it.name in setOf("state", "onEvent", "modifier") } }
    }

    /** State, Event and Effect of a screen live in its contract, which holds nothing else (D52). */
    @Test
    fun contractTypesLiveOnlyInContractFiles() {
        val screenFiles = files.filter { it.isInScreenPackage() }
        screenFiles
            .filter { file -> file.topLevelTypeNames().any { it.isContractType() } }
            .assertTrue { it.name.endsWith("Contract") }
        screenFiles
            .filter { it.name.endsWith("Contract") }
            .assertTrue { file ->
                file.functions(includeNested = false).isEmpty() && file.topLevelTypeNames().all { it.isContractType() }
            }
    }

    /** A screen file holds only its composable, a ViewModel file only its ViewModel (D52). */
    @Test
    fun screenAndViewModelFilesHoldNoOtherTypes() {
        val screenFiles = files.filter { it.isInScreenPackage() }
        screenFiles
            .filter { it.name.endsWith("Screen") }
            .assertTrue { it.topLevelTypeNames().isEmpty() }
        screenFiles
            .filter { it.name.endsWith("ViewModel") }
            .assertTrue { file -> file.topLevelTypeNames() == listOf(file.name) }
    }

    /** Files in [layer] may import from the app's own packages only when they are in [allowed]. */
    private fun assertLayerImportsOnly(
        layer: String,
        allowed: List<String>,
    ) {
        files
            .filter { it.packageName().isIn(layer) }
            .assertTrue { file ->
                file.imports
                    .map { it.name }
                    .filter { it.isIn(ROOT) }
                    .all { import -> allowed.any { import.isIn(it) } }
            }
    }

    private fun KoFileDeclaration.packageName() = packagee?.name.orEmpty()

    private fun KoFileDeclaration.isInScreenPackage() = isScreenPackage(packageName())

    /** Names of the top-level classes, interfaces and objects of a file. */
    private fun KoFileDeclaration.topLevelTypeNames() =
        classes(includeNested = false).map { it.name } +
            interfaces(includeNested = false).map { it.name } +
            objects(includeNested = false).map { it.name }

    private fun String.isContractType() = endsWith("State") || endsWith("Event") || endsWith("Effect")

    private fun KoClassDeclaration.hasAnnotation(simpleName: String) = annotations.any { it.name == simpleName }

    private fun KoClassDeclaration.extends(simpleName: String) = parents().any { it.name.substringBefore('<') == simpleName }

    /** The package argument of `@ComponentScan("…")`, or null without one. */
    private fun KoClassDeclaration.componentScanPackage(): String? =
        annotations
            .firstOrNull { it.name == "ComponentScan" }
            ?.text
            ?.let { Regex("\"([^\"]+)\"").find(it)?.groupValues?.get(1) }

    /** `…ui.feature.<feature>.<screen>`, not the feature root, `di` or `resources`. */
    private fun KoClassDeclaration.isInScreenPackage() = isScreenPackage(packagee?.name.orEmpty())

    /** `…ui.feature.<feature>.<screen>`, not the feature root, `di` or `resources`. */
    private fun isScreenPackage(name: String): Boolean {
        val rest = name.removePrefix("$FEATURE.").split('.')
        return name.startsWith("$FEATURE.") && rest.size == 2 && rest[1] !in setOf("di", "resources")
    }

    /** True for the package or name [prefix] itself and everything below it, not for `…dataX`. */
    private fun String.isIn(prefix: String) = this == prefix || startsWith("$prefix.")

    private companion object {
        const val ROOT = "com.sauerwein.booktracker"
        const val CORE = "$ROOT.core"
        const val DATA = "$ROOT.data"
        const val UI = "$ROOT.ui"
        const val FEATURE = "$ROOT.ui.feature"
        val FORBIDDEN_KOIN_IMPORTS =
            listOf(
                "org.koin.dsl",
                "org.koin.ksp",
                "org.koin.core.context.startKoin",
                "org.koin.core.component.KoinComponent",
                "org.koin.core.component.inject",
            )
    }
}

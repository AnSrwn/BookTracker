package com.sauerwein.booktracker.app

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test

/**
 * The module rules of ADR-002 / D39 that Gradle cannot see (D42), as an allow-list per layer:
 * core imports no other layer, data only core, ui only core and ui, and a feature no other feature.
 * Only shared/app may import everything. Konsist reads the production sources of every module in
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

    /** True for the package or name [prefix] itself and everything below it, not for `…dataX`. */
    private fun String.isIn(prefix: String) = this == prefix || startsWith("$prefix.")

    private companion object {
        const val ROOT = "com.sauerwein.booktracker"
        const val CORE = "$ROOT.core"
        const val DATA = "$ROOT.data"
        const val UI = "$ROOT.ui"
        const val FEATURE = "$ROOT.ui.feature"
    }
}

package com.sauerwein.booktracker.ui.common

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.sauerwein.booktracker.ui.navigation.AppNavKey
import com.sauerwein.booktracker.ui.navigation.Navigator
import kotlin.reflect.KClass

/**
 * One screen in the navigation graph: which [key][keyClass] shows it, and how its callbacks and
 * effects turn into navigation. Each feature defines one destination per screen and lists them
 * in its `destinations`; shared/app registers them with [registerIn].
 *
 * Get the ViewModel inside [Content] with `koinViewModel()`; Navigation 3 scopes it to the entry.
 */
abstract class ScreenDestination<K : AppNavKey>(
    val keyClass: KClass<K>,
) {
    @Composable
    abstract fun Content(
        key: K,
        navigator: Navigator,
    )

    /** Adds this destination as an entry of a Navigation 3 `entryProvider`. */
    fun registerIn(
        scope: EntryProviderScope<NavKey>,
        navigator: Navigator,
    ) {
        scope.addEntryProvider(keyClass) { key -> Content(key, navigator) }
    }
}

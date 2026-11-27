package com.sauerwein.booktracker.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.sauerwein.booktracker.ui.feature.account.accountDestinations
import com.sauerwein.booktracker.ui.feature.addbook.addBookDestinations
import com.sauerwein.booktracker.ui.feature.bookshelf.bookshelfDestinations
import com.sauerwein.booktracker.ui.navigation.AppNavKey
import com.sauerwein.booktracker.ui.navigation.AppTab
import com.sauerwein.booktracker.ui.navigation.Navigator
import com.sauerwein.booktracker.ui.navigation.appNavSavedStateConfiguration

private val allDestinations = bookshelfDestinations + addBookDestinations + accountDestinations

/** One tab: its own back stack, its navigator and its decorated entries. */
class TabState internal constructor(
    val backStack: NavBackStack<NavKey>,
    val navigator: Navigator,
    internal val entries: List<NavEntry<NavKey>>,
)

/**
 * Creates the state of [tab]. Keep it in composition as long as the tab lives, also while another
 * tab is shown: the decorated entries hold the tab's ViewModels and saved state, and Navigation 3
 * clears them when they leave composition (review F1). Android keeps all three in `AndroidAppRoot`,
 * iOS one per tab view controller. Every destination is known in every tab.
 */
@Composable
fun rememberTabState(tab: AppTab): TabState {
    val backStack = rememberNavBackStack(appNavSavedStateConfiguration, tab.rootKey)
    val navigator = remember(backStack) { BackStackNavigator(backStack) }
    val entries =
        rememberDecoratedNavEntries(
            backStack = backStack,
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            entryProvider =
                entryProvider {
                    allDestinations.forEach { it.registerIn(this, navigator) }
                },
        )
    return TabState(backStack, navigator, entries)
}

/** Shows the top of a tab's back stack. ViewModels are scoped to their back stack entry. */
@Composable
fun TabContent(
    state: TabState,
    modifier: Modifier = Modifier,
) {
    NavDisplay(
        entries = state.entries,
        modifier = modifier,
        onBack = state.navigator::goBack,
    )
}

private class BackStackNavigator(
    private val backStack: NavBackStack<NavKey>,
) : Navigator {
    override fun navigate(key: AppNavKey) {
        backStack.add(key)
    }

    override fun goBack() {
        // The root of a tab stays; leaving a tab is the job of the tab bar
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
}

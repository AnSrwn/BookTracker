package com.sauerwein.booktracker.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sauerwein.booktracker.app.resources.Res
import com.sauerwein.booktracker.app.resources.tab_account
import com.sauerwein.booktracker.app.resources.tab_add_book
import com.sauerwein.booktracker.app.resources.tab_bookshelf
import com.sauerwein.booktracker.ui.designsystem.BookTrackerTheme
import com.sauerwein.booktracker.ui.designsystem.resources.ic_account
import com.sauerwein.booktracker.ui.designsystem.resources.ic_add_book
import com.sauerwein.booktracker.ui.designsystem.resources.ic_bookshelf
import com.sauerwein.booktracker.ui.navigation.AppTab
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.sauerwein.booktracker.ui.designsystem.resources.Res as DesignRes

/**
 * Android shell: a Material 3 navigation bar over one back stack per tab. iOS uses a native
 * `UITabBarController` instead (see ios/), with the same [TabContent] inside each tab.
 */
@Composable
fun AndroidAppRoot() {
    BookTrackerTheme {
        var selectedTab by rememberSaveable { mutableStateOf(AppTab.BOOKSHELF) }
        // All three tabs stay in composition, so switching keeps each tab's ViewModels and state (review F1)
        // key(tab) gives each tab its own composite key; without it the three calls from this loop
        // would share remembered state and ViewModel stores (review F15)
        val tabs = AppTab.entries.associateWith { tab -> key(tab) { rememberTabState(tab) } }
        val current = tabs.getValue(selectedTab)

        // Back on the root of another tab returns to the bookshelf, as Material navigation expects
        BackHandler(enabled = selectedTab != AppTab.BOOKSHELF && current.backStack.size == 1) {
            selectedTab = AppTab.BOOKSHELF
        }

        Scaffold(
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                NavigationBar {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == selectedTab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            },
        ) { padding ->
            TabContent(current, Modifier.padding(padding).consumeWindowInsets(padding))
        }
    }
}

private val AppTab.label
    get() =
        when (this) {
            AppTab.BOOKSHELF -> Res.string.tab_bookshelf
            AppTab.ADD_BOOK -> Res.string.tab_add_book
            AppTab.ACCOUNT -> Res.string.tab_account
        }

private val AppTab.icon
    get() =
        when (this) {
            AppTab.BOOKSHELF -> DesignRes.drawable.ic_bookshelf
            AppTab.ADD_BOOK -> DesignRes.drawable.ic_add_book
            AppTab.ACCOUNT -> DesignRes.drawable.ic_account
        }

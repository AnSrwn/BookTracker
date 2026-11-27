package com.sauerwein.booktracker.app

import androidx.compose.ui.window.ComposeUIViewController
import com.sauerwein.booktracker.ui.designsystem.BookTrackerTheme
import com.sauerwein.booktracker.ui.navigation.AppTab
import platform.UIKit.UIViewController

/**
 * Entry point for the Swift side (`IosApp.shared` in Swift). The native `UITabBarController`
 * holds one of these view controllers per tab; each has its own Compose root and back stack.
 */
object IosApp {
    /** Call once from the AppDelegate. [flavorId] comes from the build configuration, `dev` or `prod`. */
    fun start(flavorId: String) = initKoin(flavorId)

    fun bookshelfViewController(): UIViewController = tabViewController(AppTab.BOOKSHELF)

    fun addBookViewController(): UIViewController = tabViewController(AppTab.ADD_BOOK)

    fun accountViewController(): UIViewController = tabViewController(AppTab.ACCOUNT)

    private fun tabViewController(tab: AppTab): UIViewController =
        ComposeUIViewController {
            BookTrackerTheme {
                // Each tab is its own Compose root that stays alive, so its state lives as long as the tab
                TabContent(rememberTabState(tab))
            }
        }
}

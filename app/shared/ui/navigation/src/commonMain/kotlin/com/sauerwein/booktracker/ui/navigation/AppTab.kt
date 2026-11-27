package com.sauerwein.booktracker.ui.navigation

/**
 * The three tabs. Each tab has its own back stack, starting at [rootKey]. On iOS a native
 * `UITabBar` shows them, on Android a Material 3 navigation bar.
 */
enum class AppTab(
    val rootKey: AppNavKey,
) {
    BOOKSHELF(BookshelfKey),
    ADD_BOOK(AddBookKey),
    ACCOUNT(AccountKey),
}

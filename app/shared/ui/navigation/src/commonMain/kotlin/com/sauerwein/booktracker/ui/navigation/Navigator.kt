package com.sauerwein.booktracker.ui.navigation

/** Moves within the back stack of the current tab. Screens get it from their destination. */
interface Navigator {
    fun navigate(key: AppNavKey)

    fun goBack()
}

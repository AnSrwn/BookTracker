package com.sauerwein.booktracker.ui.feature.account.overview

/** What the user does on [AccountScreen]. No state and no effects: a placeholder (D52). */
sealed interface AccountEvent {
    data object AboutClicked : AccountEvent
}

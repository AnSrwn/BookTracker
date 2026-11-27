package com.sauerwein.booktracker.ui.feature.account.about

/** What the user does on [AboutScreen]. No state and no effects: a placeholder (D52). */
sealed interface AboutEvent {
    data object BackClicked : AboutEvent
}

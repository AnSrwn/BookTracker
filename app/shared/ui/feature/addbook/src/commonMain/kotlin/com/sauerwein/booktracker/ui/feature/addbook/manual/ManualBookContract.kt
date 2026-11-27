package com.sauerwein.booktracker.ui.feature.addbook.manual

/** What the user does on [ManualBookScreen]. No state and no effects: a placeholder (D52). */
sealed interface ManualBookEvent {
    data object BackClicked : ManualBookEvent
}

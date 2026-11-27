package com.sauerwein.booktracker.ui.feature.bookshelf.shelf

/** What the user does on [BookshelfScreen]. No state and no effects: a placeholder (D52). */
sealed interface BookshelfEvent {
    data object OpenSampleBookClicked : BookshelfEvent
}

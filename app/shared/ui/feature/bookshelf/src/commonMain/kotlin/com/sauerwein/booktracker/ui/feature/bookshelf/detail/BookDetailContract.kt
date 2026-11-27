package com.sauerwein.booktracker.ui.feature.bookshelf.detail

/** What the user does on [BookDetailScreen]. No state and no effects: a placeholder (D52). */
sealed interface BookDetailEvent {
    data object BackClicked : BookDetailEvent
}

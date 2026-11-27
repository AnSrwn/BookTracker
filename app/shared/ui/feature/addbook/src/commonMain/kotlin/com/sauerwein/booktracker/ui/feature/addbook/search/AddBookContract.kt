package com.sauerwein.booktracker.ui.feature.addbook.search

// The contract of the add-book screen (D52): AddBookScreen renders the state and sends events,
// AddBookViewModel holds the state and sends effects, AddBookDestination connects both.

/** What [AddBookScreen] shows. */
data class AddBookState(
    val query: String = "",
) {
    val canSearch: Boolean get() = query.isNotBlank()
}

/** What the user does on [AddBookScreen]. */
sealed interface AddBookEvent {
    data class QueryChanged(
        val query: String,
    ) : AddBookEvent

    data object SearchSubmitted : AddBookEvent

    data object AddManuallyClicked : AddBookEvent
}

/** One-off reactions of [AddBookViewModel] that [AddBookDestination] handles. */
sealed interface AddBookEffect {
    /**
     * A one-off message after the search check (D41). Placeholder: the add-book feature replaces it
     * with the catalog search, and adds e.g. "book added, open it" after the async save.
     */
    data class ShowSearchNotAvailable(
        val query: String,
    ) : AddBookEffect
}

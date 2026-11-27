package com.sauerwein.booktracker.ui.feature.addbook

import com.sauerwein.booktracker.ui.common.ScreenViewModel

data class AddBookState(
    val query: String = "",
) {
    val canSearch: Boolean get() = query.isNotBlank()
}

sealed interface AddBookEvent {
    data class QueryChanged(
        val query: String,
    ) : AddBookEvent

    data object SearchSubmitted : AddBookEvent
}

sealed interface AddBookEffect {
    /**
     * A one-off message after the search check (D41). Placeholder: the add-book feature replaces it
     * with the catalog search, and adds e.g. "book added, open it" after the async save.
     */
    data class ShowSearchNotAvailable(
        val query: String,
    ) : AddBookEffect
}

class AddBookViewModel : ScreenViewModel<AddBookState, AddBookEvent, AddBookEffect>(AddBookState()) {
    override fun onEvent(event: AddBookEvent) {
        when (event) {
            is AddBookEvent.QueryChanged -> {
                updateState { copy(query = event.query) }
            }

            AddBookEvent.SearchSubmitted -> {
                val state = state.value
                if (state.canSearch) sendEffect(AddBookEffect.ShowSearchNotAvailable(state.query.trim()))
            }
        }
    }
}

package com.sauerwein.booktracker.ui.feature.addbook.search

import com.sauerwein.booktracker.ui.common.ScreenViewModel
import org.koin.core.annotation.KoinViewModel

/** Holds the add-book state and checks a search before it sends an effect. Knows nothing about the screen. */
@KoinViewModel
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

            // Navigation without logic: AddBookDestination handles it and never forwards it here (D35)
            AddBookEvent.AddManuallyClicked -> {
                Unit
            }
        }
    }
}

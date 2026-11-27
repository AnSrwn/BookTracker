package com.sauerwein.booktracker.ui.feature.bookshelf.detail

import androidx.compose.runtime.Composable
import com.sauerwein.booktracker.ui.common.ScreenDestination
import com.sauerwein.booktracker.ui.navigation.BookDetailKey
import com.sauerwein.booktracker.ui.navigation.Navigator
import org.koin.core.annotation.Singleton

/** Shows [BookDetailScreen] for [BookDetailKey] and turns its events into navigation. Collected by Koin (D48). */
@Singleton
internal class BookDetailDestination : ScreenDestination<BookDetailKey>(BookDetailKey::class) {
    @Composable
    override fun Content(
        key: BookDetailKey,
        navigator: Navigator,
    ) {
        BookDetailScreen(
            onEvent = { event ->
                when (event) {
                    BookDetailEvent.BackClicked -> navigator.goBack()
                }
            },
        )
    }
}

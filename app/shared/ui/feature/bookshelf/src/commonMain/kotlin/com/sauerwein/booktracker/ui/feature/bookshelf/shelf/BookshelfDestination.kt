package com.sauerwein.booktracker.ui.feature.bookshelf.shelf

import androidx.compose.runtime.Composable
import com.sauerwein.booktracker.ui.common.ScreenDestination
import com.sauerwein.booktracker.ui.navigation.BookDetailKey
import com.sauerwein.booktracker.ui.navigation.BookshelfKey
import com.sauerwein.booktracker.ui.navigation.Navigator
import org.koin.core.annotation.Singleton

/** Shows [BookshelfScreen] for [BookshelfKey] and turns its events into navigation. Collected by Koin (D48). */
@Singleton
internal class BookshelfDestination : ScreenDestination<BookshelfKey>(BookshelfKey::class) {
    @Composable
    override fun Content(
        key: BookshelfKey,
        navigator: Navigator,
    ) {
        BookshelfScreen(
            onEvent = { event ->
                when (event) {
                    BookshelfEvent.OpenSampleBookClicked -> navigator.navigate(BookDetailKey(bookId = "sample"))
                }
            },
        )
    }
}

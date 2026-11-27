package com.sauerwein.booktracker.ui.feature.bookshelf

import androidx.compose.runtime.Composable
import com.sauerwein.booktracker.ui.common.ScreenDestination
import com.sauerwein.booktracker.ui.designsystem.PlaceholderScreen
import com.sauerwein.booktracker.ui.feature.bookshelf.resources.Res
import com.sauerwein.booktracker.ui.feature.bookshelf.resources.book_detail_body
import com.sauerwein.booktracker.ui.feature.bookshelf.resources.book_detail_title
import com.sauerwein.booktracker.ui.feature.bookshelf.resources.bookshelf_body
import com.sauerwein.booktracker.ui.feature.bookshelf.resources.bookshelf_open_sample
import com.sauerwein.booktracker.ui.feature.bookshelf.resources.bookshelf_title
import com.sauerwein.booktracker.ui.navigation.BookDetailKey
import com.sauerwein.booktracker.ui.navigation.BookshelfKey
import com.sauerwein.booktracker.ui.navigation.Navigator
import org.jetbrains.compose.resources.stringResource

/** All screens of the bookshelf tab, registered by shared/app. */
val bookshelfDestinations: List<ScreenDestination<*>> = listOf(BookshelfDestination, BookDetailDestination)

// Placeholders until the bookshelf feature. Opening a book is navigation without logic,
// so it is a plain callback, not an effect.
private object BookshelfDestination : ScreenDestination<BookshelfKey>(BookshelfKey::class) {
    @Composable
    override fun Content(
        key: BookshelfKey,
        navigator: Navigator,
    ) {
        PlaceholderScreen(
            title = stringResource(Res.string.bookshelf_title),
            body = stringResource(Res.string.bookshelf_body),
            actionLabel = stringResource(Res.string.bookshelf_open_sample),
            onAction = { navigator.navigate(BookDetailKey(bookId = "sample")) },
        )
    }
}

private object BookDetailDestination : ScreenDestination<BookDetailKey>(BookDetailKey::class) {
    @Composable
    override fun Content(
        key: BookDetailKey,
        navigator: Navigator,
    ) {
        PlaceholderScreen(
            title = stringResource(Res.string.book_detail_title),
            body = stringResource(Res.string.book_detail_body),
            onBack = navigator::goBack,
        )
    }
}

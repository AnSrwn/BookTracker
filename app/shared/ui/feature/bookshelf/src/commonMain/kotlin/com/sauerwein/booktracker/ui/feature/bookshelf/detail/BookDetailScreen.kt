package com.sauerwein.booktracker.ui.feature.bookshelf.detail

import androidx.compose.runtime.Composable
import com.sauerwein.booktracker.ui.designsystem.PlaceholderScreen
import com.sauerwein.booktracker.ui.feature.bookshelf.resources.Res
import com.sauerwein.booktracker.ui.feature.bookshelf.resources.book_detail_body
import com.sauerwein.booktracker.ui.feature.bookshelf.resources.book_detail_title
import org.jetbrains.compose.resources.stringResource

/**
 * Placeholder until the feature is built. Gets only events out, so it works in a preview; no
 * state or logic, so no ViewModel (D50). [BookDetailDestination] handles the events.
 */
@Composable
internal fun BookDetailScreen(onEvent: (BookDetailEvent) -> Unit) {
    PlaceholderScreen(
        title = stringResource(Res.string.book_detail_title),
        body = stringResource(Res.string.book_detail_body),
        onBack = { onEvent(BookDetailEvent.BackClicked) },
    )
}

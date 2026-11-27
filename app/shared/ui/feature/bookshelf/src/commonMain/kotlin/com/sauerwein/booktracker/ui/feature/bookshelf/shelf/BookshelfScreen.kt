package com.sauerwein.booktracker.ui.feature.bookshelf.shelf

import androidx.compose.runtime.Composable
import com.sauerwein.booktracker.ui.designsystem.PlaceholderScreen
import com.sauerwein.booktracker.ui.feature.bookshelf.resources.Res
import com.sauerwein.booktracker.ui.feature.bookshelf.resources.bookshelf_body
import com.sauerwein.booktracker.ui.feature.bookshelf.resources.bookshelf_open_sample
import com.sauerwein.booktracker.ui.feature.bookshelf.resources.bookshelf_title
import org.jetbrains.compose.resources.stringResource

/**
 * Placeholder until the feature is built. Gets only events out, so it works in a preview; no
 * state or logic, so no ViewModel (D50). [BookshelfDestination] handles the events.
 */
@Composable
internal fun BookshelfScreen(onEvent: (BookshelfEvent) -> Unit) {
    PlaceholderScreen(
        title = stringResource(Res.string.bookshelf_title),
        body = stringResource(Res.string.bookshelf_body),
        actionLabel = stringResource(Res.string.bookshelf_open_sample),
        onAction = { onEvent(BookshelfEvent.OpenSampleBookClicked) },
    )
}

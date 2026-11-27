package com.sauerwein.booktracker.ui.feature.addbook.manual

import androidx.compose.runtime.Composable
import com.sauerwein.booktracker.ui.designsystem.PlaceholderScreen
import com.sauerwein.booktracker.ui.feature.addbook.resources.Res
import com.sauerwein.booktracker.ui.feature.addbook.resources.manual_book_body
import com.sauerwein.booktracker.ui.feature.addbook.resources.manual_book_title
import org.jetbrains.compose.resources.stringResource

/**
 * Placeholder until the feature is built. Gets only events out, so it works in a preview; no
 * state or logic, so no ViewModel (D50). [ManualBookDestination] handles the events.
 */
@Composable
internal fun ManualBookScreen(onEvent: (ManualBookEvent) -> Unit) {
    PlaceholderScreen(
        title = stringResource(Res.string.manual_book_title),
        body = stringResource(Res.string.manual_book_body),
        onBack = { onEvent(ManualBookEvent.BackClicked) },
    )
}

package com.sauerwein.booktracker.ui.feature.account.about

import androidx.compose.runtime.Composable
import com.sauerwein.booktracker.ui.designsystem.PlaceholderScreen
import com.sauerwein.booktracker.ui.feature.account.resources.Res
import com.sauerwein.booktracker.ui.feature.account.resources.about_body
import com.sauerwein.booktracker.ui.feature.account.resources.about_title
import org.jetbrains.compose.resources.stringResource

/**
 * Placeholder until the feature is built. Gets only events out, so it works in a preview; no
 * state or logic, so no ViewModel (D50). [AboutDestination] handles the events.
 */
@Composable
internal fun AboutScreen(onEvent: (AboutEvent) -> Unit) {
    PlaceholderScreen(
        title = stringResource(Res.string.about_title),
        body = stringResource(Res.string.about_body),
        onBack = { onEvent(AboutEvent.BackClicked) },
    )
}

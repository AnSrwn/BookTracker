package com.sauerwein.booktracker.ui.feature.account.overview

import androidx.compose.runtime.Composable
import com.sauerwein.booktracker.ui.designsystem.PlaceholderScreen
import com.sauerwein.booktracker.ui.feature.account.resources.Res
import com.sauerwein.booktracker.ui.feature.account.resources.account_about
import com.sauerwein.booktracker.ui.feature.account.resources.account_body
import com.sauerwein.booktracker.ui.feature.account.resources.account_title
import org.jetbrains.compose.resources.stringResource

/**
 * Placeholder until the feature is built. Gets only events out, so it works in a preview; no
 * state or logic, so no ViewModel (D50). [AccountDestination] handles the events.
 */
@Composable
internal fun AccountScreen(onEvent: (AccountEvent) -> Unit) {
    PlaceholderScreen(
        title = stringResource(Res.string.account_title),
        body = stringResource(Res.string.account_body),
        actionLabel = stringResource(Res.string.account_about),
        onAction = { onEvent(AccountEvent.AboutClicked) },
    )
}

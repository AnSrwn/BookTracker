package com.sauerwein.booktracker.ui.feature.account

import androidx.compose.runtime.Composable
import com.sauerwein.booktracker.ui.common.ScreenDestination
import com.sauerwein.booktracker.ui.designsystem.PlaceholderScreen
import com.sauerwein.booktracker.ui.feature.account.resources.Res
import com.sauerwein.booktracker.ui.feature.account.resources.about_body
import com.sauerwein.booktracker.ui.feature.account.resources.about_title
import com.sauerwein.booktracker.ui.feature.account.resources.account_about
import com.sauerwein.booktracker.ui.feature.account.resources.account_body
import com.sauerwein.booktracker.ui.feature.account.resources.account_title
import com.sauerwein.booktracker.ui.navigation.AboutKey
import com.sauerwein.booktracker.ui.navigation.AccountKey
import com.sauerwein.booktracker.ui.navigation.Navigator
import org.jetbrains.compose.resources.stringResource

/** All screens of the account tab, registered by shared/app. */
val accountDestinations: List<ScreenDestination<*>> = listOf(AccountDestination, AboutDestination)

// Placeholders until the auth step adds sign-in, sign-out and the account info.
private object AccountDestination : ScreenDestination<AccountKey>(AccountKey::class) {
    @Composable
    override fun Content(
        key: AccountKey,
        navigator: Navigator,
    ) {
        PlaceholderScreen(
            title = stringResource(Res.string.account_title),
            body = stringResource(Res.string.account_body),
            actionLabel = stringResource(Res.string.account_about),
            onAction = { navigator.navigate(AboutKey) },
        )
    }
}

private object AboutDestination : ScreenDestination<AboutKey>(AboutKey::class) {
    @Composable
    override fun Content(
        key: AboutKey,
        navigator: Navigator,
    ) {
        PlaceholderScreen(
            title = stringResource(Res.string.about_title),
            body = stringResource(Res.string.about_body),
            onBack = navigator::goBack,
        )
    }
}

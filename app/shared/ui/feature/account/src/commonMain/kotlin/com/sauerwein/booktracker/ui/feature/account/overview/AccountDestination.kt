package com.sauerwein.booktracker.ui.feature.account.overview

import androidx.compose.runtime.Composable
import com.sauerwein.booktracker.ui.common.ScreenDestination
import com.sauerwein.booktracker.ui.navigation.AboutKey
import com.sauerwein.booktracker.ui.navigation.AccountKey
import com.sauerwein.booktracker.ui.navigation.Navigator
import org.koin.core.annotation.Singleton

/** Shows [AccountScreen] for [AccountKey] and turns its events into navigation. Collected by Koin (D48). */
@Singleton
internal class AccountDestination : ScreenDestination<AccountKey>(AccountKey::class) {
    @Composable
    override fun Content(
        key: AccountKey,
        navigator: Navigator,
    ) {
        AccountScreen(
            onEvent = { event ->
                when (event) {
                    AccountEvent.AboutClicked -> navigator.navigate(AboutKey)
                }
            },
        )
    }
}

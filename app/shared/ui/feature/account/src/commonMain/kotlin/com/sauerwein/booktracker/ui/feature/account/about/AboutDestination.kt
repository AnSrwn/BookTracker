package com.sauerwein.booktracker.ui.feature.account.about

import androidx.compose.runtime.Composable
import com.sauerwein.booktracker.ui.common.ScreenDestination
import com.sauerwein.booktracker.ui.navigation.AboutKey
import com.sauerwein.booktracker.ui.navigation.Navigator
import org.koin.core.annotation.Singleton

/** Shows [AboutScreen] for [AboutKey] and turns its events into navigation. Collected by Koin (D48). */
@Singleton
internal class AboutDestination : ScreenDestination<AboutKey>(AboutKey::class) {
    @Composable
    override fun Content(
        key: AboutKey,
        navigator: Navigator,
    ) {
        AboutScreen(
            onEvent = { event ->
                when (event) {
                    AboutEvent.BackClicked -> navigator.goBack()
                }
            },
        )
    }
}

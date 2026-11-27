package com.sauerwein.booktracker.ui.feature.addbook.manual

import androidx.compose.runtime.Composable
import com.sauerwein.booktracker.ui.common.ScreenDestination
import com.sauerwein.booktracker.ui.navigation.ManualBookKey
import com.sauerwein.booktracker.ui.navigation.Navigator
import org.koin.core.annotation.Singleton

/** Shows [ManualBookScreen] for [ManualBookKey] and turns its events into navigation. Collected by Koin (D48). */
@Singleton
internal class ManualBookDestination : ScreenDestination<ManualBookKey>(ManualBookKey::class) {
    @Composable
    override fun Content(
        key: ManualBookKey,
        navigator: Navigator,
    ) {
        ManualBookScreen(
            onEvent = { event ->
                when (event) {
                    ManualBookEvent.BackClicked -> navigator.goBack()
                }
            },
        )
    }
}

package com.sauerwein.booktracker.ui.feature.addbook

import androidx.compose.runtime.Composable
import com.sauerwein.booktracker.ui.common.ScreenDestination
import com.sauerwein.booktracker.ui.designsystem.PlaceholderScreen
import com.sauerwein.booktracker.ui.feature.addbook.resources.Res
import com.sauerwein.booktracker.ui.feature.addbook.resources.manual_book_body
import com.sauerwein.booktracker.ui.feature.addbook.resources.manual_book_title
import com.sauerwein.booktracker.ui.navigation.AddBookKey
import com.sauerwein.booktracker.ui.navigation.ManualBookKey
import com.sauerwein.booktracker.ui.navigation.Navigator
import org.jetbrains.compose.resources.stringResource

/** All screens of the add-book tab, registered by shared/app. */
val addBookDestinations: List<ScreenDestination<*>> = listOf(AddBookDestination, ManualBookDestination)

private object AddBookDestination : ScreenDestination<AddBookKey>(AddBookKey::class) {
    @Composable
    override fun Content(
        key: AddBookKey,
        navigator: Navigator,
    ) {
        AddBookScreen(onAddManually = { navigator.navigate(ManualBookKey) })
    }
}

// Placeholder until the add-book feature builds the form
private object ManualBookDestination : ScreenDestination<ManualBookKey>(ManualBookKey::class) {
    @Composable
    override fun Content(
        key: ManualBookKey,
        navigator: Navigator,
    ) {
        PlaceholderScreen(
            title = stringResource(Res.string.manual_book_title),
            body = stringResource(Res.string.manual_book_body),
            onBack = navigator::goBack,
        )
    }
}

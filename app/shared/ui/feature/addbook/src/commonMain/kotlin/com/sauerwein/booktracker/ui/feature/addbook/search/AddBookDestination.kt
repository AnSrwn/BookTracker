package com.sauerwein.booktracker.ui.feature.addbook.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sauerwein.booktracker.ui.common.ScreenDestination
import com.sauerwein.booktracker.ui.common.ScreenHost
import com.sauerwein.booktracker.ui.feature.addbook.resources.Res
import com.sauerwein.booktracker.ui.feature.addbook.resources.add_book_search_pending
import com.sauerwein.booktracker.ui.navigation.AddBookKey
import com.sauerwein.booktracker.ui.navigation.ManualBookKey
import com.sauerwein.booktracker.ui.navigation.Navigator
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.Singleton

/**
 * Orchestrates [AddBookScreen] for [AddBookKey]: gets [AddBookViewModel] from Koin, routes the
 * screen's events (navigation without logic directly, the rest to the ViewModel, D35), and turns
 * effects into a snackbar. Koin collects it as a [ScreenDestination] (D48).
 */
@Singleton
internal class AddBookDestination : ScreenDestination<AddBookKey>(AddBookKey::class) {
    @Composable
    override fun Content(
        key: AddBookKey,
        navigator: Navigator,
    ) {
        val viewModel = koinViewModel<AddBookViewModel>()
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        ScreenHost(
            viewModel = viewModel,
            onEffect = { effect ->
                when (effect) {
                    is AddBookEffect.ShowSearchNotAvailable -> {
                        scope.launch { snackbarHostState.showSnackbar(getString(Res.string.add_book_search_pending, effect.query)) }
                    }
                }
            },
        ) { state, onEvent ->
            Box(Modifier.fillMaxSize()) {
                AddBookScreen(
                    state = state,
                    onEvent = { event ->
                        when (event) {
                            AddBookEvent.AddManuallyClicked -> navigator.navigate(ManualBookKey)
                            else -> onEvent(event)
                        }
                    },
                )
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing),
                )
            }
        }
    }
}

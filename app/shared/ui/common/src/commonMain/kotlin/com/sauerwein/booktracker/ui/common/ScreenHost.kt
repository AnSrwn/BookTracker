package com.sauerwein.booktracker.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Connects a [ScreenViewModel] to its stateless content. Every screen looks like this:
 *
 * ```
 * @Composable
 * fun BookshelfScreen(onOpenBook: (String) -> Unit, viewModel: BookshelfViewModel = koinViewModel()) =
 *     ScreenHost(viewModel, onEffect = { effect -> ... }) { state, onEvent -> BookshelfContent(state, onEvent) }
 * ```
 *
 * The content gets only `state` and `onEvent`, so it works in previews without a ViewModel.
 */
@Composable
fun <State : Any, Event : Any, Effect : Any> ScreenHost(
    viewModel: ScreenViewModel<State, Event, Effect>,
    onEffect: (Effect) -> Unit,
    content: @Composable (state: State, onEvent: (Event) -> Unit) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects, onEffect)
    content(state, viewModel::onEvent)
}

/**
 * Collects one-off effects only while the screen is at least STARTED, on `Main.immediate`, so an
 * effect is neither handled while the screen is invisible nor lost between dispatch and handling.
 */
@Composable
fun <Effect : Any> CollectEffects(
    effects: Flow<Effect>,
    onEffect: (Effect) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnEffect by rememberUpdatedState(onEffect)
    LaunchedEffect(effects, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            withContext(Dispatchers.Main.immediate) {
                effects.collect { currentOnEffect(it) }
            }
        }
    }
}

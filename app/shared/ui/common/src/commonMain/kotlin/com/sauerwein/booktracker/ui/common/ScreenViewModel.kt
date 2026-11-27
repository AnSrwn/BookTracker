package com.sauerwein.booktracker.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel of one screen. Data flows one way:
 * - the screen sends [Event]s to [onEvent],
 * - the ViewModel publishes one immutable [State],
 * - one-off reactions after logic or async work (navigate, show a message) go out as [Effect]s.
 *
 * Keep this class small. Loading, error handling or retries belong in use cases or helpers, not here.
 */
abstract class ScreenViewModel<State : Any, Event : Any, Effect : Any>(
    initialState: State,
) : ViewModel() {
    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<State> = mutableState.asStateFlow()

    // Unlimited, so sendEffect never fails or suspends. An effect waits here until the screen
    // collects again, e.g. while the app is in the background or an iOS tab is not visible.
    private val effectChannel = Channel<Effect>(Channel.UNLIMITED)

    /** Collected once by [ScreenHost]. Every effect is delivered to exactly one collector. */
    val effects: Flow<Effect> = effectChannel.receiveAsFlow()

    abstract fun onEvent(event: Event)

    protected fun updateState(transform: State.() -> State) {
        mutableState.update(transform)
    }

    protected fun sendEffect(effect: Effect) {
        effectChannel.trySend(effect)
    }

    /** Local-first: keeps the state in step with a flow from the database, as long as the ViewModel lives. */
    protected fun <T> Flow<T>.collectIntoState(reduce: State.(T) -> State): Job =
        viewModelScope.launch {
            collect { value -> updateState { reduce(value) } }
        }
}

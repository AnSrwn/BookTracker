package com.sauerwein.booktracker.ui.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenViewModelTest {
    private data class CounterState(
        val count: Int = 0,
        val source: Int = 0,
    )

    private sealed interface CounterEvent {
        data object Increment : CounterEvent

        data object Finish : CounterEvent
    }

    private sealed interface CounterEffect {
        data class Finished(
            val count: Int,
        ) : CounterEffect
    }

    private class CounterViewModel(
        source: MutableStateFlow<Int>,
    ) : ScreenViewModel<CounterState, CounterEvent, CounterEffect>(CounterState()) {
        init {
            source.collectIntoState { copy(source = it) }
        }

        override fun onEvent(event: CounterEvent) {
            when (event) {
                CounterEvent.Increment -> updateState { copy(count = count + 1) }
                CounterEvent.Finish -> sendEffect(CounterEffect.Finished(state.value.count))
            }
        }
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun eventsUpdateTheState() {
        val viewModel = CounterViewModel(MutableStateFlow(0))

        viewModel.onEvent(CounterEvent.Increment)
        viewModel.onEvent(CounterEvent.Increment)

        assertEquals(2, viewModel.state.value.count)
    }

    @Test
    fun effectsWaitUntilSomeoneCollects() =
        runTest {
            val viewModel = CounterViewModel(MutableStateFlow(0))
            viewModel.onEvent(CounterEvent.Increment)

            viewModel.onEvent(CounterEvent.Finish)

            assertEquals(CounterEffect.Finished(count = 1), viewModel.effects.first())
        }

    @Test
    fun collectIntoStateFollowsTheSourceFlow() {
        val source = MutableStateFlow(1)
        val viewModel = CounterViewModel(source)

        source.value = 5

        assertEquals(5, viewModel.state.value.source)
    }
}

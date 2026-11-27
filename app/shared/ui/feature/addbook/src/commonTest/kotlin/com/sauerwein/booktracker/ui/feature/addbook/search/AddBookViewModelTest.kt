package com.sauerwein.booktracker.ui.feature.addbook.search

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AddBookViewModelTest {
    private val viewModel = AddBookViewModel()

    @Test
    fun typingUpdatesTheQuery() {
        viewModel.onEvent(AddBookEvent.QueryChanged("Momo"))

        assertEquals("Momo", viewModel.state.value.query)
        assertTrue(viewModel.state.value.canSearch)
    }

    @Test
    fun submittingSendsTheTrimmedQueryAsAMessage() =
        runTest {
            val effects = collectEffects()
            viewModel.onEvent(AddBookEvent.QueryChanged("  Momo "))

            viewModel.onEvent(AddBookEvent.SearchSubmitted)

            assertEquals(listOf<AddBookEffect>(AddBookEffect.ShowSearchNotAvailable("Momo")), effects)
        }

    @Test
    fun aBlankQuerySendsNothing() =
        runTest {
            val effects = collectEffects()
            viewModel.onEvent(AddBookEvent.QueryChanged("   "))

            viewModel.onEvent(AddBookEvent.SearchSubmitted)

            assertFalse(viewModel.state.value.canSearch)
            assertEquals(emptyList(), effects)
        }

    private fun TestScope.collectEffects(): List<AddBookEffect> {
        val effects = mutableListOf<AddBookEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }
}

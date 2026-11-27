package com.sauerwein.booktracker.ui.feature.addbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.sauerwein.booktracker.ui.common.ScreenHost
import com.sauerwein.booktracker.ui.feature.addbook.resources.Res
import com.sauerwein.booktracker.ui.feature.addbook.resources.add_book_manual
import com.sauerwein.booktracker.ui.feature.addbook.resources.add_book_scan
import com.sauerwein.booktracker.ui.feature.addbook.resources.add_book_search
import com.sauerwein.booktracker.ui.feature.addbook.resources.add_book_search_label
import com.sauerwein.booktracker.ui.feature.addbook.resources.add_book_search_pending
import com.sauerwein.booktracker.ui.feature.addbook.resources.add_book_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Root of the add-book tab (D4): search field on top, then "scan" and "add manually". */
@Composable
internal fun AddBookScreen(
    onAddManually: () -> Unit,
    viewModel: AddBookViewModel = koinViewModel(),
) {
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
        AddBookContent(state = state, onEvent = onEvent, onAddManually = onAddManually, snackbarHostState = snackbarHostState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddBookContent(
    state: AddBookState,
    onEvent: (AddBookEvent) -> Unit,
    onAddManually: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(Res.string.add_book_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { onEvent(AddBookEvent.QueryChanged(it)) },
                label = { Text(stringResource(Res.string.add_book_search_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onEvent(AddBookEvent.SearchSubmitted) }),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onEvent(AddBookEvent.SearchSubmitted) },
                enabled = state.canSearch,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(Res.string.add_book_search)) }
            // Scanning comes with the scan step of the roadmap
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.add_book_scan))
            }
            OutlinedButton(onClick = onAddManually, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.add_book_manual))
            }
        }
    }
}

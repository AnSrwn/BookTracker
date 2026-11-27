package com.sauerwein.booktracker.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sauerwein.booktracker.ui.designsystem.resources.Res
import com.sauerwein.booktracker.ui.designsystem.resources.ic_back
import org.jetbrains.compose.resources.painterResource

/** A screen that is not built yet. Replaced feature by feature. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderScreen(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(Res.drawable.ic_back), contentDescription = null)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(body, style = MaterialTheme.typography.bodyLarge)
            if (actionLabel != null) {
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

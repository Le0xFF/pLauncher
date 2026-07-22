package com.le0xff.plauncher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.le0xff.plauncher.model.LaunchApp

@Composable
fun AppScreen(
    apps: List<LaunchApp>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onAddApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filtered = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps else apps.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            singleLine = true
        )

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(if (apps.isEmpty()) "No apps configured. Tap + to add apps." else "No matching apps.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filtered, key = { it.packageName }) { app ->
                    ListItem(
                        headlineContent = { Text(app.displayName) },
                        supportingContent = { Text(app.packageName, style = MaterialTheme.typography.bodySmall) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onAddApp,
            modifier = Modifier
                .align(Alignment.End)
                .padding(16.dp)
        ) {
            Text("+")
        }
    }
}

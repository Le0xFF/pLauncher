package com.le0xff.plauncher.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.le0xff.plauncher.R
import com.le0xff.plauncher.model.LaunchApp

@Composable
fun AppScreen(
    apps: List<LaunchApp>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onAddApp: () -> Unit,
    onRemoveApp: (LaunchApp) -> Unit,
    modifier: Modifier = Modifier
) {
    val filtered = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps else apps.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text(stringResource(R.string.placeholder_search)) },
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
                Text(if (apps.isEmpty()) stringResource(R.string.appscreen_empty) else stringResource(R.string.appscreen_no_match), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filtered, key = { it.packageName }) { app ->
                    if (filtered.indexOf(app) > 0) {
                        Divider(modifier = Modifier.padding(start = 16.dp))
                    }

                    val context = LocalContext.current
                    val iconBitmap = remember(app.packageName) {
                        try {
                            val pm = context.packageManager
                            val info = pm.getApplicationInfo(app.packageName, 0)
                            val drawable = info.loadIcon(pm)
                            drawable?.toBitmap(72, 72)?.asImageBitmap()
                        } catch (_: PackageManager.NameNotFoundException) {
                            null
                        } catch (_: Exception) {
                            null
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        iconBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp).padding(end = 8.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.displayName)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(
                            onClick = { onRemoveApp(app) },
                            modifier = Modifier
                                .size(56.dp)
                                .padding(start = 8.dp)
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.appscreen_remove),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAddApp,
            modifier = Modifier
                .align(Alignment.End)
                .padding(16.dp)
        ) {
            Text(stringResource(R.string.button_add_app))
        }
    }
}

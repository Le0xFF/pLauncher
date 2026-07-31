package com.le0xff.plauncher.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.le0xff.plauncher.R
import com.le0xff.plauncher.model.LaunchApp
import com.le0xff.plauncher.model.SortOrder
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun AppScreen(
    apps: List<LaunchApp>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onAddApp: () -> Unit,
    onRemoveApp: (LaunchApp) -> Unit,
    onRenameApp: (LaunchApp) -> Unit,
    onReorderApp: (fromIndex: Int, toIndex: Int) -> Unit,
    onSortApps: (SortOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    val filtered = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps else apps.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
    }

    val listState = rememberLazyListState()

    val isDragEnabled = searchQuery.isBlank()

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState
    ) { from, to ->
        onReorderApp(from.index, to.index)
    }

    var showSortMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(stringResource(R.string.placeholder_search)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors().copy(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent
                )
            )

            Box {
                IconButton(
                    onClick = { showSortMenu = !showSortMenu },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Filled.SortByAlpha,
                        contentDescription = stringResource(R.string.appscreen_sort_menu),
                        modifier = Modifier.size(24.dp)
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.appscreen_sort_ascending)) },
                        onClick = {
                            onSortApps(SortOrder.Ascending)
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.appscreen_sort_descending)) },
                        onClick = {
                            onSortApps(SortOrder.Descending)
                            showSortMenu = false
                        }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(if (apps.isEmpty()) stringResource(R.string.appscreen_empty) else stringResource(R.string.appscreen_no_match), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState
            ) {
                items(filtered, key = { app -> app.packageName }) { app ->
                    if (filtered.indexOf(app) > 0) {
                        Divider(modifier = Modifier.padding(start = 16.dp))
                    }

                    ReorderableItem(
                        state = reorderableState,
                        key = app.packageName,
                        enabled = isDragEnabled
                    ) { isDragging ->
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .then(
                                    if (isDragging) {
                                        Modifier.background(
                                            MaterialTheme.colorScheme.surfaceContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                } else {
                                    Modifier
                                }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isDragEnabled) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                        .draggableHandle()
                            ) {
                                Icon(
                                    Icons.Filled.DragIndicator,
                                    contentDescription = stringResource(R.string.appscreen_drag_handle),
                                    modifier = Modifier
                                        .size(28.dp)
                                        .align(Alignment.Center),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

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
                        Row(modifier = Modifier.padding(start = 8.dp)) {
                            IconButton(
                                onClick = { onRenameApp(app) },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = stringResource(R.string.appscreen_rename),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            IconButton(
                                onClick = { onRemoveApp(app) },
                                modifier = Modifier.size(56.dp)
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

package com.le0xff.plauncher.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.le0xff.plauncher.R
import com.le0xff.plauncher.model.LaunchApp
import com.le0xff.plauncher.model.SortOrder
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableCollectionItemScope
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
    appCount: Int,
    maxApps: Int,
    autoLaunchEnabled: Boolean,
    autoLaunchTarget: Int,
    onAutoLaunchTargetChange: (Int) -> Unit,
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

    // Hoist theme colors, context, and icon cache out of LazyColumn to avoid per-item recomposition
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val context = LocalContext.current
    val appIndexMap = remember(apps) {
        apps.mapIndexed { idx, app -> app.packageName to idx }.toMap()
    }
    val iconCache = remember(context) { mutableMapOf<String, ImageBitmap?>() }

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
                Text(if (apps.isEmpty()) stringResource(R.string.appscreen_empty) else stringResource(R.string.appscreen_no_match), style = typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState
            ) {
                itemsIndexed(filtered, key = { _, app -> app.packageName }) { index, app ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    }

                    ReorderableItem(
                        state = reorderableState,
                        key = app.packageName,
                        enabled = isDragEnabled
                    ) { isDragging ->
                    val iconBitmap = remember(app.packageName) {
                            iconCache.getOrPut(app.packageName) {
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
                        }

                        AppListItem(
                            app = app,
                            iconBitmap = iconBitmap,
                            isDragging = isDragging,
                            isDragEnabled = isDragEnabled,
                            scope = this,
                            appIndexMap = appIndexMap,
                            autoLaunchEnabled = autoLaunchEnabled,
                            autoLaunchTarget = autoLaunchTarget,
                            onAutoLaunchTargetChange = onAutoLaunchTargetChange,
                            onRenameApp = onRenameApp,
                            onRemoveApp = onRemoveApp,
                            colorScheme = colorScheme,
                            typography = typography
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAddApp,
            modifier = Modifier
                .align(Alignment.End)
                .padding(16.dp)
                .widthIn(min = 100.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.button_add_app))
                Text(" | ")
                Text("$appCount/$maxApps")
            }
        }
    }
}

@Composable
fun AppListItem(
    app: LaunchApp,
    iconBitmap: ImageBitmap?,
    isDragging: Boolean,
    isDragEnabled: Boolean,
    scope: ReorderableCollectionItemScope,
    appIndexMap: Map<String, Int>,
    autoLaunchEnabled: Boolean,
    autoLaunchTarget: Int,
    onAutoLaunchTargetChange: (Int) -> Unit,
    onRenameApp: (LaunchApp) -> Unit,
    onRemoveApp: (LaunchApp) -> Unit,
    colorScheme: ColorScheme,
    typography: Typography
) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .then(
                                    if (isDragging) {
                                        Modifier.background(
                                            colorScheme.surfaceContainer,
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
                                        .then(with(scope) { Modifier.draggableHandle() })
                            ) {
                                Icon(
                                    Icons.Filled.DragIndicator,
                                    contentDescription = stringResource(R.string.appscreen_drag_handle),
                                    modifier = Modifier
                                        .size(28.dp)
                                        .align(Alignment.Center),
                                    tint = colorScheme.onSurfaceVariant
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
                            Text(app.packageName, style = typography.bodySmall)
                        }
                        Row(modifier = Modifier.padding(start = 8.dp)) {
                            val appIndex = appIndexMap[app.packageName] ?: -1
                            val isAutoLaunchTarget = appIndex == autoLaunchTarget
                            val ringColor = if (isAutoLaunchTarget) {
                                colorScheme.primary
                            } else {
                                colorScheme.onSurfaceVariant
                            }
                            val circleAlpha = if (autoLaunchEnabled) 1f else 0.3f
                            IconButton(
                                onClick = { onAutoLaunchTargetChange(appIndex) },
                                enabled = autoLaunchEnabled,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                    .size(28.dp)
                                    .border(2.dp, ringColor.copy(alpha = circleAlpha), CircleShape),
                                    contentAlignment = Alignment.Center
                                    ) {
                                    if (isAutoLaunchTarget) {
                                Box(
                                    modifier = Modifier
                                    .size(12.dp)
                                    .background(ringColor.copy(alpha = circleAlpha), CircleShape)
                                        )
                                    }
                                }
                            }
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
                                    tint = colorScheme.error,
                                    modifier = Modifier.size(28.dp)
                                )
            }
        }
    }
}

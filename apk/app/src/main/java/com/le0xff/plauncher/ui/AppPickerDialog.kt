package com.le0xff.plauncher.ui

/**
 * pLauncher Companion App — Full-screen dialog for selecting installed apps to add to the launcher, with search and system app filtering.
 *
 * @author Le0xFF
 */

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.le0xff.plauncher.R
import com.le0xff.plauncher.model.LaunchApp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

private const val PickerIconSize = 48
private const val PickerColumnHeight = 400

/**
 * Dialog for selecting installed apps to add to the launcher. Shows search, system app filtering, and selection limit.
 */
@Composable
fun AppPickerDialog(
    selectedApps: List<LaunchApp>,
    showSystemApps: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<LaunchApp>) -> Unit,
    maxApps: Int,
    @Suppress("UnusedParameter") modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val installedApps = remember(context) { getInstalledLaunchableApps(context) }

    val filtered = remember(installedApps, showSystemApps) {
        if (showSystemApps) installedApps else installedApps.filter { !it.isSystem }
    }

    var searchQuery by remember { mutableStateOf("") }
    val filteredBySearch = remember(filtered, searchQuery) {
        if (searchQuery.isBlank()) filtered else filtered.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val selectedPackageNames = remember(selectedApps) { selectedApps.map { it.packageName }.toSet() }
    var localSelected by remember { mutableStateOf(selectedPackageNames) }

    LaunchedEffect(selectedPackageNames) {
        localSelected = selectedPackageNames
    }

    val isAtLimit = localSelected.size >= maxApps

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.picker_title))
                Text(
                    "${localSelected.size}/$maxApps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isAtLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(modifier = Modifier.height(PickerColumnHeight.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.placeholder_search)) },
                    modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    singleLine = true
                )
                LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                    ) {
                    items(filteredBySearch, key = { it.packageName }) { app ->
                        val isSelected = localSelected.contains(app.packageName)
                        val checkboxDisabled = isAtLimit && !isSelected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                enabled = !checkboxDisabled,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        localSelected = localSelected + app.packageName
                                    } else {
                                        localSelected = localSelected - app.packageName
                                    }
                                }
                            )
                            val iconBitmap = remember(app.packageName) {
                                val bmp = app.icon?.toBitmap(PickerIconSize, PickerIconSize)
                                bmp?.asImageBitmap()
                            }
                            iconBitmap?.let { bitmap ->
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp).padding(end = 8.dp)
                                )
                            }
                            Text(
                                app.name,
                                modifier = Modifier.weight(1f),
                                color = if (checkboxDisabled && !isSelected)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val result = localSelected.map { pkg ->
                    val info = installedApps.find { it.packageName == pkg }
                    if (info != null) LaunchApp(info.packageName, info.name) else LaunchApp(pkg, pkg)
                }
                onConfirm(result)
            }) {
                Text(stringResource(R.string.button_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
}

data class InstalledAppInfo(
    val packageName: String,
    val name: String,
    val icon: Drawable?,
    val isSystem: Boolean
)

fun getInstalledLaunchableApps(context: android.content.Context): List<InstalledAppInfo> {
    val pm = context.packageManager
    val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
    return packages.mapNotNull { packageInfo ->
        val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
        val packageName = appInfo.packageName
        val label = appInfo.loadLabel(pm).toString()
        val icon = appInfo.loadIcon(pm)
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        InstalledAppInfo(packageName, label.ifBlank { packageName }, icon, isSystem)
    }.sortedBy { it.name.lowercase() }
}

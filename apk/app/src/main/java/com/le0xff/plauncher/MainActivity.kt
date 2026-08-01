package com.le0xff.plauncher

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.le0xff.plauncher.data.AppDataStore
import com.le0xff.plauncher.data.ImportResult
import com.le0xff.plauncher.data.YamlExportImport
import com.le0xff.plauncher.model.LaunchApp
import com.le0xff.plauncher.model.SortOrder
import com.le0xff.plauncher.ui.AppTheme
import com.le0xff.plauncher.ui.AppPickerDialog
import com.le0xff.plauncher.ui.AppScreen
import com.le0xff.plauncher.ui.SettingsScreen
import com.le0xff.plauncher.R
import com.le0xff.plauncher.ui.checkCanDrawOverlays
import com.le0xff.plauncher.ui.checkIgnoringBatteryOptimizations
import com.le0xff.plauncher.ui.pLauncherTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel : ViewModel() {
    private val _apps = MutableStateFlow<List<LaunchApp>>(emptyList())
    val apps: StateFlow<List<LaunchApp>> = _apps.asStateFlow()

    private val _showSystemApps = MutableStateFlow(false)
    val showSystemApps: StateFlow<Boolean> = _showSystemApps.asStateFlow()

    private val _generateCrashReports = MutableStateFlow(false)
    val generateCrashReports: StateFlow<Boolean> = _generateCrashReports.asStateFlow()

    private val _showPicker = MutableStateFlow(false)
    val showPicker: StateFlow<Boolean> = _showPicker.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _removeAppTarget = MutableStateFlow<LaunchApp?>(null)
    val removeAppTarget: StateFlow<LaunchApp?> = _removeAppTarget.asStateFlow()

    private val _renameAppTarget = MutableStateFlow<LaunchApp?>(null)
    val renameAppTarget: StateFlow<LaunchApp?> = _renameAppTarget.asStateFlow()

    private val _connectionStatus = MutableStateFlow("")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _appTheme = MutableStateFlow(AppTheme.Light)
    val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()

    private val _resumeCounter = MutableStateFlow(0)
    val resumeCounter: StateFlow<Int> = _resumeCounter.asStateFlow()

    private val _vibrationPref = MutableStateFlow(0)
    val vibrationPref: StateFlow<Int> = _vibrationPref.asStateFlow()

    private val _autoClose = MutableStateFlow(false)
    val autoClose: StateFlow<Boolean> = _autoClose.asStateFlow()

    private val _autoLaunchEnabled = MutableStateFlow(false)
    val autoLaunchEnabled: StateFlow<Boolean> = _autoLaunchEnabled.asStateFlow()

    private val _autoLaunchTarget = MutableStateFlow(0)
    val autoLaunchTarget: StateFlow<Int> = _autoLaunchTarget.asStateFlow()

    fun setApps(newApps: List<LaunchApp>) {
        _apps.value = newApps
    }

    fun setShowSystemApps(value: Boolean) {
        _showSystemApps.value = value
    }

    fun setGenerateCrashReports(value: Boolean) {
        _generateCrashReports.value = value
    }

    fun setShowPicker(value: Boolean) {
        _showPicker.value = value
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setRemoveAppTarget(app: LaunchApp?) {
        _removeAppTarget.value = app
    }

    fun setRenameAppTarget(app: LaunchApp?) {
        _renameAppTarget.value = app
    }

    fun setConnectionStatus(status: String) {
        _connectionStatus.value = status
    }

    fun setAppTheme(value: AppTheme) {
        _appTheme.value = value
    }

    fun reorderApp(fromIndex: Int, toIndex: Int): List<LaunchApp> {
        val current = _apps.value
        if (fromIndex == toIndex || fromIndex !in current.indices || toIndex !in current.indices) {
            return current
        }
        val reordered = current.toMutableList()
        val item = reordered.removeAt(fromIndex)
        reordered.add(toIndex, item)
        return reordered
    }

    fun sortApps(order: SortOrder): List<LaunchApp> {
        return _apps.value.sortedWith(
            compareBy<LaunchApp> { it.displayName.lowercase() }.thenBy { it.packageName }
        ).let { sorted ->
            if (order == SortOrder.Descending) sorted.reversed() else sorted
        }
    }

    fun setVibrationPref(value: Int) {
        _vibrationPref.value = value
    }

    fun setAutoClose(value: Boolean) {
        _autoClose.value = value
    }

    fun setAutoLaunchEnabled(value: Boolean) {
        _autoLaunchEnabled.value = value
    }

    fun setAutoLaunchTarget(value: Int) {
        _autoLaunchTarget.value = value
    }

    fun onActivityResume() {
        _resumeCounter.value++
    }
}

class MainActivity : ComponentActivity() {
    companion object {
        const val MAX_APPS = 20
    }
    private val coroutineScope: CoroutineScope = MainScope()
    private val viewModel = AppViewModel()
    private lateinit var appDataStore: AppDataStore
    private lateinit var senderHelper: PebbleSenderHelper

    private fun validateAutoLaunchTarget(apps: List<LaunchApp>) {
        val target = viewModel.autoLaunchTarget.value
        if (target >= apps.size && apps.isNotEmpty()) {
            viewModel.setAutoLaunchTarget(0)
            appDataStore.setAutoLaunchTarget(0)
            coroutineScope.launch {
                senderHelper.sendAutoLaunchTarget(0u)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appDataStore = AppDataStore(this)
        senderHelper = PebbleSenderHelper(this)

        setContent {
            val appTheme by viewModel.appTheme.collectAsState()
            pLauncherTheme(theme = appTheme) {
                val context = LocalContext.current
                val dataStore = remember { appDataStore }

                val apps by viewModel.apps.collectAsState()
                val showSystemApps by viewModel.showSystemApps.collectAsState()
                val generateCrashReports by viewModel.generateCrashReports.collectAsState()
                val showPicker by viewModel.showPicker.collectAsState()
                val searchQuery by viewModel.searchQuery.collectAsState()
                val removeAppTarget by viewModel.removeAppTarget.collectAsState()
                val renameAppTarget by viewModel.renameAppTarget.collectAsState()
                val connectionStatus by viewModel.connectionStatus.collectAsState()
                val resumeCounter by viewModel.resumeCounter.collectAsState()
                val vibrationPref by viewModel.vibrationPref.collectAsState()
                val autoClose by viewModel.autoClose.collectAsState()
                val autoLaunchEnabled by viewModel.autoLaunchEnabled.collectAsState()
                val autoLaunchTarget by viewModel.autoLaunchTarget.collectAsState()

                val initialStatus = stringResource(R.string.status_disconnected)
                LaunchedEffect(Unit) {
                    viewModel.setApps(dataStore.apps.value)
                    viewModel.setShowSystemApps(dataStore.getShowSystemApps())
                    viewModel.setGenerateCrashReports(dataStore.getGenerateCrashReports())
                    viewModel.setAppTheme(dataStore.getAppTheme())
                    viewModel.setVibrationPref(dataStore.getVibrationPref())
                    viewModel.setAutoClose(dataStore.getAutoClose())
                    viewModel.setAutoLaunchEnabled(dataStore.getAutoLaunchEnabled())
                    viewModel.setAutoLaunchTarget(dataStore.getAutoLaunchTarget())
                    viewModel.setConnectionStatus(initialStatus)
                }

                var selectedTab by remember { mutableStateOf(0) }

                val canDrawOverlays = remember(resumeCounter) {
                    mutableStateOf(checkCanDrawOverlays(this))
                }
                val ignoringBatteryOpt = remember(resumeCounter) {
                    mutableStateOf(checkIgnoringBatteryOptimizations(this))
                }

                var importPendingResult: ImportResult? by remember { mutableStateOf(null) }
                var importWarningsResult: ImportResult? by remember { mutableStateOf(null) }

                val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    if (result.resultCode != android.app.Activity.RESULT_OK) return@rememberLauncherForActivityResult
                    val uri = result.data?.data ?: return@rememberLauncherForActivityResult
                    val yamlContent = try {
                        contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    } catch (e: Exception) {
                        Toast.makeText(this, R.string.import_failed, Toast.LENGTH_SHORT).show()
                        return@rememberLauncherForActivityResult
                    }
                    if (yamlContent.isBlank()) {
                        Toast.makeText(this, R.string.import_empty_file, Toast.LENGTH_SHORT).show()
                        return@rememberLauncherForActivityResult
                    }
                    val parsedResult = try {
                        YamlExportImport.importAppsFromYaml(yamlContent, packageManager, packageName)
                    } catch (e: Exception) {
                        Toast.makeText(this, R.string.import_failed, Toast.LENGTH_SHORT).show()
                        return@rememberLauncherForActivityResult
                    }
                    if (parsedResult.apps.isEmpty() && parsedResult.skippedPackages.isEmpty() && !parsedResult.multipleAutoLaunch && !parsedResult.maxAppsExceeded && parsedResult.duplicatePackages.isEmpty() && parsedResult.duplicatePositionPackages.isEmpty() && parsedResult.outOfRangePackages.isEmpty()) {
                        Toast.makeText(this, R.string.import_empty_file, Toast.LENGTH_SHORT).show()
                        return@rememberLauncherForActivityResult
                    }
                    importPendingResult = parsedResult
                }

                fun applyImportResult(result: ImportResult) {
                    if (result.apps.isNotEmpty()) {
                        viewModel.setApps(result.apps)
                        viewModel.setAutoLaunchTarget(result.autoLaunchTarget)
                        dataStore.setAutoLaunchTarget(result.autoLaunchTarget)
                        dataStore.saveApps(result.apps)
                        validateAutoLaunchTarget(result.apps)
                        coroutineScope.launch {
                            senderHelper.sendAppList(result.apps, null)
                            senderHelper.sendAutoLaunchTarget(result.autoLaunchTarget.toUInt())
                        }
                        sendBroadcast(Intent(PebbleListenerService.ACTION_SEND_APP_LIST))
                    }
                    val hasWarnings = result.skippedPackages.isNotEmpty() ||
                        result.duplicatePackages.isNotEmpty() ||
                        result.duplicatePositionPackages.isNotEmpty() ||
                        result.outOfRangePackages.isNotEmpty() ||
                        result.multipleAutoLaunch ||
                        result.maxAppsExceeded
                    if (hasWarnings) {
                        importWarningsResult = result
                    }
                }

                fun buildOriginalNames(appsList: List<LaunchApp>): Map<String, String> {
                    val map = mutableMapOf<String, String>()
                    for (app in appsList) {
                        try {
                            val info = packageManager.getApplicationInfo(app.packageName, 0)
                            map[app.packageName] = info.loadLabel(packageManager).toString().ifBlank { app.packageName }
                        } catch (_: PackageManager.NameNotFoundException) {
                            map[app.packageName] = app.displayName
                        }
                    }
                    return map
                }

                val onExportClick: () -> Unit = {
                    try {
                        val originalNames = buildOriginalNames(apps)
                        val yamlContent = dataStore.exportAppsToYaml(originalNames, autoLaunchTarget)
                        val exportsDir = File(context.cacheDir, "exports")
                        if (!exportsDir.exists()) exportsDir.mkdirs()
                        val file = File(exportsDir, "plauncher_apps.yaml")
                        file.writeText(yamlContent)
                        val uri = FileProvider.getUriForFile(
                            context, "com.le0xff.plauncher.fileprovider", file
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/yaml"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "pLauncher app list")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(shareIntent, getString(R.string.button_export)))
                    } catch (e: Exception) {
                        Toast.makeText(context, R.string.import_failed, Toast.LENGTH_SHORT).show()
                    }
                }

                val onImportClick: () -> Unit = {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    importLauncher.launch(intent)
                }

                var showPermissionDialog by remember { mutableStateOf(!canDrawOverlays.value || !ignoringBatteryOpt.value) }
                var dismissedOnce by remember { mutableStateOf(false) }

                LaunchedEffect(resumeCounter) {
                    if (!dismissedOnce) {
                        showPermissionDialog = !canDrawOverlays.value || !ignoringBatteryOpt.value
                    }
                }

                if (showPermissionDialog) {
                    val needsOverlay = !canDrawOverlays.value
                    val needsBattery = !ignoringBatteryOpt.value

                    AlertDialog(
                        onDismissRequest = {
                            showPermissionDialog = false
                        },
                        title = { Text(stringResource(R.string.perm_dialog_title)) },
                        text = {
                            Column {
                                Text(stringResource(R.string.perm_dialog_text))
                                Spacer(modifier = Modifier.height(8.dp))
                                if (needsOverlay) Text(stringResource(R.string.perm_draw_overlays))
                                if (needsBattery) Text(stringResource(R.string.perm_ignore_battery))
                                if (!needsOverlay && !needsBattery) {
                                    Text(stringResource(R.string.perm_dialog_settings_hint))
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Column {
                                    if (needsOverlay) {
                                        Button(
                                            onClick = {
                                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                                    data = android.net.Uri.parse("package:${packageName}")
                                                }
                                                startActivity(intent)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 18.dp)
                                        ) {
                                            Text(stringResource(R.string.perm_button_grant_overlay))
                                        }
                                    }
                                    if (needsBattery) {
                                        Button(
                                            onClick = {
                                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                    data = android.net.Uri.parse("package:${packageName}")
                                                }
                                                startActivity(intent)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 18.dp)
                                        ) {
                                            Text(stringResource(R.string.perm_button_grant_battery))
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showPermissionDialog = false
                                dismissedOnce = true
                            }) {
                                Text(stringResource(R.string.button_dismiss))
                            }
                        }
                    )
                }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.tab_apps)) },
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                label = { Text(stringResource(R.string.tab_apps)) }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.tab_settings)) },
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                label = { Text(stringResource(R.string.tab_settings)) }
                            )
                        }
                    }
                ) { padding ->
                    if (selectedTab == 0) {
                        AppScreen(
                            apps = apps,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { viewModel.setSearchQuery(it) },
                            onAddApp = {
                                if (apps.size >= MAX_APPS) {
                                    Toast.makeText(context, R.string.appscreen_limit_reached, Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.setShowPicker(true)
                                }
                            },
                            onRemoveApp = { viewModel.setRemoveAppTarget(it) },
                            onRenameApp = { viewModel.setRenameAppTarget(it) },
                            onReorderApp = { fromIndex, toIndex ->
                                val reordered = viewModel.reorderApp(fromIndex, toIndex)
                                if (reordered !== apps) {
                                    viewModel.setApps(reordered)
                                    dataStore.saveApps(reordered)
                                    validateAutoLaunchTarget(reordered)
                                    coroutineScope.launch {
                                        senderHelper.sendAppList(reordered, null)
                                    }
                                    sendBroadcast(Intent(PebbleListenerService.ACTION_SEND_APP_LIST))
                                }
                            },
                            onSortApps = { order ->
                                val sorted = viewModel.sortApps(order)
                                if (sorted !== apps) {
                                    viewModel.setApps(sorted)
                                    dataStore.saveApps(sorted)
                                    validateAutoLaunchTarget(sorted)
                                    coroutineScope.launch {
                                        senderHelper.sendAppList(sorted, null)
                                    }
                                    sendBroadcast(Intent(PebbleListenerService.ACTION_SEND_APP_LIST))
                                }
                            },
                            appCount = apps.size,
                            maxApps = MAX_APPS,
                            autoLaunchEnabled = autoLaunchEnabled,
                            autoLaunchTarget = autoLaunchTarget,
                            onAutoLaunchTargetChange = {
                                viewModel.setAutoLaunchTarget(it)
                                dataStore.setAutoLaunchTarget(it)
                                coroutineScope.launch {
                                    senderHelper.sendAutoLaunchTarget(it.toUInt())
                                }
                            },
                            modifier = Modifier.padding(padding)
                        )
                    } else {
                        SettingsScreen(
                            showSystemApps = showSystemApps,
                            onShowSystemAppsChange = {
                                viewModel.setShowSystemApps(it)
                                dataStore.setShowSystemApps(it)
                            },
                            generateCrashReports = generateCrashReports,
                            onGenerateCrashReportsChange = {
                                viewModel.setGenerateCrashReports(it)
                                dataStore.setGenerateCrashReports(it)
                            },
                            canDrawOverlays = canDrawOverlays.value,
                            ignoringBatteryOpt = ignoringBatteryOpt.value,
                            currentTheme = appTheme,
                            onThemeChange = {
                                viewModel.setAppTheme(it)
                                dataStore.setAppTheme(it)
                            },
                            vibrationPref = vibrationPref,
                            onVibrationPrefChange = {
                                viewModel.setVibrationPref(it)
                                dataStore.setVibrationPref(it)
                                coroutineScope.launch {
                                    senderHelper.sendVibrationPref(it.toUInt())
                                }
                            },
                            autoClose = autoClose,
                            onAutoCloseChange = {
                                viewModel.setAutoClose(it)
                                dataStore.setAutoClose(it)
                                coroutineScope.launch {
                                    senderHelper.sendAutoClosePref(if (it) 1u else 0u)
                                }
                            },
                            autoLaunch = autoLaunchEnabled,
                            onAutoLaunchChange = {
                                viewModel.setAutoLaunchEnabled(it)
                                dataStore.setAutoLaunchEnabled(it)
                                coroutineScope.launch {
                                    senderHelper.sendAutoLaunchPref(if (it) 1u else 0u)
                                }
                            },
                            onExportClick = onExportClick,
                            onImportClick = onImportClick,
                            modifier = Modifier.padding(padding)
                        )
                    }

                    removeAppTarget?.let { targetApp ->
                    AlertDialog(
                        onDismissRequest = {
                            viewModel.setRemoveAppTarget(null)
                        },
                        title = { Text(stringResource(R.string.confirm_remove_title)) },
                        text = {
                            Text(stringResource(R.string.confirm_remove_text, targetApp.displayName))
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val updatedApps = apps.filter { it.packageName != targetApp.packageName }
                                dataStore.saveApps(updatedApps)
                                viewModel.setApps(updatedApps)
                                validateAutoLaunchTarget(updatedApps)
                                viewModel.setRemoveAppTarget(null)
                                coroutineScope.launch {
                                    senderHelper.sendAppList(updatedApps, null)
                                }
                                sendBroadcast(Intent(PebbleListenerService.ACTION_SEND_APP_LIST))
                            }) {
                                Text(stringResource(R.string.confirm_remove_confirm))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                viewModel.setRemoveAppTarget(null)
                            }) {
                                Text(stringResource(R.string.button_cancel))
                            }
                        }
                    )
                }

                renameAppTarget?.let { targetApp ->
                    val renameContext = LocalContext.current
                    val originalName = remember(targetApp) {
                        val pm = renameContext.packageManager
                        try {
                            val info = pm.getApplicationInfo(targetApp.packageName, 0)
                            info.loadLabel(pm).toString().ifBlank { targetApp.packageName }
                        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                            targetApp.packageName
                        }
                    }
                    var editName by remember(targetApp) { mutableStateOf(targetApp.displayName.takeIf { it != originalName } ?: "") }

                    AlertDialog(
                        onDismissRequest = {
                            viewModel.setRenameAppTarget(null)
                        },
                        title = { Text(stringResource(R.string.rename_dialog_title)) },
                        text = {
                            Column {
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { if (it.length <= 32) editName = it },
                                placeholder = { Text(originalName, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                                Text(
                                    stringResource(R.string.rename_char_count, editName.length),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (editName.length >= 32) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val finalName = if (editName.isBlank()) originalName else editName
                                val updatedApps = apps.map { a ->
                                    if (a.packageName == targetApp.packageName) LaunchApp(a.packageName, finalName) else a
                                }
                                dataStore.saveApps(updatedApps)
                                viewModel.setApps(updatedApps)
                                validateAutoLaunchTarget(updatedApps)
                                viewModel.setRenameAppTarget(null)
                                coroutineScope.launch {
                                    senderHelper.sendAppList(updatedApps, null)
                                }
                                sendBroadcast(Intent(PebbleListenerService.ACTION_SEND_APP_LIST))
                            }) {
                                Text(stringResource(R.string.rename_button_save))
                            }
                        },
                        dismissButton = {
                            Row {
                                TextButton(onClick = {
                                    viewModel.setRenameAppTarget(null)
                                }) {
                                    Text(stringResource(R.string.button_cancel))
                                }
                                TextButton(onClick = {
                                    val updatedApps = apps.map { a ->
                                        if (a.packageName == targetApp.packageName) LaunchApp(a.packageName, originalName) else a
                                    }
                                    dataStore.saveApps(updatedApps)
                                    viewModel.setApps(updatedApps)
                                    validateAutoLaunchTarget(updatedApps)
                                    viewModel.setRenameAppTarget(null)
                                    coroutineScope.launch {
                                        senderHelper.sendAppList(updatedApps, null)
                                    }
                                    sendBroadcast(Intent(PebbleListenerService.ACTION_SEND_APP_LIST))
                                }) {
                                    Text(stringResource(R.string.rename_button_reset))
                                }
                            }
                        }
                    )
                }

                if (showPicker) {
                        AppPickerDialog(
                            selectedApps = apps,
                            showSystemApps = showSystemApps,
                            onDismiss = { viewModel.setShowPicker(false) },
                            maxApps = MAX_APPS,
                            onConfirm = { selectedApps ->
                                dataStore.saveApps(selectedApps)
                                viewModel.setApps(selectedApps)
                                validateAutoLaunchTarget(selectedApps)
                                viewModel.setShowPicker(false)
                                coroutineScope.launch {
                                    senderHelper.sendAppList(selectedApps, null)
                                }
                                sendBroadcast(Intent(PebbleListenerService.ACTION_SEND_APP_LIST))
                            }
                        )
                    }

                    importPendingResult?.let { result ->
                        AlertDialog(
                            onDismissRequest = {
                                importPendingResult = null
                            },
                            title = { Text(stringResource(R.string.import_confirm_title)) },
                            text = {
                                Text(stringResource(R.string.import_confirm_text, result.apps.size))
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    importPendingResult = null
                                    applyImportResult(result)
                                }) {
                                    Text(stringResource(R.string.import_button_replace))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    importPendingResult = null
                                }) {
                                    Text(stringResource(R.string.button_cancel))
                                }
                            }
                        )
                    }
    
                importWarningsResult?.let { result ->
                    AlertDialog(
                        onDismissRequest = {
                            importWarningsResult = null
                        },
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "⚠️")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.import_warnings_title))
                            }
                        },
                        text = {
                            Column(
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                HorizontalDivider()

                                if (result.skippedPackages.isNotEmpty()) {
                                    HorizontalDivider()
                                    Text(
                                        text = getString(R.string.import_skipped_apps),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                    result.skippedPackages.forEach { pkg ->
                                        Text(
                                            text = "  $pkg",
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }

                                if (result.duplicatePackages.isNotEmpty()) {
                                    HorizontalDivider()
                                    Text(
                                        text = getString(R.string.import_duplicate_packages),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                    result.duplicatePackages.forEach { pkg ->
                                        Text(
                                            text = "  $pkg",
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }

                                if (result.duplicatePositionPackages.isNotEmpty()) {
                                    HorizontalDivider()
                                    Text(
                                        text = getString(R.string.import_duplicate_positions),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                    result.duplicatePositionPackages.forEach { pkg ->
                                        Text(
                                            text = "  $pkg",
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }

                                if (result.outOfRangePackages.isNotEmpty()) {
                                    HorizontalDivider()
                                    Text(
                                        text = getString(R.string.import_position_out_of_range),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                    result.outOfRangePackages.forEach { pkg ->
                                        Text(
                                            text = "  $pkg",
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }

                                if (result.multipleAutoLaunch) {
                                    HorizontalDivider()
                                    Text(
                                        text = getString(R.string.import_multiple_auto_launch),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                    result.multipleAutoLaunchPackages.forEach { pkg ->
                                        Text(
                                            text = "  $pkg",
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }

                                if (result.maxAppsExceeded) {
                                    HorizontalDivider()
                                    Text(
                                        text = getString(R.string.import_max_apps_exceeded),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                importWarningsResult = null
                            }) {
                                Text(stringResource(R.string.button_done))
                            }
                        }
                    )
                }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onActivityResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            senderHelper.close()
        } catch (e: Exception) {
            // Ignore if sender was already closed
        }
    }
}

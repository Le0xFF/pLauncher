package com.le0xff.plauncher

/**
 * pLauncher Companion App — Main Activity and ViewModel. Hosts the Compose UI,
 * manages app list CRUD, settings, Pebble sync, and YAML import/export.
 *
 * @author Le0xFF
 */

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import java.io.IOException
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.le0xff.plauncher.data.AppDataStore
import com.le0xff.plauncher.data.AppLogBuffer
import com.le0xff.plauncher.data.ImportResult
import com.le0xff.plauncher.data.YamlExportImport
import com.le0xff.plauncher.media.MediaControlListenerService
import com.le0xff.plauncher.model.LaunchApp
import com.le0xff.plauncher.model.SortOrder
import com.le0xff.plauncher.ui.AppTheme
import com.le0xff.plauncher.ui.AppPickerDialog
import com.le0xff.plauncher.ui.AppScreen
import com.le0xff.plauncher.ui.SettingsScreen
import com.le0xff.plauncher.R
import com.le0xff.plauncher.ui.checkCanDrawOverlays
import com.le0xff.plauncher.ui.checkIgnoringBatteryOptimizations
import com.le0xff.plauncher.ui.checkNotificationListenerAccess
import com.le0xff.plauncher.ui.PLauncherTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppTab { Apps, Settings }

/**
 * ViewModel holding UI state as StateFlow properties. Bridges the data store and the Compose UI.
 */
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

    private val _playOnLaunch = MutableStateFlow(false)
    val playOnLaunch: StateFlow<Boolean> = _playOnLaunch.asStateFlow()

    private val _playOnLaunchTimeoutS = MutableStateFlow(AppDataStore.DEFAULT_PLAY_ON_LAUNCH_TIMEOUT_S)
    val playOnLaunchTimeoutS: StateFlow<Int> = _playOnLaunchTimeoutS.asStateFlow()

    private val _playOnLaunchFirstPhaseS = MutableStateFlow(AppDataStore.DEFAULT_PLAY_ON_LAUNCH_FIRST_PHASE_S)
    val playOnLaunchFirstPhaseS: StateFlow<Int> = _playOnLaunchFirstPhaseS.asStateFlow()

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

    fun setPlayOnLaunch(value: Boolean) {
        _playOnLaunch.value = value
    }

    fun setPlayOnLaunchTimeoutS(value: Int) {
        _playOnLaunchTimeoutS.value = value
    }

    fun onPlayOnLaunchTimeoutInvalid() {
        _playOnLaunchTimeoutS.value = AppDataStore.DEFAULT_PLAY_ON_LAUNCH_TIMEOUT_S
    }

    fun setPlayOnLaunchFirstPhaseS(value: Int) {
        _playOnLaunchFirstPhaseS.value = value
    }

    fun onPlayOnLaunchFirstPhaseInvalid() {
        _playOnLaunchFirstPhaseS.value = AppDataStore.DEFAULT_PLAY_ON_LAUNCH_FIRST_PHASE_S
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

/**
 * Main activity. Initializes data store and Pebble sender, sets up Compose UI with app list and settings tabs.
 */
class MainActivity : ComponentActivity() {
    companion object {
        const val MAX_APPS = 20
    }
    private val coroutineScope: CoroutineScope = MainScope()
    private val viewModel = AppViewModel()
    private lateinit var appDataStore: AppDataStore
    private lateinit var senderHelper: PebbleSenderHelper

    // Return true if any of the import warning conditions are met.
    private fun hasImportWarnings(result: ImportResult): Boolean =
        result.skippedPackages.isNotEmpty() ||
            result.duplicatePackages.isNotEmpty() ||
            result.duplicatePositionPackages.isNotEmpty() ||
            result.outOfRangePackages.isNotEmpty() ||
            result.multipleAutoLaunch ||
            result.maxAppsExceeded

    // Load persisted preferences from data store into the ViewModel.
    private fun loadPersistedPrefs() {
        viewModel.setApps(appDataStore.apps.value)
        viewModel.setShowSystemApps(appDataStore.getShowSystemApps())
        viewModel.setGenerateCrashReports(appDataStore.getGenerateCrashReports())
        viewModel.setAppTheme(appDataStore.getAppTheme())
        viewModel.setVibrationPref(appDataStore.getVibrationPref())
        viewModel.setAutoClose(appDataStore.getAutoClose())
        viewModel.setPlayOnLaunch(appDataStore.getPlayOnLaunch())
        viewModel.setPlayOnLaunchTimeoutS(appDataStore.getPlayOnLaunchTimeoutS())
        viewModel.setPlayOnLaunchFirstPhaseS(appDataStore.getPlayOnLaunchFirstPhaseS())
        viewModel.setAutoLaunchEnabled(appDataStore.getAutoLaunchEnabled())
        viewModel.setAutoLaunchTarget(appDataStore.getAutoLaunchTarget())
        viewModel.setConnectionStatus(getString(R.string.status_disconnected))
    }

    // Flip the persisted play-on-launch pref back to OFF when notification access is missing,
    // so a feature ON without permission can never trigger useless flows. No-op when the
    // pref is already false (idempotent across repeated onResume calls).
    private fun autoFlipPlayOnLaunchIfAccessMissing() {
        if (!appDataStore.getPlayOnLaunch()) return
        if (checkNotificationListenerAccess(this)) return
        AppLogBuffer.warn("MainActivity", "Play on launch auto-disabled: notification access revoked")
        viewModel.setPlayOnLaunch(false)
        appDataStore.setPlayOnLaunch(false)
    }

    // Ensure the auto-launch target index is within bounds; resets to 0 and syncs if out of range.
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

    @Suppress("CognitiveComplexMethod", "LongMethod", "CyclomaticComplexMethod")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize data store, Pebble sender, load persisted prefs into ViewModel, set up Compose UI.
        super.onCreate(savedInstanceState)
        AppLogBuffer.info("MainActivity", "App started")
        appDataStore = AppDataStore(this)
        senderHelper = PebbleSenderHelper(this)

    // Load persisted data into ViewModel BEFORE setContent to avoid initial flash
    loadPersistedPrefs()
    autoFlipPlayOnLaunchIfAccessMissing()

        setContent {
            val appTheme by viewModel.appTheme.collectAsState()
            PLauncherTheme(theme = appTheme) {
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
                val playOnLaunch by viewModel.playOnLaunch.collectAsState()
                val playOnLaunchTimeoutS by viewModel.playOnLaunchTimeoutS.collectAsState()
                val playOnLaunchFirstPhaseS by viewModel.playOnLaunchFirstPhaseS.collectAsState()
                val autoLaunchEnabled by viewModel.autoLaunchEnabled.collectAsState()
                val autoLaunchTarget by viewModel.autoLaunchTarget.collectAsState()

                var selectedTab by remember { mutableStateOf(AppTab.Apps) }

                val canDrawOverlays = remember(resumeCounter) {
                    mutableStateOf(false).apply { value = checkCanDrawOverlays(this@MainActivity) }
                }
                val ignoringBatteryOpt = remember(resumeCounter) {
                    mutableStateOf(false).apply { value = checkIgnoringBatteryOptimizations(this@MainActivity) }
                }
                val notificationAccessGranted = remember(resumeCounter) {
                    mutableStateOf(false).apply { value = checkNotificationListenerAccess(this@MainActivity) }
                }

                var importPendingResult: ImportResult? by remember { mutableStateOf(null) }
                var importWarningsResult: ImportResult? by remember { mutableStateOf(null) }

                val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    if (result.resultCode != android.app.Activity.RESULT_OK) return@rememberLauncherForActivityResult
                    val uri = result.data?.data ?: return@rememberLauncherForActivityResult
                    val yamlContent = try {
                        val stream = contentResolver.openInputStream(uri)
                        val reader = stream?.bufferedReader()
                        reader?.use { it.readText() } ?: ""
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
                    val hasWarnings = hasImportWarnings(parsedResult)
                    if (parsedResult.apps.isEmpty() && !hasWarnings) {
                        Toast.makeText(this, R.string.import_empty_file, Toast.LENGTH_SHORT).show()
                        return@rememberLauncherForActivityResult
                    }
                    AppLogBuffer.info("MainActivity", "Import parsed: ${parsedResult.apps.size} apps")
                    importPendingResult = parsedResult
                }

                fun applyImportResult(result: ImportResult) {
                    AppLogBuffer.info("MainActivity", "Import applied: ${result.apps.size} apps")
                    if (result.apps.isNotEmpty()) {
                        viewModel.setApps(result.apps)
                        viewModel.setAutoLaunchTarget(result.autoLaunchTarget)
                        dataStore.setAutoLaunchTarget(result.autoLaunchTarget)
                        dataStore.saveApps(result.apps)
                        validateAutoLaunchTarget(result.apps)
                        coroutineScope.launch {
                            senderHelper.sendAutoLaunchTarget(result.autoLaunchTarget.toUInt())
                        }
                        sendBroadcast(Intent(PebbleListenerService.ACTION_SEND_APP_LIST))
                    }
                    if (hasImportWarnings(result)) {
                        importWarningsResult = result
                    }
                }

                fun buildOriginalNames(appsList: List<LaunchApp>): Map<String, String> {
                    val map = mutableMapOf<String, String>()
                    for (app in appsList) {
                        try {
                            val info = packageManager.getApplicationInfo(app.packageName, 0)
                            val label = info.loadLabel(packageManager).toString()
                            map[app.packageName] = if (label.isBlank()) app.packageName else label
                        } catch (_: PackageManager.NameNotFoundException) {
                            map[app.packageName] = app.displayName
                        }
                    }
                    return map
                }

                var yamlExportPending by remember { mutableStateOf(false) }

                val exportYamlLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("text/yaml")
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    try {
                        val originalNames = buildOriginalNames(apps)
                        val yamlContent = dataStore.exportAppsToYaml(originalNames, autoLaunchTarget)
                        contentResolver.openOutputStream(uri)?.use { it.write(yamlContent.toByteArray()) }
                            ?: throw IOException("No output stream for $uri")
                        AppLogBuffer.info("MainActivity", "App list exported to document: $uri")
                        Toast.makeText(context, R.string.export_success, Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        AppLogBuffer.error("MainActivity", "Failed to export app list to document: ${e.message}")
                        Toast.makeText(context, R.string.export_failed, Toast.LENGTH_SHORT).show()
                    }
                }

                val onExportClick: () -> Unit = onExport@{
                    AppLogBuffer.info("MainActivity", "Export initiated")
                    if (!yamlExportPending) {
                        yamlExportPending = true
                        exportYamlLauncher.launch("plauncher_apps.yaml")
                    }
                }

                val onImportClick: () -> Unit = {
                    AppLogBuffer.info("MainActivity", "Import initiated")
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    importLauncher.launch(intent)
                }

                var logSavePending by remember { mutableStateOf(false) }

                val saveLogsLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("text/plain")
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    try {
                        val logContent = AppLogBuffer.getLogsAsString()
                        contentResolver.openOutputStream(uri)?.use { it.write(logContent.toByteArray()) }
                            ?: throw IOException("No output stream for $uri")
                        AppLogBuffer.info("MainActivity", "Logs saved to document: $uri")
                        Toast.makeText(context, R.string.logs_saved_success, Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        AppLogBuffer.error("MainActivity", "Failed to save logs to document: ${e.message}")
                        Toast.makeText(context, R.string.logs_saved_error, Toast.LENGTH_SHORT).show()
                    }
                }

                val onSaveLogsClick: () -> Unit = onSaveLogs@{
                    AppLogBuffer.info("MainActivity", "Save logs initiated")
                    if (!logSavePending) {
                        if (AppLogBuffer.getEntries().isEmpty()) {
                            Toast.makeText(context, R.string.logs_empty, Toast.LENGTH_SHORT).show()
                            return@onSaveLogs
                        }
                        logSavePending = true
                        saveLogsLauncher.launch("plauncher_logs.txt")
                    }
                }

                fun syncAppList(newApps: List<LaunchApp>) {
                    viewModel.setApps(newApps)
                    dataStore.saveApps(newApps)
                    validateAutoLaunchTarget(newApps)
                    sendBroadcast(Intent(PebbleListenerService.ACTION_SEND_APP_LIST))
                }

                var showPermissionDialog by remember { mutableStateOf(!canDrawOverlays.value || !ignoringBatteryOpt.value) }
                var dismissedOnce by remember { mutableStateOf(false) }

                LaunchedEffect(resumeCounter) {
                    if (logSavePending) logSavePending = false
                    if (yamlExportPending) yamlExportPending = false
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
                                selected = selectedTab == AppTab.Apps,
                                onClick = { selectedTab = AppTab.Apps },
                                label = { Text(stringResource(R.string.tab_apps)) }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.tab_settings)) },
                                selected = selectedTab == AppTab.Settings,
                                onClick = { selectedTab = AppTab.Settings },
                                label = { Text(stringResource(R.string.tab_settings)) }
                            )
                        }
                    }
                ) { padding ->
                    if (selectedTab == AppTab.Apps) {
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
                                    syncAppList(reordered)
                                }
                            },
                            onSortApps = { order ->
                                val sorted = viewModel.sortApps(order)
                                if (sorted !== apps) {
                                    syncAppList(sorted)
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
                            notificationAccessGranted = notificationAccessGranted.value,
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
                            playOnLaunch = playOnLaunch,
                            onPlayOnLaunchChange = { newValue ->
                                if (newValue && !checkNotificationListenerAccess(this)) {
                                    Toast.makeText(
                                        this,
                                        R.string.play_on_launch_requires_notification_access,
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    viewModel.setPlayOnLaunch(newValue)
                                    dataStore.setPlayOnLaunch(newValue)
                                    // With the feature off and nothing in flight, let the notification
                                    // listener idle (best effort) so it stops consuming system notifications.
                                    if (!newValue && !MediaControlListenerService.isFlowActive) {
                                        MediaControlListenerService.stopListenerBestEffort()
                                    }
                                }
                            },
                            playOnLaunchTimeoutS = playOnLaunchTimeoutS,
                            onPlayOnLaunchTimeoutChange = { newValue ->
                                viewModel.setPlayOnLaunchTimeoutS(newValue)
                                dataStore.setPlayOnLaunchTimeoutS(newValue)
                            },
                            onPlayOnLaunchTimeoutInvalid = { viewModel.onPlayOnLaunchTimeoutInvalid() },
                            playOnLaunchFirstPhaseS = playOnLaunchFirstPhaseS,
                            onPlayOnLaunchFirstPhaseChange = { newValue ->
                                viewModel.setPlayOnLaunchFirstPhaseS(newValue)
                                dataStore.setPlayOnLaunchFirstPhaseS(newValue)
                            },
                            onPlayOnLaunchFirstPhaseInvalid = { viewModel.onPlayOnLaunchFirstPhaseInvalid() },
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
                            onSaveLogsClick = onSaveLogsClick,
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
                                syncAppList(updatedApps)
                                viewModel.setRemoveAppTarget(null)
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
                            val lbl = info.loadLabel(pm).toString()
                            if (lbl.isBlank()) targetApp.packageName else lbl
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
                                    color = if (editName.length >= 32)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
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
                                syncAppList(updatedApps)
                                viewModel.setRenameAppTarget(null)
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
                                    syncAppList(updatedApps)
                                    viewModel.setRenameAppTarget(null)
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
                                syncAppList(selectedApps)
                                viewModel.setShowPicker(false)
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
                                Text(text = stringResource(R.string.icon_warning))
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
                                        style = MaterialTheme.typography.bodySmall
                                            .copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                                         modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                     )
                                     result.skippedPackages.forEach { pkg ->
                                         Text(
                                             text = "  $pkg",
                                             style = MaterialTheme.typography.bodySmall
                                                 .copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }

                                if (result.duplicatePackages.isNotEmpty()) {
                                    HorizontalDivider()
                                    Text(
                                        text = getString(R.string.import_duplicate_packages),
                                        style = MaterialTheme.typography.bodySmall
                                            .copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                                         modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                     )
                                     result.duplicatePackages.forEach { pkg ->
                                         Text(
                                             text = "  $pkg",
                                             style = MaterialTheme.typography.bodySmall
                                                 .copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }

                                if (result.duplicatePositionPackages.isNotEmpty()) {
                                    HorizontalDivider()
                                    Text(
                                        text = getString(R.string.import_duplicate_positions),
                                        style = MaterialTheme.typography.bodySmall
                                            .copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                                         modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                     )
                                     result.duplicatePositionPackages.forEach { pkg ->
                                         Text(
                                             text = "  $pkg",
                                             style = MaterialTheme.typography.bodySmall
                                                 .copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }

                                if (result.outOfRangePackages.isNotEmpty()) {
                                    HorizontalDivider()
                                    Text(
                                        text = getString(R.string.import_position_out_of_range),
                                        style = MaterialTheme.typography.bodySmall
                                            .copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                                         modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                     )
                                     result.outOfRangePackages.forEach { pkg ->
                                         Text(
                                             text = "  $pkg",
                                             style = MaterialTheme.typography.bodySmall
                                                 .copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }

                                if (result.multipleAutoLaunch) {
                                    HorizontalDivider()
                                    Text(
                                        text = getString(R.string.import_multiple_auto_launch),
                                        style = MaterialTheme.typography.bodySmall
                                            .copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                                         modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                     )
                                     result.multipleAutoLaunchPackages.forEach { pkg ->
                                         Text(
                                             text = "  $pkg",
                                             style = MaterialTheme.typography.bodySmall
                                                 .copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }

                                if (result.maxAppsExceeded) {
                                    HorizontalDivider()
                                    Text(
                                        text = getString(R.string.import_max_apps_exceeded),
                                        style = MaterialTheme.typography.bodySmall
                                            .copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
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

    // Increment resume counter to trigger permission recheck in Compose; auto-flips the
    // play-on-launch pref if notification access was revoked while it was ON.
    override fun onResume() {
        super.onResume()
        AppLogBuffer.debug("MainActivity", "Activity resumed")
        viewModel.onActivityResume()
        autoFlipPlayOnLaunchIfAccessMissing()
    }

    // Close Pebble sender to free resources.
    override fun onDestroy() {
        super.onDestroy()
        try {
            senderHelper.close()
        } catch (e: Exception) {
            // Ignore if sender was already closed
        }
    }
}

package com.le0xff.plauncher

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
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
import com.le0xff.plauncher.model.LaunchApp
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

    fun onActivityResume() {
        _resumeCounter.value++
    }
}

class MainActivity : ComponentActivity() {
    private val coroutineScope: CoroutineScope = MainScope()
    private val viewModel = AppViewModel()
    private lateinit var appDataStore: AppDataStore
    private lateinit var senderHelper: PebbleSenderHelper

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

                val initialStatus = stringResource(R.string.status_disconnected)
                LaunchedEffect(Unit) {
                    viewModel.setApps(dataStore.apps.value)
                    viewModel.setShowSystemApps(dataStore.getShowSystemApps())
                    viewModel.setGenerateCrashReports(dataStore.getGenerateCrashReports())
                    viewModel.setAppTheme(dataStore.getAppTheme())
                    viewModel.setConnectionStatus(initialStatus)
                }

                var selectedTab by remember { mutableStateOf(0) }

                val canDrawOverlays = remember(resumeCounter) {
                    mutableStateOf(checkCanDrawOverlays(this))
                }
                val ignoringBatteryOpt = remember(resumeCounter) {
                    mutableStateOf(checkIgnoringBatteryOptimizations(this))
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
                            onAddApp = { viewModel.setShowPicker(true) },
                            onRemoveApp = { viewModel.setRemoveAppTarget(it) },
                            onRenameApp = { viewModel.setRenameAppTarget(it) },
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
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                placeholder = { Text(originalName, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val finalName = if (editName.isBlank()) originalName else editName.take(32)
                                val updatedApps = apps.map { a ->
                                    if (a.packageName == targetApp.packageName) LaunchApp(a.packageName, finalName) else a
                                }
                                dataStore.saveApps(updatedApps)
                                viewModel.setApps(updatedApps)
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
                            onConfirm = { selectedApps ->
                                dataStore.saveApps(selectedApps)
                                viewModel.setApps(selectedApps)
                                viewModel.setShowPicker(false)
                                // Push updated list to watch using MainActivity's own sender
                                coroutineScope.launch {
                                    senderHelper.sendAppList(selectedApps, null)
                                }
                                // Also notify PebbleListenerService if it is running
                                sendBroadcast(Intent(PebbleListenerService.ACTION_SEND_APP_LIST))
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

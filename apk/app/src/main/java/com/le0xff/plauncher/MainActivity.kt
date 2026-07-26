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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.le0xff.plauncher.data.AppDataStore
import com.le0xff.plauncher.model.LaunchApp
import com.le0xff.plauncher.ui.AppPickerDialog
import com.le0xff.plauncher.ui.AppScreen
import com.le0xff.plauncher.ui.SettingsScreen
import com.le0xff.plauncher.ui.checkCanDrawOverlays
import com.le0xff.plauncher.ui.checkIgnoringBatteryOptimizations
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

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

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

    fun setConnectionStatus(status: String) {
        _connectionStatus.value = status
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
            MaterialTheme {
                val context = LocalContext.current
                val dataStore = remember { appDataStore }

                val apps by viewModel.apps.collectAsState()
                val showSystemApps by viewModel.showSystemApps.collectAsState()
                val generateCrashReports by viewModel.generateCrashReports.collectAsState()
                val showPicker by viewModel.showPicker.collectAsState()
                val searchQuery by viewModel.searchQuery.collectAsState()
                val connectionStatus by viewModel.connectionStatus.collectAsState()
                val resumeCounter by viewModel.resumeCounter.collectAsState()

                LaunchedEffect(Unit) {
                    viewModel.setApps(dataStore.apps.value)
                    viewModel.setShowSystemApps(dataStore.getShowSystemApps())
                    viewModel.setGenerateCrashReports(dataStore.getGenerateCrashReports())
                }

                var selectedTab by remember { mutableStateOf(0) }

                val canDrawOverlays = remember(resumeCounter) {
                    mutableStateOf(checkCanDrawOverlays(this))
                }
                val ignoringBatteryOpt = remember(resumeCounter) {
                    mutableStateOf(checkIgnoringBatteryOptimizations(this))
                }

                var showPermissionDialog by remember { mutableStateOf(!canDrawOverlays.value || !ignoringBatteryOpt.value) }

                if (showPermissionDialog) {
                    AlertDialog(
                        onDismissRequest = { showPermissionDialog = false },
                        title = { Text("Background Launch Permissions") },
                        text = {
                            Column {
                                Text("To launch apps from Pebble when this app is in background, you need to grant special permissions:")
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("• Draw Over Other Apps")
                                Text("• Ignore Battery Optimizations")
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("You can also grant these from the Settings tab.")
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                        data = android.net.Uri.parse("package:${packageName}")
                                    }
                                    startActivity(intent)
                                }
                                showPermissionDialog = false
                            }) {
                                Text("Open Settings")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPermissionDialog = false }) {
                                Text("Dismiss")
                            }
                        }
                    )
                }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Apps") },
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                label = { Text("Apps") }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                label = { Text("Settings") }
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
                            modifier = Modifier.padding(padding)
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
                                coroutineScope.launch {
                                    senderHelper.sendAppList(selectedApps, null)
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

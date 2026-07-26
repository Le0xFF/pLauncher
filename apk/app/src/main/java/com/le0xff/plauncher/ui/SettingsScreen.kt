package com.le0xff.plauncher.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    showSystemApps: Boolean,
    onShowSystemAppsChange: (Boolean) -> Unit,
    generateCrashReports: Boolean,
    onGenerateCrashReportsChange: (Boolean) -> Unit,
    canDrawOverlays: Boolean,
    ignoringBatteryOpt: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(16.dp))

        ListItem(
            headlineContent = { Text("Show system apps") },
            trailingContent = {
                Switch(checked = showSystemApps, onCheckedChange = onShowSystemAppsChange)
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        ListItem(
            headlineContent = { Text("Generate crash reports") },
            supportingContent = { Text("Show crash details when the app crashes") },
            trailingContent = {
                Switch(checked = generateCrashReports, onCheckedChange = onGenerateCrashReportsChange)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Background Launch Permissions", style = MaterialTheme.typography.titleMedium)
        Text("Required for launching apps from Pebble when this app is in background", style = MaterialTheme.typography.bodySmall)

        Spacer(modifier = Modifier.height(8.dp))

        ListItem(
            headlineContent = { Text("Draw Over Other Apps") },
            supportingContent = { Text("Allows launching apps from background service") },
            trailingContent = {
                if (canDrawOverlays) {
                    Text("Granted")
                } else {
                    Button(onClick = {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }) {
                        Text("Grant")
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(4.dp))

        ListItem(
            headlineContent = { Text("Ignore Battery Optimizations") },
            supportingContent = { Text("Prevents aggressive service killing") },
            trailingContent = {
                if (ignoringBatteryOpt) {
                    Text("Granted")
                } else {
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    }) {
                        Text("Grant")
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text("pLauncher v1.0.0", style = MaterialTheme.typography.bodySmall)
    }
}

fun checkCanDrawOverlays(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        return Settings.canDrawOverlays(context)
    }
    return true
}

fun checkIgnoringBatteryOptimizations(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
    return true
}

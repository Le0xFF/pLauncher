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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.le0xff.plauncher.R

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
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(16.dp))

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_show_system_apps)) },
            trailingContent = {
                Switch(checked = showSystemApps, onCheckedChange = onShowSystemAppsChange)
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_generate_crash_reports)) },
            supportingContent = { Text(stringResource(R.string.settings_crash_reports_desc)) },
            trailingContent = {
                Switch(checked = generateCrashReports, onCheckedChange = onGenerateCrashReportsChange)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(stringResource(R.string.settings_perm_section_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_perm_section_desc), style = MaterialTheme.typography.bodySmall)

        Spacer(modifier = Modifier.height(8.dp))

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_draw_overlays)) },
            supportingContent = { Text(stringResource(R.string.settings_draw_overlays_desc)) },
            trailingContent = {
                if (canDrawOverlays) {
                    Text(stringResource(R.string.settings_granted))
                } else {
                    Button(onClick = {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }) {
                        Text(stringResource(R.string.button_grant))
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(4.dp))

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_ignore_battery)) },
            supportingContent = { Text(stringResource(R.string.settings_ignore_battery_desc)) },
            trailingContent = {
                if (ignoringBatteryOpt) {
                    Text(stringResource(R.string.settings_granted))
                } else {
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    }) {
                        Text(stringResource(R.string.button_grant))
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(stringResource(R.string.settings_version), style = MaterialTheme.typography.bodySmall)
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

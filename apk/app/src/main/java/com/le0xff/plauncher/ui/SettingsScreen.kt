package com.le0xff.plauncher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    showSystemApps: Boolean,
    onShowSystemAppsChange: (Boolean) -> Unit,
    generateCrashReports: Boolean,
    onGenerateCrashReportsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
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

        Spacer(modifier = Modifier.height(32.dp))

        Text("pLauncher v1.0.0", style = MaterialTheme.typography.bodySmall)
    }
}

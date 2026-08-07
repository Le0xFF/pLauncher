package com.le0xff.plauncher.ui

/**
 * pLauncher Companion App — Settings screen with expandable accordion panels for general, watchapp, permissions, and debug options.
 *
 * @author Le0xFF
 */

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.le0xff.plauncher.PbwInstaller
import com.le0xff.plauncher.R

private const val VibrationNone = 0
private const val VibrationShort = 1
private const val VibrationLong = 2
private const val VibrationDouble = 3

/**
 * Settings screen: expandable accordion panels for general, watchapp, import/export, permissions, install, and debug options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    showSystemApps: Boolean,
    onShowSystemAppsChange: (Boolean) -> Unit,
    generateCrashReports: Boolean,
    onGenerateCrashReportsChange: (Boolean) -> Unit,
    canDrawOverlays: Boolean,
    ignoringBatteryOpt: Boolean,
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    vibrationPref: Int,
    onVibrationPrefChange: (Int) -> Unit,
    autoClose: Boolean,
    onAutoCloseChange: (Boolean) -> Unit,
    autoLaunch: Boolean,
    onAutoLaunchChange: (Boolean) -> Unit,
    onExportClick: () -> Unit = {},
    onImportClick: () -> Unit = {},
    onSaveLogsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var generalExpanded by remember { mutableStateOf(false) }
    var watchappExpanded by remember { mutableStateOf(false) }
    var permissionsExpanded by remember { mutableStateOf(false) }
    var installExpanded by remember { mutableStateOf(false) }
    var debugExpanded by remember { mutableStateOf(false) }
    var importExportExpanded by remember { mutableStateOf(false) }

    Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(16.dp))

        AccordionCard(
            title = stringResource(R.string.settings_section_general),
            expanded = generalExpanded,
            onExpandedChange = { generalExpanded = it }
        ) {
            val themeLabels = mapOf(
                AppTheme.Light to stringResource(R.string.settings_theme_light),
                AppTheme.Dark to stringResource(R.string.settings_theme_dark),
                AppTheme.Amoled to stringResource(R.string.settings_theme_amoled)
            )
            var themeExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
                    Text(text = stringResource(R.string.settings_theme_desc), style = MaterialTheme.typography.bodySmall)
                }
            ExposedDropdownMenuBox(
                    expanded = themeExpanded,
                    onExpandedChange = { themeExpanded = it }
                ) {
                    Row(
                    modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .widthIn(min = 80.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = themeLabels[currentTheme] ?: stringResource(R.string.settings_theme_light),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Icon(
                            imageVector = if (themeExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                ExposedDropdownMenu(
                    expanded = themeExpanded,
                    onDismissRequest = { themeExpanded = false }
                ) {
                    themeLabels.forEach { (theme, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onThemeChange(theme)
                                    themeExpanded = false
                            }
                        )
                    }
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_show_system_apps),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = showSystemApps, onCheckedChange = onShowSystemAppsChange)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        AccordionCard(
            title = stringResource(R.string.settings_section_watchapp),
            expanded = watchappExpanded,
            onExpandedChange = { watchappExpanded = it }
        ) {
            val vibeLabels = mapOf(
                VibrationNone to stringResource(R.string.settings_vibration_none),
                VibrationShort to stringResource(R.string.settings_vibration_short),
                VibrationLong to stringResource(R.string.settings_vibration_long),
                VibrationDouble to stringResource(R.string.settings_vibration_double)
            )
            var vibeExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.settings_vibration), style = MaterialTheme.typography.bodyLarge)
                    Text(text = stringResource(R.string.settings_vibration_desc), style = MaterialTheme.typography.bodySmall)
                }
                ExposedDropdownMenuBox(
                    expanded = vibeExpanded,
                    onExpandedChange = { vibeExpanded = it }
                ) {
                    Row(
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .widthIn(min = 80.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = vibeLabels[vibrationPref] ?: stringResource(R.string.settings_vibration_none),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Icon(
                            imageVector = if (vibeExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ExposedDropdownMenu(
                        expanded = vibeExpanded,
                        onDismissRequest = { vibeExpanded = false }
                    ) {
                        vibeLabels.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onVibrationPrefChange(value)
                                    vibeExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.settings_auto_close), style = MaterialTheme.typography.bodyLarge)
                    Text(text = stringResource(R.string.settings_auto_close_desc), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = autoClose, onCheckedChange = onAutoCloseChange)
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.settings_auto_launch), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(R.string.settings_auto_launch_desc),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                }
                Switch(checked = autoLaunch, onCheckedChange = onAutoLaunchChange)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        AccordionCard(
            title = stringResource(R.string.settings_section_import_export),
            expanded = importExportExpanded,
            onExpandedChange = { importExportExpanded = it }
        ) {
            Text(text = stringResource(R.string.settings_import_export_desc), style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.settings_yaml_example),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onImportClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.button_import))
                }
                Button(
                    onClick = onExportClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.button_export))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        AccordionCard(
            title = stringResource(R.string.settings_section_permissions),
            expanded = permissionsExpanded,
            onExpandedChange = { permissionsExpanded = it }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.settings_draw_overlays), style = MaterialTheme.typography.bodyLarge)
                    Text(text = stringResource(R.string.settings_draw_overlays_desc), style = MaterialTheme.typography.bodySmall)
                }
                if (canDrawOverlays) {
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text(stringResource(R.string.button_revoke))
                    }
                } else {
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        },
                        contentPadding = PaddingValues(horizontal = 18.dp)
                    ) {
                        Text(stringResource(R.string.button_grant))
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.settings_ignore_battery), style = MaterialTheme.typography.bodyLarge)
                    Text(text = stringResource(R.string.settings_ignore_battery_desc), style = MaterialTheme.typography.bodySmall)
                }
                if (ignoringBatteryOpt) {
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text(stringResource(R.string.button_revoke))
                    }
                } else {
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 18.dp)
                    ) {
                        Text(stringResource(R.string.button_grant))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        AccordionCard(
            title = stringResource(R.string.settings_section_install_watchapp),
            expanded = installExpanded,
            onExpandedChange = { installExpanded = it }
        ) {
            Text(
                text = stringResource(R.string.settings_install_watchapp_desc),
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            val pbwInfo = remember { PbwInstaller.getInfo(context) }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.watchapp_label_version),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(min = 60.dp)
                )
                Text(
                    text = pbwInfo.version,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.watchapp_label_md5),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(min = 60.dp)
                )
                Text(
                    text = pbwInfo.md5,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    when {
                        !PbwInstaller.isBundled(context) ->
                            Toast.makeText(context, R.string.install_watchapp_missing, Toast.LENGTH_LONG).show()
                        !PbwInstaller.install(context) ->
                            Toast.makeText(context, R.string.install_watchapp_none, Toast.LENGTH_LONG).show()
                        else ->
                            PbwInstaller.install(context)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.button_install_watchapp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        AccordionCard(
            title = stringResource(R.string.settings_section_debug),
            expanded = debugExpanded,
            onExpandedChange = { debugExpanded = it }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.settings_generate_crash_reports), style = MaterialTheme.typography.bodyLarge)
                    Text(text = stringResource(R.string.settings_crash_reports_desc), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = generateCrashReports, onCheckedChange = onGenerateCrashReportsChange)
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.settings_save_logs), style = MaterialTheme.typography.bodyLarge)
                    Text(text = stringResource(R.string.settings_save_logs_desc), style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = onSaveLogsClick,
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text(stringResource(R.string.button_save_logs))
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.settings_crash_the_app), style = MaterialTheme.typography.bodyLarge)
                    Text(text = stringResource(R.string.settings_crash_the_app_desc), style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { throw RuntimeException("Test crash from settings") },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text(stringResource(R.string.settings_crash_button))
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        val githubRepoUrl = stringResource(R.string.github_repo_url)
        Text(
            text = stringResource(R.string.settings_version),
            style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(githubRepoUrl))
                    context.startActivity(intent)
                }
        )
    }
}

/**
 * Expandable card with animated collapse/expand. Clicking the header toggles visibility of the content section.
 */
@Composable
private fun AccordionCard(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onExpandedChange(!expanded) }
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(
                animationSpec = spring(),
                expandFrom = Alignment.Top
            ),
            exit = shrinkVertically(
                animationSpec = spring(),
                shrinkTowards = Alignment.Top
            ) + fadeOut()
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                content()
            }
        }
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

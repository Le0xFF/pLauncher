package com.le0xff.plauncher

/**
 * pLauncher Companion App — Activity that displays crash report details with copy-to-clipboard, copyable stack trace, and restart/close actions.
 *
 * @author Le0xFF
 */

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.le0xff.plauncher.R

@OptIn(ExperimentalMaterial3Api::class)
class CrashReportActivity : ComponentActivity() {

    // Parse crash report from intent, render structured UI with exception details, stack trace, and action buttons.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashReport = intent.getStringExtra("CRASH_REPORT")

        setContent {
            MaterialTheme {
                val clipboardManager = LocalClipboardManager.current
                val copiedSnackbarText = stringResource(R.string.crash_copied_snackbar)
                var snackbarMessage by remember { mutableStateOf<String?>(null) }

                Box(modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        MaterialTheme {
                            Scaffold(
                                topBar = {
                                    TopAppBar(title = { Text(stringResource(R.string.crash_title)) })
                                }
                            ) { padding ->
                                Column(Modifier.padding(padding).fillMaxSize()) {
                                    if (!crashReport.isNullOrBlank()) {
                                        val parsed = parseCrashReport(crashReport)

                                        Column(
                                            Modifier
                                                .weight(1f)
                                                .verticalScroll(rememberScrollState())
                                                .fillMaxWidth()
                                                .padding(16.dp)
                                        ) {
                                            Text(
                                                text = "${stringResource(R.string.crash_label_exception)}${parsed.exception}",
                                                style = MaterialTheme.typography.headlineSmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                text = "${stringResource(R.string.crash_label_message)}${parsed.message}",
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            Spacer(Modifier.height(16.dp))
                                            HorizontalDivider()
                                            Spacer(Modifier.height(16.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.crash_label_stacktrace),
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                TextButton(
                                                    onClick = {
                                                        clipboardManager.setText(AnnotatedString(parsed.stackTrace))
                                                        snackbarMessage = copiedSnackbarText
                                                    }
                                                ) {
                                                    Icon(
                                                        Icons.Default.ContentCopy,
                                                        contentDescription = stringResource(R.string.crash_button_copy),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(stringResource(R.string.crash_button_copy))
                                                }
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                                )
                                            ) {
                                                Column(modifier = Modifier.padding(16.dp)) {
                                                    if (parsed.deviceInfo.isNotEmpty()) {
                                                        for (line in parsed.deviceInfo) {
                                                            Text(
                                                                text = line,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Spacer(Modifier.height(2.dp))
                                                        }
                                                        Spacer(Modifier.height(8.dp))
                                                        Text(
                                                            text = stringResource(R.string.crash_divider),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        Spacer(Modifier.height(8.dp))
                                                    }
                                                    Text(
                                                        text = parsed.stackTrace,
                                                        fontFamily = FontFamily.Monospace,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier.weight(1f).fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(stringResource(R.string.crash_unexpected_error))
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val intent = Intent(this@CrashReportActivity, MainActivity::class.java)
                                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                                startActivity(intent)
                                                finish()
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(stringResource(R.string.button_restart_app))
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                finishAndRemoveTask()
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(stringResource(R.string.button_close_app))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (snackbarMessage != null) {
                        Text(
                            text = snackbarMessage!!,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    private data class ParsedCrashReport(
        val exception: String,
        val message: String,
        val stackTrace: String,
        val deviceInfo: List<String>
    )

    private fun parseCrashReport(report: String): ParsedCrashReport {
        val lines = report.lines()
        val labelException = getString(R.string.report_label_exception)
        val labelMessage = getString(R.string.report_label_message)
        val noMessage = getString(R.string.report_no_message)
        val labelStacktrace = getString(R.string.report_label_stacktrace)
        val labelDevice = getString(R.string.report_label_device)

        val exception = lines.firstOrNull { it.startsWith(labelException) }
            ?.substringAfter(labelException)?.trim() ?: "Unknown"
        val message = lines.firstOrNull { it.startsWith(labelMessage) }
            ?.substringAfter(labelMessage)?.trim() ?: noMessage

        val stackTraceStart = lines.indexOfFirst { it == labelStacktrace }
        val deviceStart = lines.indexOfFirst { it.startsWith(labelDevice) }

        val stackTraceLines = if (stackTraceStart >= 0 && deviceStart > stackTraceStart) {
            lines.subList(stackTraceStart + 1, deviceStart).filter { it.isNotBlank() }
        } else {
            emptyList()
        }

        val deviceInfoLines = if (deviceStart >= 0) {
            lines.subList(deviceStart, lines.size)
        } else {
            emptyList()
        }

        return ParsedCrashReport(
            exception = exception,
            message = message,
            stackTrace = stackTraceLines.joinToString("\n"),
            deviceInfo = deviceInfoLines
        )
    }
}

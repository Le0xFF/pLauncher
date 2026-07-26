package com.le0xff.plauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.le0xff.plauncher.R

@OptIn(ExperimentalMaterial3Api::class)
class CrashReportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashReport = intent.getStringExtra("CRASH_REPORT")

        setContent {
            MaterialTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(title = { Text(stringResource(R.string.crash_title)) })
                    }
                ) { padding ->
                    Column(Modifier.padding(padding)) {
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
                                Divider()
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.crash_label_stacktrace),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(8.dp))
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Text(
                                        text = parsed.stackTrace,
                                        modifier = Modifier.padding(16.dp),
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(16.dp))
                                Divider()
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.crash_label_device_info),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(8.dp))
                                for (line in parsed.deviceInfo) {
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(Modifier.height(4.dp))
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

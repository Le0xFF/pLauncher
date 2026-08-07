package com.le0xff.plauncher

/**
 * pLauncher Companion App — Transient Activity that launches a target Android
 * app by package name and broadcasts the result to PebbleListenerService.
 *
 * @author Le0xFF
 */

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.le0xff.plauncher.data.AppLogBuffer

class LaunchActivity : ComponentActivity() {
    companion object {
        const val ACTION_LAUNCH_RESULT = "com.le0xff.plauncher.LAUNCH_RESULT"
        private const val EXTRA_RESULT = "result"
    }

    // Trampoline: extract package name, resolve launch intent, start target app, broadcast result, finish immediately.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra("package_name") ?: run {
            AppLogBuffer.warn("LaunchActivity", "No package name in intent")
            sendLaunchResult(false)
            finish()
            return
        }
        AppLogBuffer.info("LaunchActivity", "Launching package: $packageName")
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            try {
                startActivity(launchIntent)
                AppLogBuffer.info("LaunchActivity", "Launch success: $packageName")
                sendLaunchResult(true)
            } catch (e: Exception) {
                AppLogBuffer.error("LaunchActivity", "Launch failed: ${e.message}")
                sendLaunchResult(false)
            }
        } else {
            AppLogBuffer.warn("LaunchActivity", "No launch intent for: $packageName")
            sendLaunchResult(false)
        }
        finish()
    }

    private fun sendLaunchResult(success: Boolean) {
        val intent = Intent(ACTION_LAUNCH_RESULT).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_RESULT, if (success) 1 else 0)
        }
        sendBroadcast(intent)
    }
}

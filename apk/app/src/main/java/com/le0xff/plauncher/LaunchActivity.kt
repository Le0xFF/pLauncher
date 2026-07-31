package com.le0xff.plauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class LaunchActivity : ComponentActivity() {
    companion object {
        const val ACTION_LAUNCH_RESULT = "com.le0xff.plauncher.LAUNCH_RESULT"
        private const val EXTRA_RESULT = "result"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra("package_name") ?: run {
            sendLaunchResult(false)
            finish()
            return
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            try {
                startActivity(launchIntent)
                sendLaunchResult(true)
            } catch (e: Exception) {
                sendLaunchResult(false)
            }
        } else {
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

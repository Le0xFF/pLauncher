package com.le0xff.plauncher

/**
 * pLauncher Companion App — BroadcastReceiver that restarts PebbleListenerService as a foreground service on device boot.
 *
 * @author Le0xFF
 */

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, PebbleListenerService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}

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
import com.le0xff.plauncher.data.AppDataStore
import com.le0xff.plauncher.media.MediaControlListenerService
import com.le0xff.plauncher.ui.checkNotificationListenerAccess

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, PebbleListenerService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
            // Rebind the notification listener in case a slow OEM dropped the binding at boot, so the
            // first launch after reboot can resume playback. No-op when the feature is off or ungranted.
            val dataStore = AppDataStore(context)
            if (dataStore.getPlayOnLaunch() && checkNotificationListenerAccess(context)) {
                MediaControlListenerService.requestRebindIfUnbound(context)
            }
        }
    }
}

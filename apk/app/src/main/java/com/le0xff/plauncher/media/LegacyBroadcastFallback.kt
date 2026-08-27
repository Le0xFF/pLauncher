package com.le0xff.plauncher.media

/**
 * pLauncher Companion App — Sends a legacy MEDIA_BUTTON play broadcast to a target package.
 *
 * @author Le0xFF
 */

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import com.le0xff.plauncher.data.AppLogBuffer

object LegacyBroadcastFallback {

    private const val TAG = "MediaResumer"

    fun sendPlay(context: Context, packageName: String) {
        try {
            val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(Intent.EXTRA_KEY_EVENT, keyEvent)
                setPackage(packageName)
            }
            context.sendBroadcast(intent)
            AppLogBuffer.info(TAG, "Legacy media button broadcast sent to $packageName")
        } catch (e: Exception) {
            AppLogBuffer.warn(TAG, "Failed to send legacy broadcast to $packageName: ${e.message}")
        }
    }
}
package com.le0xff.plauncher.util

/**
 * pLauncher Companion App — Helper that keeps the device screen on for the
 * play-on-launch flow. Uses a [PowerManager.ACQUIRE_CAUSES_WAKEUP] wake lock:
 * holding it turns the display on (keyguard stays up) and keeps it on; releasing
 * it lets the display return to its previous state once nothing else holds it.
 *
 * @author Le0xFF
 */

import android.content.Context
import android.os.PowerManager
import com.le0xff.plauncher.R
import com.le0xff.plauncher.data.AppLogBuffer

object ScreenWakeHelper {

    private const val TAG = "ScreenWake"

    // Lock kept while the screen must stay on during the resume flow. Released by turnScreenOff().
    private var displayWakeLock: PowerManager.WakeLock? = null

    // Returns true if the power manager reports the device as interactive (screen on).
    fun isScreenOn(context: Context): Boolean {
        return (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
    }

    /**
     * Acquires the ACQUIRE_CAUSES_WAKEUP lock so the display turns on (if off) and
     * stays on for the rest of the flow. The lock is held indefinitely until
     * [turnScreenOff] releases it, so nothing else can turn the screen off early.
     * Returns false when the screen was already on, so callers know whether they
     * must later call [turnScreenOff].
     */
    fun wakeScreen(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isInteractive) {
            AppLogBuffer.info(TAG, "Screen already on, no wake-up needed")
            return false
        }
        displayWakeLock?.let { if (it.isHeld) it.release() }
        return runCatching {
            val wl = pm.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                context.getString(R.string.screen_wake_reason)
            )
            wl.setReferenceCounted(false)
            wl.acquire()
            displayWakeLock = wl
            AppLogBuffer.info(TAG, "Screen woken via ${context.getString(R.string.screen_wake_reason)} wake lock")
            true
        }.getOrElse { e ->
            AppLogBuffer.warn(TAG, "Failed to wake screen: ${e.message}")
            false
        }
    }

    // Releases the wake lock acquired by wakeScreen(), letting the screen go back to sleep.
    fun turnScreenOff(context: Context) {
        runCatching {
            displayWakeLock?.let {
                if (it.isHeld) it.release()
                AppLogBuffer.info(TAG, "Screen off released via ${context.getString(R.string.screen_sleep_reason)}")
            } ?: AppLogBuffer.warn(TAG, "No wake lock held, nothing to release for screen off")
            displayWakeLock = null
        }.onFailure { e ->
            AppLogBuffer.warn(TAG, "Failed to turn screen off: ${e.message}")
        }
    }
}
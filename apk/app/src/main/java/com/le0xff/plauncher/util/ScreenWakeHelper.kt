package com.le0xff.plauncher.util

/**
 * pLauncher Companion App — Helper that keeps the device screen on for the
 * play-on-launch flow. Uses a [PowerManager.ACQUIRE_CAUSES_WAKEUP] wake lock:
 * holding it turns the display on (keyguard stays up) and keeps it on; releasing
 * it lets the display return to its previous state once nothing else holds it.
 *
 * The lock is a single shared resource guarded by an atomic flag: [wakeScreen]
 * acquires it (idempotently) when the screen is off and arms a watchdog; [turnScreenOff]
 * releases it (idempotently) so concurrent flows share one lock and the last one out
 * simply finds nothing left to release. A watchdog force-releases the lock if it is
 * still held after [WATCHDOG_MS], so a stalled or aborted flow can never leave the
 * display on indefinitely. All operations are safe from any thread.
 *
 * @author Le0xFF
 */

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.le0xff.plauncher.R
import com.le0xff.plauncher.data.AppDataStore
import com.le0xff.plauncher.data.AppLogBuffer
import java.util.concurrent.atomic.AtomicBoolean

object ScreenWakeHelper {

    private const val TAG = "ScreenWake"
    private const val MS_PER_SECOND = 1000L

    // Upper bound for how long any single resume flow may hold the screen awake:
    // first-phase window + second-phase timeout, both user-configurable up to 60 s each.
    // Derived from the max values so the watchdog never fires on a legitimate flow;
    // only a stuck/aborted flow that exceeds the theoretical maximum is force-released.
    private const val WATCHDOG_MS =
        (AppDataStore.MAX_PLAY_ON_LAUNCH_FIRST_PHASE_S + AppDataStore.MAX_PLAY_ON_LAUNCH_TIMEOUT_S) * MS_PER_SECOND

    // True while the screen wake lock is (or should be) held. Guards the acquire/release
    // path against double-release and tells the watchdog whether there is anything to do.
    private val lockHeld = AtomicBoolean(false)
    private val watchdogArmed = AtomicBoolean(false)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val watchdogTask = Runnable {
        watchdogArmed.set(false)
        // Pure backstop: if the lock is somehow still held past the maximum legitimate flow
        // length (stalled coroutine, aborted flow whose release was lost), force it off.
        if (lockHeld.get()) {
            AppLogBuffer.warn(TAG, "Watchdog released stale screen wake lock")
            runCatching {
                displayWakeLock?.let { if (it.isHeld) it.release() }
            }.onFailure { e ->
                AppLogBuffer.warn(TAG, "Watchdog failed to release wake lock: ${e.message}")
            }
            lockHeld.set(false)
            displayWakeLock = null
        }
    }

    private var displayWakeLock: PowerManager.WakeLock? = null

    /** Returns true if the power manager reports the device as interactive (screen on). */
    fun isScreenOn(context: Context): Boolean {
        return (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
    }

    /**
     * Ensures the ACQUIRE_CAUSES_WAKEUP lock is held so the display is on (if it was off) and
     * stays on for the rest of the flow. Idempotent: concurrent calls share one lock. Returns
     * false when the screen was already on (nothing was acquired), so callers know whether they
     * must later call [turnScreenOff].
     */
    fun wakeScreen(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isInteractive) {
            AppLogBuffer.info(TAG, "Screen already on, no wake-up needed")
            return false
        }
        // Already holding the lock for another flow: nothing more to do.
        if (lockHeld.get()) {
            AppLogBuffer.info(TAG, "Wake lock already held, reusing it")
            return true
        }
        val acquired = runCatching {
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
        if (acquired) {
            lockHeld.set(true)
            armWatchdog()
        }
        return acquired
    }

    /**
     * Releases the screen wake lock, letting the display go back to sleep. Idempotent: if no lock
     * is held (already released, or the watchdog already fired) this is a no-op. Safe to call from
     * every flow end and from abort paths.
     */
    fun turnScreenOff(context: Context) {
        if (!lockHeld.compareAndSet(true, false)) {
            AppLogBuffer.info(TAG, "No wake lock held, nothing to release for screen off")
            return
        }
        runCatching {
            displayWakeLock?.let {
                if (it.isHeld) it.release()
            }
            displayWakeLock = null
            AppLogBuffer.info(TAG, "Screen off released via ${context.getString(R.string.screen_sleep_reason)}")
        }.onFailure { e ->
            AppLogBuffer.warn(TAG, "Failed to turn screen off: ${e.message}")
        }
        disarmWatchdog()
    }

    private fun armWatchdog() {
        if (!watchdogArmed.compareAndSet(false, true)) return
        mainHandler.postDelayed(watchdogTask, WATCHDOG_MS)
    }

    private fun disarmWatchdog() {
        mainHandler.removeCallbacks(watchdogTask)
        watchdogArmed.set(false)
    }
}
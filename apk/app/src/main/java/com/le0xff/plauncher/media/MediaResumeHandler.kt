package com.le0xff.plauncher.media

/**
 * pLauncher Companion App — Orchestrates the media playback resume strategy.
 *
 * @author Le0xFF
 */

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.le0xff.plauncher.data.AppDataStore
import com.le0xff.plauncher.data.AppLogBuffer
import com.le0xff.plauncher.util.ScreenWakeHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MediaResumeHandler(
    private val context: Context,
    private val dataStore: AppDataStore?
) {

    private companion object {
        const val TAG = "MediaResumer"
        const val MS_PER_SECOND = 1000L
        /** Minimum timeout that reliably covers a target app's media session appearing after a cold start. */
        const val MIN_EFFECTIVE_TIMEOUT_S = 20
    }

    fun resumeInBackground(packageName: String, scope: CoroutineScope, wokenByUs: Boolean = false) {
        AppLogBuffer.info(TAG, "resume flow will turn screen off at end: $wokenByUs")
        scope.launch {
            runCatching { resumePlayback(packageName, wokenByUs) }
                .onFailure { e -> AppLogBuffer.error(TAG, "resumePlayback failed for $packageName: ${e.message}") }
        }
    }

    private suspend fun resumePlayback(packageName: String, wokenByUs: Boolean) {
        AppLogBuffer.info(TAG, "Starting media resume for $packageName")
        val storedS = dataStore?.getPlayOnLaunchTimeoutS() ?: AppDataStore.DEFAULT_PLAY_ON_LAUNCH_TIMEOUT_S
        val timeoutS = maxOf(
            storedS.coerceIn(AppDataStore.MIN_PLAY_ON_LAUNCH_TIMEOUT_S, AppDataStore.MAX_PLAY_ON_LAUNCH_TIMEOUT_S),
            MIN_EFFECTIVE_TIMEOUT_S
        )
        if (timeoutS != storedS) {
            AppLogBuffer.warn(TAG, "Stored timeout ${storedS}s is below the effective minimum, using ${timeoutS}s")
        }
        val timeoutMs = timeoutS * MS_PER_SECOND
        // User-configurable first-phase window: how long to wait for the launched app's media
        // session before re-launching it. Falls back to a sane default if unset.
        val storedFirstPhaseS = dataStore?.getPlayOnLaunchFirstPhaseS()
            ?: AppDataStore.DEFAULT_PLAY_ON_LAUNCH_FIRST_PHASE_S
        val firstPhaseS = storedFirstPhaseS.coerceIn(
            AppDataStore.MIN_PLAY_ON_LAUNCH_FIRST_PHASE_S,
            AppDataStore.MAX_PLAY_ON_LAUNCH_FIRST_PHASE_S
        )
        val firstPhaseMs = firstPhaseS * MS_PER_SECOND
        // Only turn the screen off at the end of the flow if we are the ones who turned it on.
        val onFlowFinished: (() -> Unit)? = if (wokenByUs) {
            { runCatching { ScreenWakeHelper.turnScreenOff(context) }
                .onFailure { e -> AppLogBuffer.warn(TAG, "Failed to turn screen off after resume: ${e.message}") } }
        } else {
            null
        }
        val dispatched = MediaControlListenerService.requestResume(
            context,
            packageName,
            timeoutMs,
            firstPhaseMs,
            onFlowFinished
        )
        if (!dispatched) {
            // NLS not bound: send the legacy broadcast first (never opens a screen), release our
            // wake lock, and only then open the notification access settings informally so the
            // user knows the permission is missing. The activity start may be blocked by the
            // system for background services; in that case the flow simply ends with the log.
            AppLogBuffer.warn(TAG, "Notification access unavailable, sending legacy broadcast")
            LegacyBroadcastFallback.sendPlay(context, packageName)
            onFlowFinished?.invoke()
            val openedSettings = runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            }.getOrElse { e ->
                AppLogBuffer.warn(TAG, "Failed to open notification access settings: ${e.message}")
                false
            }
            if (openedSettings) {
                AppLogBuffer.info(TAG, "Notification access settings opened after fallback broadcast")
            }
        }
    }
}
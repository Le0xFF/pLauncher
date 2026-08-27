package com.le0xff.plauncher.media

/**
 * pLauncher Companion App — Orchestrates the media playback resume strategy.
 *
 * @author Le0xFF
 */

import android.content.Context
import com.le0xff.plauncher.data.AppDataStore
import com.le0xff.plauncher.data.AppLogBuffer
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

    fun resumeInBackground(packageName: String, scope: CoroutineScope) {
        scope.launch {
            runCatching { resumePlayback(packageName) }
                .onFailure { e -> AppLogBuffer.error(TAG, "resumePlayback failed for $packageName: ${e.message}") }
        }
    }

    private suspend fun resumePlayback(packageName: String) {
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
        val dispatched = MediaControlListenerService.requestResume(context, packageName, timeoutMs)
        if (!dispatched) {
            AppLogBuffer.warn(TAG, "Notification access unavailable, sending legacy broadcast only")
            LegacyBroadcastFallback.sendPlay(context, packageName)
        }
    }
}